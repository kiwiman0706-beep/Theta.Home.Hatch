package jp.yaegaki.theta.home.edge

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import jp.yaegaki.theta.home.data.Store

/**
 * ほかのアプリ（Dimple・モードとルーチン・ショートカット）からエッジを開くときの中継。何も描かず、すぐ消える。
 * ショートカットやアプリの一覧から直接エッジを開くと、Android が後ろにホームを出してしまう（下のアプリに戻れない）ことがあるので、
 * エッジの取っ手が動いていれば、取っ手を押したときと同じく常駐の側から開く。動いていなければ、ここから開く。
 */
class EdgeLauncher : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val alias = intent?.component?.className.orEmpty()
        val action = when {
            alias.endsWith(".QuickInput") -> EdgeActivity.QUICK
            alias.endsWith(".ShowTabs") -> SHOW_TABS
            else -> intent?.action ?: SHOW_TABS
        }
        val forward = Intent(this, EdgeActivity::class.java).setAction(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        intent?.extras?.let { forward.putExtras(it) }
        val store = Store(this)
        val viaService = store.edgeOn && Settings.canDrawOverlays(this) && runCatching {
            startService(Intent(this, EdgeService::class.java).setAction(EdgeService.ACTION_OPEN).putExtra(EdgeService.EXTRA_FORWARD, forward))
        }.isSuccess
        if (!viaService) runCatching { startActivity(forward) }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        const val SHOW_TABS = "jp.yaegaki.theta.home.SHOW_TABS"
    }
}
