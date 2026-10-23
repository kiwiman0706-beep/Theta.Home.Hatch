package jp.yaegaki.theta.home

import android.app.Application
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
}
