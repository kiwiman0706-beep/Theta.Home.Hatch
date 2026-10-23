package jp.yaegaki.theta.home

import android.content.ComponentName
import androidx.test.core.app.ApplicationProvider
import jp.yaegaki.theta.home.data.AppEntry
import jp.yaegaki.theta.home.data.Backup
import jp.yaegaki.theta.home.data.Bookmark
import jp.yaegaki.theta.home.data.Category
import jp.yaegaki.theta.home.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupTest {
    private val app = ApplicationProvider.getApplicationContext<HomeApp>()
    private val store get() = app.store

    @Test
    fun exportThenImportRestoresSettingsAndListsMissingApps() {
        store.prefs.edit().clear().commit()
        store.themeMode = "light"
        store.railLeft = true
        store.saveCategories(listOf(Category("c1", "仕事"), Category("c2", "趣味")))
        store.saveAppCategories(mapOf("com.example.notes/.Main" to "c1"))
        store.categoryPresets = listOf("仕事", "趣味", "病院")
        store.saveBookmarks(listOf(Bookmark("b1", "GitHub", "https://github.com")))
        val apps = listOf(
            AppEntry(ComponentName("com.example.notes", "com.example.notes.Main"), "Notes"),
            AppEntry(ComponentName("jp.theta.travelhatch", "jp.theta.travelhatch.Main"), "TravelHatch"),
        )
        val text = Backup.export(app, store, apps)

        store.prefs.edit().clear().commit()
        val report = Backup.import(app, store, text)

        assertEquals("light", store.themeMode)
        assertTrue(store.railLeft)
        assertEquals(listOf("仕事", "趣味"), store.categories().map { it.name })
        assertEquals("c1", store.appCategories()["com.example.notes/.Main"])
        assertEquals(listOf("仕事", "趣味", "病院"), store.categoryPresets)
        assertEquals("GitHub", store.bookmarks().single().name)
        // どちらもテスト環境には入っていない。Hatch シリーズは先に並び、印が付く
        assertEquals(listOf("jp.theta.travelhatch", "com.example.notes"), report.missing.map { it.packageName })
        assertTrue(report.missing.first().hatch)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOtherFiles() {
        Backup.import(app, store, """{"hello":"world"}""")
    }

    @Test
    fun oldJapaneseTabNamesBecomeEnglish() {
        store.prefs.edit().clear().putString("tabs", """[{"id":"work","kind":"FOLDER","label":"仕事","folder":""},{"id":"x","kind":"FOLDER","label":"病院","folder":""}]""").commit()
        assertEquals(listOf("WORK", "病院"), Store(app).tabs().map { it.label })
    }
}
