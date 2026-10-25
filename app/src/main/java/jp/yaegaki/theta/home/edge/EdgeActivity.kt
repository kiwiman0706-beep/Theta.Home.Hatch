package jp.yaegaki.theta.home.edge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import jp.yaegaki.theta.home.HomeApp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.MainActivity
import jp.yaegaki.theta.home.ui.HomeRoot
import jp.yaegaki.theta.home.ui.HomeTheme

/**
 * エッジ：ほかのアプリの上に重ねる、透けた画面。端のタブとドロワーはホームと同じもの（同じ HomeModel）で、
 * ウィジェット・フォルダ・予定なども出る。背景のファイラーは出さず、外側を押す・戻るで閉じる。
 * 自分のタスク（最近の一覧には出さない）で開くので、閉じると下のアプリに戻る。
 */
class EdgeActivity : ComponentActivity() {
    private val model: HomeModel get() = (application as HomeApp).model
    private val widgets get() = (application as HomeApp).widgets

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val pop = model.themeMode == "pop"
            val dark = when (model.themeMode) {
                "light", "pop" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            HomeTheme(dark = dark, pop = pop) {
                HomeRoot(model, this, edgeExit = ::exit)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!model.safeMode) widgets.start()
    }

    override fun onResume() {
        super.onResume()
        EdgeService.homeShown.value = true
        model.reloadShortcuts()
        // 開いたら、最後に開いていたタブを出しておく（無ければタブだけ）
        if (model.open == null && !model.settingsOpen) model.openLast()
    }

    override fun onPause() {
        EdgeService.homeShown.value = false
        super.onPause()
    }

    override fun onStop() {
        widgets.stop()
        super.onStop()
    }

    /** 閉じる。開いていたドロワーも閉じておく（ホームを開いたときに開いたままにならないように）。 */
    private fun exit() {
        model.onHome()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    @Deprecated("AppWidgetHost の設定画面は requestCode で結果を返す")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MainActivity.REQ_WIDGET_CONFIGURE) model.onWidgetConfigured(resultCode == RESULT_OK)
    }
}
