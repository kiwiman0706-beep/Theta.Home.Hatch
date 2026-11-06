package app.thetahatch.home

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import app.thetahatch.home.data.Store
import app.thetahatch.home.widgets.WidgetController
import app.thetahatch.home.rescue.Rescue

class HomeApp : Application() {
    lateinit var store: Store
        private set
    lateinit var widgets: WidgetController
        private set

    /** ホームとエッジで同じものを使う（タブ・中身・開いているドロワー）。 */
    val model: HomeModel by lazy { HomeModel(this) }

    override fun onCreate() {
        super.onCreate()
        // 落ちたときの記録はいちばん最初に（レスキュー画面を出すかの判断に使う）
        Rescue.install(this)
        // レスキューのプロセス（":rescue"）では、本体の準備をしない（本体が壊れていても開けるように）
        if (getProcessName().endsWith(":rescue")) return
        store = Store(this)
        widgets = WidgetController(this)
        Rescue.watchMainThread(this)
    }

    fun toast(message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
}
