package jp.yaegaki.theta.home

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import jp.yaegaki.theta.home.data.Store
import jp.yaegaki.theta.home.widgets.WidgetController

class HomeApp : Application() {
    lateinit var store: Store
        private set
    lateinit var widgets: WidgetController
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        widgets = WidgetController(this)
    }

    fun toast(message: String) {
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, message, Toast.LENGTH_SHORT).show() }
    }
}
