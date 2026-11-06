package app.thetahatch.home

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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import app.thetahatch.home.ui.HomeRoot
import app.thetahatch.home.ui.HomeTheme
import app.thetahatch.home.ui.isDark
import app.thetahatch.home.edge.EdgeService
import app.thetahatch.home.notify.MediaWatch
import app.thetahatch.home.rescue.Rescue
import app.thetahatch.home.rescue.RescueActivity

class MainActivity : ComponentActivity() {
    private val model: HomeModel get() = (application as HomeApp).model
    private val widgets get() = (application as HomeApp).widgets

    /** 回線が変わったら（Wi-Fi⇔モバイル、Tailscale のオン・オフ）NAS の状態を調べ直す。 */
    private val network = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) = runOnUiThread { model.refreshStatus(force = true); model.refreshSpeed() }
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
    private val markHealthy = Runnable { Rescue.onHomeHealthy(this) }

    /** ホームが前にある間、5 分ごとに回線の速さを確かめる（測るかどうかは設定の間隔で決める）。 */
    private val speedTick: Runnable = object : Runnable {
        override fun run() {
            model.refreshSpeed()
            healthy.postDelayed(this, 5 * 60_000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 落ちる・固まるのが続いていたら、ホームの代わりにレスキュー画面
        val safe = intent?.getBooleanExtra(Rescue.EXTRA_SAFE, false) == true
        if (Rescue.onHomeStart(this) && !safe) {
            rescued = true
            startActivity(Intent(this, RescueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
            return
        }
        if (safe) model.safeMode = true
        intent?.getStringExtra(EXTRA_HATCH)?.let { model.showHatch(it) }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packages, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(ConnectivityManager::class.java)?.registerDefaultNetworkCallback(network)
        setContent {
            val dark = app.thetahatch.home.ui.paletteFor(model.themeMode, isSystemInDarkTheme()).isDark
            // ステータスバー・ナビゲーションバーの文字色もテーマに合わせる
            val view = LocalView.current
            SideEffect {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            HomeTheme(model.themeMode) {
                HomeRoot(model, this)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // ホームボタンを押したら、開いているドロワーを閉じてホームに戻る
        if (intent.hasCategory(Intent.CATEGORY_HOME)) model.onHome()
        intent.getStringExtra(EXTRA_HATCH)?.let { model.showHatch(it) }
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
        model.refreshSpeed()
        // ホームが前にある間は 5 分ごとに回線の速さ（測るかどうかは設定の間隔で決める）
        healthy.removeCallbacks(speedTick)
        healthy.postDelayed(speedTick, 5 * 60_000L)
        // タスクバーの「最近」と、再生中の音楽
        model.refreshUsage()
        MediaWatch.start(this)
        model.reloadShortcuts()
        model.refreshHomeRole()
        model.checkUpdate()
        app.thetahatch.home.hub.UpdateHub.foreground = true
        // エッジ：ホームが見えている間は端の取っ手を隠す
        EdgeService.homeShown.value = true
        model.syncEdge()
    }

    override fun onPause() {
        app.thetahatch.home.hub.UpdateHub.foreground = false
        healthy.removeCallbacks(speedTick)
        EdgeService.homeShown.value = false
        super.onPause()
    }

    override fun onStop() {
        if (!rescued) {
            healthy.removeCallbacks(markHealthy)
            // 普通に裏へ回った（落ちた・固まったのではない）
            Rescue.onHomeHealthy(this)
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
        if (requestCode == REQ_WIDGET_CONFIGURE) model.onWidgetConfigured(resultCode == RESULT_OK)
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
        /** Hatch の一覧を開く（値はアプリの id。空なら一覧だけ）。更新の通知・ほかのアプリの「更新する」から。 */
        const val EXTRA_HATCH = "app.thetahatch.home.HATCH"
    }
}
