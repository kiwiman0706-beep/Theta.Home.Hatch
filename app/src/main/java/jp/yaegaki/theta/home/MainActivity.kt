package jp.yaegaki.theta.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import jp.yaegaki.theta.home.ui.HomeRoot
import jp.yaegaki.theta.home.ui.HomeTheme

class MainActivity : ComponentActivity() {
    private val model: HomeModel by viewModels()
    private val widgets get() = (application as HomeApp).widgets

    /** 回線が変わったら（Wi-Fi⇔モバイル、Tailscale のオン・オフ）NAS の状態を調べ直す。 */
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) = runOnUiThread { model.refreshStatus(force = true) }
        override fun onLost(n: Network) = runOnUiThread { model.refreshStatus(force = true) }
    }

    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            model.refreshApps()
            model.refreshHatchInstalled()
        }
    }

    /** レスキュー画面へ回したとき（このアクティビティは何もしないで閉じる）。 */
    private var rescued = false
    private val healthy = android.os.Handler(android.os.Looper.getMainLooper())
    private val markHealthy = Runnable { jp.yaegaki.theta.home.rescue.Rescue.onHomeHealthy(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 落ちる・固まるのが続いていたら、ホームの代わりにレスキュー画面
        val safe = intent?.getBooleanExtra(jp.yaegaki.theta.home.rescue.Rescue.EXTRA_SAFE, false) == true
        if (jp.yaegaki.theta.home.rescue.Rescue.onHomeStart(this) && !safe) {
            rescued = true
            startActivity(Intent(this, jp.yaegaki.theta.home.rescue.RescueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        if (safe) model.safeMode = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packages, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(network)
        setContent {
            val pop = model.themeMode == "pop"
            val dark = when (model.themeMode) {
                "light", "pop" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            // ステータスバー・ナビゲーションバーの文字色もテーマに合わせる
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            HomeTheme(dark = dark, pop = pop) {
                HomeRoot(model, this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // ホームボタンを押したら、開いているドロワーを閉じてホームに戻る
        if (intent.hasCategory(Intent.CATEGORY_HOME)) model.onHome()
    }

    override fun onStart() {
        super.onStart()
        if (rescued) return
        if (!model.safeMode) widgets.start()
    }

    override fun onResume() {
        super.onResume()
        if (rescued) return
        // 8 秒動けば「無事に起動した」
        healthy.removeCallbacks(markHealthy)
        healthy.postDelayed(markHealthy, 8000)
        model.refreshEvents()
        model.refreshHatchInstalled()
        model.reloadClips()
        model.refreshCaptures()
        model.refreshStatus()
        model.refreshWeather()
        model.reloadShortcuts()
        model.refreshHomeRole()
        model.checkUpdate()
    }

    override fun onStop() {
        if (!rescued) {
            healthy.removeCallbacks(markHealthy)
            // 普通に裏へ回った（落ちた・固まったのではない）
            jp.yaegaki.theta.home.rescue.Rescue.onHomeHealthy(this)
            widgets.stop()
        }
        super.onStop()
    }

    override fun onDestroy() {
        if (rescued) {
            super.onDestroy()
            return
        }
        unregisterReceiver(packages)
        runCatching { getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(network) }
        super.onDestroy()
    }

    @Deprecated("AppWidgetHost の設定画面は requestCode で結果を返す")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_WIDGET_CONFIGURE) {
            val id = (application as HomeApp).store.pendingWidget
            if (id == -1) return
            if (resultCode == RESULT_OK) model.addWidget(id)
            else {
                widgets.release(id)
                (application as HomeApp).store.pendingWidgetTab = -1
                (application as HomeApp).toast("ウィジェットの設定をやめたので、置きませんでした")
            }
            (application as HomeApp).store.pendingWidget = -1
        }
    }

    /**
     * 物理キーボード（Titan など）：Alt＋キーでドロワー、文字を打つとすぐ検索、Esc で閉じる。
     * 入力欄が文字を受け取ったときはここまで来ない。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE && (model.open != null || model.settingsOpen)) {
            model.onHome()
            return true
        }
        if (event.isAltPressed && !event.isCtrlPressed) {
            val c = event.getUnicodeChar(0).toChar()
            if (c.isLetterOrDigit() && model.openByKey(c)) return true
        }
        if (!event.isAltPressed && !event.isCtrlPressed && !event.isMetaPressed && model.open == null && !model.settingsOpen) {
            val code = event.unicodeChar
            if (code != 0 && !Character.isISOControl(code) && code != ' '.code) {
                model.typeToSearch(code.toChar())
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val REQ_WIDGET_CONFIGURE = 51
    }
}
