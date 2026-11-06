package app.thetahatch.home.hub

import android.app.PendingIntent
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import app.thetahatch.home.HomeApp
import app.thetahatch.home.MainActivity
import app.thetahatch.home.nas.Secret

/**
 * 設定のハブ（THETA_SHARED_SETTINGS §4.0）。content://app.thetahatch.home.theta/…
 * 権限 app.thetahatch.permission.SHARE（signature）で守るので、シリーズ共通の鍵で署名したアプリだけが読める。
 *
 * - query /info → 列 key・value・by・at（1 項目 1 行。at は ISO 8601）
 * - query /secret/<キー> → 列 key・value（gemini.key・nas.password・nas.deviceToken）。読まれたら通知で知らせる
 * - update /info（ContentValues：キー → 値）→ 書き戻し。書いたアプリと時刻を記録する。秘密は受け付けない
 */
class SettingsProvider : ContentProvider() {
    private val prefs get() = (context!!.applicationContext as HomeApp).store.prefs

    override fun onCreate() = true

    private fun caller(): String {
        val pkg = callingPackage ?: return "?"
        Catalog.load(context!!)
        return Catalog.byPackage(pkg)?.id ?: pkg
    }

    private fun callerName(): String {
        val pkg = callingPackage ?: return "シリーズのアプリ"
        return Catalog.byPackage(pkg)?.name ?: pkg
    }

    /** ホームが持っている値。 */
    internal fun value(key: String): String? {
        val p = prefs
        return when (key) {
            "ui.skin" -> (context!!.applicationContext as HomeApp).store.themeMode
            "gemini.model" -> (context!!.applicationContext as HomeApp).store.geminiModel
            "nas.url" -> p.getString("nasUrl", "")
            "nas.user" -> p.getString("nasAccount", "")
            "google.account" -> p.getString("driveEmail", "")
            "gemini.key" -> (context!!.applicationContext as HomeApp).store.geminiKey
            "nas.password" -> Secret.open(p.getString("nasPassword", ""))
            "nas.deviceToken" -> p.getString("nasDid", "")
            else -> SeriesMeta.extra(p, key)
        }?.takeIf { it.isNotEmpty() }
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val seg = uri.pathSegments
        return when {
            seg.firstOrNull() == "info" -> {
                val c = MatrixCursor(arrayOf("key", "value", "by", "at"))
                (SeriesMeta.INFO + SeriesMeta.extraKeys(prefs)).distinct().filterNot { SeriesMeta.looksSecret(it) }.forEach { k ->
                    value(k)?.let { c.addRow(arrayOf(k, it, SeriesMeta.by(prefs, k), SeriesMeta.at(prefs, k))) }
                }
                c
            }
            seg.size == 2 && seg[0] == "secret" && seg[1] in SeriesMeta.SECRETS -> {
                val c = MatrixCursor(arrayOf("key", "value"))
                val v = value(seg[1])
                if (v != null) {
                    c.addRow(arrayOf(seg[1], v))
                    SeriesMeta.addLent(prefs, caller(), seg[1])
                    tellLent(seg[1])
                }
                c
            }
            else -> null
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        if (uri.pathSegments.firstOrNull() != "info" || values == null) return 0
        val by = caller()
        val app = context!!.applicationContext as HomeApp
        var n = 0
        for (k in values.keySet()) {
            if (SeriesMeta.looksSecret(k)) continue
            val v = values.getAsString(k) ?: continue
            when (k) {
                "ui.skin" -> Handler(Looper.getMainLooper()).post { app.model.changeTheme(v) }
                "gemini.model" -> app.store.geminiModel = v
                "google.account" -> prefs.edit().putString("driveEmail", v).apply()
                "nas.url", "nas.user" -> {
                    val pk = if (k == "nas.url") "nasUrl" else "nasAccount"
                    if (prefs.getString(pk, "") != v) prefs.edit().putString(pk, v).remove("nasDid").remove("nasCertPin").apply()
                }
                else -> SeriesMeta.putExtra(prefs, k, v)
            }
            SeriesMeta.stamp(prefs, k, by)
            n++
        }
        return n
    }

    private fun tellLent(key: String) {
        val ctx = context!!
        val what = when (key) {
            "gemini.key" -> "Gemini の API キー"
            "nas.password" -> "NAS のパスワード"
            else -> "NAS のデバイストークン"
        }
        UpdateHub.notify(
            ctx, 100 + SeriesMeta.SECRETS.indexOf(key), "共通設定を貸しました", "${callerName()} が $what を読みました",
            PendingIntent.getActivity(ctx, 3, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE),
        )
    }

    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
}
