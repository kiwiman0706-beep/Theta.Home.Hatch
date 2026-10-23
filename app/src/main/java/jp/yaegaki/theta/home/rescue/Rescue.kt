package jp.yaegaki.theta.home.rescue

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings

/**
 * ベータ版のホームが落ち続ける・固まったときの逃げ道。
 * - 落ちたら（例外）時刻を残す。起動して 8 秒もたずに終わった（落ちた・固まって止められた）回数も数える
 * - 主スレッドが 8 秒以上応えなければ「固まった」と記録する
 * - こうしたことが続いたら、次の起動ではホームの代わりにレスキュー画面（[RescueActivity]）を出す
 * 記録は設定とは別のファイルに、その場で書く（commit）。
 */
object Rescue {
    private const val PREFS = "rescue"
    private const val WINDOW = 5 * 60_000L
    const val LIMIT = 3
    const val EXTRA_SAFE = "safe_mode"
    const val RELEASES = "https://github.com/kiwiman0706-beep/Theta.Home.Hatch/releases"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 落ちたときの記録。いちばん最初に入れる。 */
    fun install(c: Context) {
        val app = c.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { record(app, "crash", e.javaClass.simpleName + ": " + (e.message ?: "")) }
            previous?.uncaughtException(t, e)
        }
    }

    private fun record(c: Context, kind: String, detail: String) {
        val p = prefs(c)
        val now = System.currentTimeMillis()
        val times = (p.getString("times", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.filter { now - it < WINDOW } + now
        p.edit().putString("times", times.takeLast(10).joinToString(",")).putString("last", "$kind · $detail").putLong("lastAt", now).commit()
    }

    /** 最近（5 分以内）の不具合の回数。 */
    fun recentTrouble(c: Context): Int {
        val now = System.currentTimeMillis()
        return (prefs(c).getString("times", "") ?: "").split(',').mapNotNull { it.toLongOrNull() }.count { now - it < WINDOW }
    }

    fun lastTrouble(c: Context): String? = prefs(c).getString("last", null)

    /**
     * ホームの起動のはじめに呼ぶ。前の起動が「無事」の印を付けずに終わっていたら、不具合として数える。
     * true ならレスキュー画面を出す。
     */
    fun onHomeStart(c: Context): Boolean {
        val p = prefs(c)
        if (p.getBoolean("starting", false)) record(c, "start", "起動して間もなく止まりました")
        p.edit().putBoolean("starting", true).commit()
        return recentTrouble(c) >= LIMIT
    }

    /** 8 秒動いた・普通に裏へ回った：無事。 */
    fun onHomeHealthy(c: Context) {
        prefs(c).edit().putBoolean("starting", false).commit()
    }

    /** レスキュー画面から「開き直す」ときに、数え直す。 */
    fun reset(c: Context) {
        prefs(c).edit().clear().commit()
    }

    private var watching = false

    /** 主スレッドが応えなくなったら記録する（固まった）。 */
    fun watchMainThread(c: Context) {
        if (watching) return
        watching = true
        val app = c.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread({
            var reported = false
            while (true) {
                val answered = java.util.concurrent.atomic.AtomicBoolean(false)
                main.post { answered.set(true) }
                Thread.sleep(8000)
                if (!answered.get() && !reported) {
                    runCatching { record(app, "freeze", "画面が 8 秒以上応えませんでした") }
                    reported = true
                } else if (answered.get()) {
                    reported = false
                }
            }
        }, "home-watchdog").apply { isDaemon = true }.start()
    }

    /** 別のホームアプリを選ぶ画面（Android の設定）。開けなければ設定のトップ。 */
    fun chooseHome(c: Context) {
        val flags = Intent.FLAG_ACTIVITY_NEW_TASK
        runCatching { c.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(flags)) }
            .recoverCatching { c.startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS).addFlags(flags)) }
            .recoverCatching { c.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(flags)) }
    }
}
