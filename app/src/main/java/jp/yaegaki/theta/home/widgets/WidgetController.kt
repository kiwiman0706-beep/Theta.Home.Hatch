package jp.yaegaki.theta.home.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent

/**
 * 他のアプリのウィジェットをドロワーに置くためのホスト。
 * 置くときは「割り当ての許可」と、必要ならそのウィジェット自身の設定画面を通る。
 */
class WidgetController(context: Context) {
    private val app = context.applicationContext
    val manager: AppWidgetManager = AppWidgetManager.getInstance(app)
    val host = AppWidgetHost(app, HOST_ID)
    private val views = HashMap<Int, AppWidgetHostView>()

    fun providers(): List<AppWidgetProviderInfo> =
        manager.installedProviders.sortedBy { it.loadLabel(app.packageManager).toString() }

    fun label(info: AppWidgetProviderInfo): String = info.loadLabel(app.packageManager).toString()

    fun info(id: Int): AppWidgetProviderInfo? = manager.getAppWidgetInfo(id)

    fun allocate(): Int = host.allocateAppWidgetId()

    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean = manager.bindAppWidgetIdIfAllowed(id, info.provider)

    fun bindIntent(id: Int, info: AppWidgetProviderInfo): Intent =
        Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)

    fun needsConfigure(id: Int): Boolean = info(id)?.configure != null

    fun startConfigure(activity: Activity, id: Int, requestCode: Int) {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
    }

    fun release(id: Int) {
        views.remove(id)
        host.deleteAppWidgetId(id)
    }

    /** 同じウィジェットを描き直すたびに作り直さないよう、ビューを使い回す。 */
    fun view(activity: Activity, id: Int): AppWidgetHostView? {
        views[id]?.let { v ->
            (v.parent as? android.view.ViewGroup)?.removeView(v)
            return v
        }
        val info = info(id) ?: return null
        return host.createView(activity, id, info).also { views[id] = it }
    }

    /** 見せている画面（ホーム・エッジ）の数。0 になったら更新を受けるのをやめる。 */
    private var users = 0

    fun start() {
        users++
        if (users == 1) runCatching { host.startListening() }
    }

    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) runCatching { host.stopListening() }
    }

    companion object {
        const val HOST_ID = 6105
    }
}
