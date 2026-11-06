package app.thetahatch.home

import android.content.ContentValues
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import app.thetahatch.home.hub.ApkFacts
import app.thetahatch.home.hub.Catalog
import app.thetahatch.home.hub.CatalogApp
import app.thetahatch.home.hub.InstallKind
import app.thetahatch.home.hub.Migration
import app.thetahatch.home.hub.MigrationStep
import app.thetahatch.home.hub.SeriesMeta
import app.thetahatch.home.hub.SettingsProvider
import app.thetahatch.home.hub.UpdateHub
import app.thetahatch.home.hub.Verdict
import app.thetahatch.home.hub.Verify
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 更新のハブ・移行・設定のハブ（spec §6.1、THETA_SHARED_SETTINGS §4）。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HubTest {
    private val ctx = ApplicationProvider.getApplicationContext<HomeApp>()
    private val series = Verify.SERIES_KEY
    private val oldKey = "00".repeat(32)

    private fun app(bridge: Boolean = false, cur: String = "jp.old.memo", tgt: String = "app.thetahatch.memo") =
        CatalogApp("memo", "Theta Memo Hatch", null, "Theta.Memo.Hatch", cur, tgt, android = true, bridge = bridge, updateHub = false, status = null)

    @Test
    fun bundledCatalogReads() {
        val text = ctx.assets.open("apps.json").bufferedReader().readText()
        val list = Catalog.parse(text)
        val home = list.first { it.id == "home" }
        assertEquals("app.thetahatch.home", home.targetPackage)
        assertEquals("jp.yaegaki.theta.home", home.currentPackage)
        assertTrue(home.android && home.updateHub && !home.bridge)
        assertTrue(list.first { it.id == "meishi" }.bridge)
        // Android 版のない Hitsudan は更新の対象にしない
        assertFalse(list.first { it.id == "hitsudan" }.android)
    }

    @Test
    fun newerCatalogWins() {
        val a = """{"updated":"2026-11-01","apps":[]}"""
        val b = """{"updated":"2026-11-06","apps":[]}"""
        assertEquals(b, Catalog.newer(a, b))
        assertEquals(b, Catalog.newer(b, a))
        assertEquals(a, Catalog.newer(a, null))
    }

    @Test
    fun installedAndMigration() {
        val a = app()
        val onlyOld: (String) -> String? = { if (it == "jp.old.memo") "0.9" else null }
        val both: (String) -> String? = { if (it == "jp.old.memo" || it == "app.thetahatch.memo") "1.0" else null }
        assertEquals("jp.old.memo", a.installedPackage(onlyOld))
        assertTrue(a.needsMigration(onlyOld))
        assertEquals("app.thetahatch.memo", a.installedPackage(both))
        assertFalse(a.needsMigration { if (it == "app.thetahatch.memo") "1.0" else null })
    }

    @Test
    fun judgeUpdatesOnlyWithSeriesKey() {
        val a = app(cur = "app.thetahatch.memo")
        val installed = ApkFacts("app.thetahatch.memo", "1.0.0", 100, setOf(series))
        val lookup: (String) -> ApkFacts? = { if (it == "app.thetahatch.memo") installed else null }
        assertEquals(Verdict.Ok(InstallKind.UPDATE), Verify.judge(a, ApkFacts("app.thetahatch.memo", "1.1.0", 110, setOf(series)), lookup))
        // 古い版・ほかの鍵・ほかのパッケージは入れない
        assertTrue(Verify.judge(a, ApkFacts("app.thetahatch.memo", "0.9.0", 90, setOf(series)), lookup) is Verdict.Bad)
        assertTrue(Verify.judge(a, ApkFacts("app.thetahatch.memo", "1.1.0", 110, setOf(oldKey)), lookup) is Verdict.Bad)
        assertTrue(Verify.judge(a, ApkFacts("com.evil", "9", 999, setOf(series)), lookup) is Verdict.Bad)
        // 入っていないアプリは共通の鍵なら入れてよい
        assertEquals(Verdict.Ok(InstallKind.NEW), Verify.judge(a, ApkFacts("app.thetahatch.memo", "1.0.0", 100, setOf(series))) { null })
        assertTrue(Verify.judge(a, ApkFacts("app.thetahatch.memo", "1.0.0", 100, setOf(oldKey))) { null } is Verdict.Bad)
    }

    @Test
    fun judgeBridgeOnlyForBridgeApps() {
        val installedOld = ApkFacts("jp.old.memo", "0.9", 9, setOf(oldKey))
        val bridgeApk = ApkFacts("jp.old.memo", "0.9.1", 10, setOf(oldKey))
        val lookup: (String) -> ApkFacts? = { if (it == "jp.old.memo") installedOld else null }
        assertEquals(Verdict.Ok(InstallKind.BRIDGE), Verify.judge(app(bridge = true), bridgeApk, lookup))
        assertTrue(Verify.judge(app(bridge = false), bridgeApk, lookup) is Verdict.Bad)
        // 橋渡し版でも、入っているアプリと署名が違えば入れない
        assertTrue(Verify.judge(app(bridge = true), bridgeApk.copy(signers = setOf("11".repeat(32))), lookup) is Verdict.Bad)
    }

    @Test
    fun ownerFollowsDefaultHome() {
        assertTrue(UpdateHub.ownerFor("app.thetahatch.home"))
        assertTrue(UpdateHub.ownerFor(null))
        assertTrue(UpdateHub.ownerFor("com.sec.android.app.launcher"))
        assertFalse(UpdateHub.ownerFor("app.thetahatch.kidshome"))
        assertFalse(UpdateHub.ownerFor("com.theta.kidshome"))
    }

    @Test
    fun releaseUsesGithubDigest() {
        val r = UpdateHub.parseRelease(
            JSONObject(
                """{"tag_name":"v1.0.0","body":"変更点","assets":[
                {"name":"ThetaHomeHatch-1.0.0.apk","browser_download_url":"https://x/a.apk","digest":"sha256:abc"},
                {"name":"SHA256SUMS-1.0.0.txt","browser_download_url":"https://x/s.txt"}]}""",
            ),
        )!!
        assertEquals("1.0.0", r.version)
        assertEquals("sha256:abc", r.digest)
        assertEquals("変更点", r.body)
        assertNull(UpdateHub.parseRelease(JSONObject("""{"tag_name":"v1","assets":[]}""")))
    }

    @Test
    fun migrationSteps() {
        val b = app(bridge = true)
        assertEquals(MigrationStep.BRIDGE, Migration.step(b, oldInstalled = true, newInstalled = false, hasExport = false, exported = false, imported = false))
        assertEquals(MigrationStep.EXPORT, Migration.step(b, true, false, hasExport = true, exported = false, imported = false))
        assertEquals(MigrationStep.INSTALL_NEW, Migration.step(b, true, false, true, exported = true, imported = false))
        assertEquals(MigrationStep.IMPORT, Migration.step(b, true, true, true, exported = true, imported = false))
        assertEquals(MigrationStep.DELETE_OLD, Migration.step(b, true, true, true, exported = true, imported = true))
        assertEquals(MigrationStep.DONE, Migration.step(b, false, true, false, true, true))
        // 橋渡し版のないアプリ：新しい版を入れて、古いものを消すだけ
        val n = app(bridge = false)
        assertEquals(MigrationStep.INSTALL_NEW, Migration.step(n, true, false, false, false, false))
        assertEquals(MigrationStep.DELETE_OLD, Migration.step(n, true, true, false, false, false))
    }

    @Test
    fun settingsProviderInfoSecretAndWriteBack() {
        val store = ctx.store
        store.geminiModel = "gemini-test"
        // 試験の環境には Android Keystore が無いので、平文で持つデバイストークンで確かめる
        store.prefs.edit().putString("nasUrl", "https://nas.example:5001").putString("nasAccount", "taro").putString("nasDid", "did-123").commit()
        val p = Robolectric.buildContentProvider(SettingsProvider::class.java).create("app.thetahatch.home.theta").get()

        val info = mutableMapOf<String, String>()
        p.query(Uri.parse("content://app.thetahatch.home.theta/info"), null, null, null, null)!!.use { c ->
            while (c.moveToNext()) info[c.getString(0)] = c.getString(1)
        }
        assertEquals("gemini-test", info["gemini.model"])
        assertEquals("taro", info["nas.user"])
        assertNull("秘密は info に出さない", info["nas.deviceToken"])

        p.query(Uri.parse("content://app.thetahatch.home.theta/secret/nas.deviceToken"), null, null, null, null)!!.use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("did-123", c.getString(1))
        }
        assertEquals("nas.deviceToken", SeriesMeta.lent(store.prefs).first().key)
        assertNull(p.query(Uri.parse("content://app.thetahatch.home.theta/secret/other"), null, null, null, null))

        // 書き戻し：設定は受け付け、秘密らしいものは受け付けない
        val n = p.update(
            Uri.parse("content://app.thetahatch.home.theta/info"),
            ContentValues().apply { put("gemini.model", "gemini-new"); put("nas.shareRoot", "/media"); put("gemini.key", "x") },
            null, null,
        )
        assertEquals(2, n)
        assertEquals("gemini-new", store.geminiModel)
        assertEquals("", store.prefs.getString("geminiKey", ""))
        assertEquals("/media", SeriesMeta.extra(store.prefs, "nas.shareRoot"))
        assertNotNull(SeriesMeta.at(store.prefs, "gemini.model").takeIf { it.isNotEmpty() })
    }
}
