package jp.yaegaki.theta.home.edge

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
        handle(intent)
        setContent {
            HomeTheme(model.themeMode) {
                HomeRoot(model, this, edgeExit = ::exit)
            }
            // 入力だけのために開いたなら、送った・閉じたらそのまま下のアプリ（送り先）へ戻る
            androidx.compose.runtime.LaunchedEffect(Unit) {
                androidx.compose.runtime.snapshotFlow { model.quickOpen }.collect { if (it == null && purpose == QUICK) exit() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    /** 何のために開いたか：タブ（null）・入力（QUICK）・声でアプリ（VOICE_APP）。 */
    private var purpose: String? = null

    private fun handle(intent: Intent?) {
        val alias = intent?.component?.className.orEmpty()
        purpose = when {
            intent?.action == QUICK || alias.endsWith(".QuickInput") -> QUICK
            intent?.action == VOICE_APP -> VOICE_APP
            else -> null
        }
        when (purpose) {
            QUICK -> {
                model.onHome()
                model.quickOpen = intent?.getStringExtra(EXTRA_MODE)?.takeIf { it in MODES } ?: "voice"
            }
            VOICE_APP -> {
                model.onHome()
                model.openApps(voice = true)
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
        if (purpose == null && model.open == null && !model.settingsOpen && model.quickOpen == null) model.openLast()
    }

    override fun onPause() {
        EdgeService.homeShown.value = false
        super.onPause()
    }

    override fun onStop() {
        widgets.stop()
        // 声でアプリを開いたら、開いたアプリへ移るので閉じておく（入力はカメラへ移ることがあるので閉じない）
        if (purpose == VOICE_APP && !isChangingConfigurations) {
            purpose = null
            model.onHome()
            finish()
        }
        super.onStop()
    }

    /** 閉じる。開いていたドロワーも閉じておく（ホームを開いたときに開いたままにならないように）。 */
    private fun exit() {
        model.onHome()
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        /** 入力（声・文字・写真 → 送る）を開く。extra "mode" は voice・text・photo（無ければ voice）。 */
        const val QUICK = "jp.yaegaki.theta.home.QUICK"
        /** すべてのアプリを開いて、声でアプリを開く（呼び名でも）。 */
        const val VOICE_APP = "jp.yaegaki.theta.home.VOICE_APP"
        const val EXTRA_MODE = "mode"
        private val MODES = setOf("voice", "text", "photo")
    }

    @Deprecated("AppWidgetHost の設定画面は requestCode で結果を返す")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MainActivity.REQ_WIDGET_CONFIGURE) model.onWidgetConfigured(resultCode == RESULT_OK)
    }
}
