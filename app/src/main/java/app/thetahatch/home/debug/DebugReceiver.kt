package app.thetahatch.home.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import app.thetahatch.home.BuildConfigInfo
import app.thetahatch.home.HomeApp
import org.json.JSONObject
import java.io.File

/**
 * PC から adb で設定を読む・変える（試験・β テスト用。docs/DEV_ADB.md）。
 * 権限 android.permission.DUMP で守るので、送れるのは adb の shell（と端末の仕組み）だけ。ほかのアプリからは送れない。
 *
 *   adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd dump
 *   adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd get --es key theme
 *   adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd set --es key theme --es value pop
 *   adb shell am broadcast -n app.thetahatch.home/.debug.DebugReceiver --es cmd restart
 *
 * 結果は「Broadcast completed: result=…, data=…」の data と、logcat の ThetaHomeDebug に出る。
 * 秘密（API キー・パスワード・トークン）は読めず、書けない。
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? HomeApp ?: return
        val prefs = app.store.prefs
        val out = runCatching {
            when (intent.getStringExtra("cmd")) {
                "info" -> JSONObject()
                    .put("version", BuildConfigInfo.versionName(context))
                    .put("package", context.packageName)
                    .put("profile", app.model.profile)
                    .put("theme", app.store.themeMode)
                    .toString()
                "dump" -> {
                    val o = JSONObject()
                    prefs.all.toSortedMap().forEach { (k, v) -> if (!secret(k)) o.put(k, v) }
                    val text = o.toString(1)
                    // 長いと am の表示で切れるので、ファイルにも置く（adb pull で取れる）
                    context.getExternalFilesDir("debug")?.let { File(it, "prefs.json").writeText(text) }
                    text
                }
                "get" -> {
                    val k = intent.getStringExtra("key") ?: error("key がありません")
                    require(!secret(k)) { "秘密の項目は読めません" }
                    prefs.all[k]?.toString() ?: "(なし)"
                }
                "set" -> {
                    val k = intent.getStringExtra("key") ?: error("key がありません")
                    require(!secret(k)) { "秘密の項目は書けません" }
                    val v = intent.getStringExtra("value") ?: error("value がありません")
                    val e = prefs.edit()
                    when (intent.getStringExtra("type") ?: guess(prefs.all[k])) {
                        "bool" -> e.putBoolean(k, v.toBooleanStrict())
                        "int" -> e.putInt(k, v.toInt())
                        "long" -> e.putLong(k, v.toLong())
                        "float" -> e.putFloat(k, v.toFloat())
                        else -> e.putString(k, v)
                    }
                    e.commit()
                    "OK $k=$v（反映は cmd restart のあと）"
                }
                "remove" -> {
                    val k = intent.getStringExtra("key") ?: error("key がありません")
                    require(!secret(k)) { "秘密の項目は消せません" }
                    prefs.edit().remove(k).commit()
                    "OK $k を消しました"
                }
                "restart" -> {
                    // ホームアプリなので、終わると端末がすぐ開き直す
                    Handler(Looper.getMainLooper()).postDelayed({ android.os.Process.killProcess(android.os.Process.myPid()) }, 300)
                    "OK 開き直します"
                }
                else -> "cmd は info・dump・get・set・remove・restart のどれか"
            }
        }.getOrElse { "ERROR ${it.message}" }
        Log.i(TAG, out)
        resultData = out
    }

    private fun guess(v: Any?) = when (v) {
        is Boolean -> "bool"
        is Int -> "int"
        is Long -> "long"
        is Float -> "float"
        else -> "string"
    }

    companion object {
        const val TAG = "ThetaHomeDebug"

        /** 秘密の項目（読めない・書けない）。 */
        private val SECRETS = setOf("geminikey", "naspassword", "nasdid", "nascertpin")

        fun secret(key: String): Boolean {
            val k = key.lowercase()
            return k in SECRETS || listOf("password", "passwd", "token", "secret", "apikey").any { k.contains(it) }
        }
    }
}
