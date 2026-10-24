package jp.yaegaki.theta.home.edge

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import jp.yaegaki.theta.home.MainActivity
import jp.yaegaki.theta.home.R
import jp.yaegaki.theta.home.data.AppEntry
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.PinnedShortcut
import jp.yaegaki.theta.home.data.Shortcuts
import jp.yaegaki.theta.home.data.Store
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.notify.HatchNotificationListener
import jp.yaegaki.theta.home.ui.DarkPalette
import jp.yaegaki.theta.home.ui.HomeTheme
import jp.yaegaki.theta.home.ui.IconLook
import jp.yaegaki.theta.home.ui.LightPalette
import jp.yaegaki.theta.home.ui.LocalIconLook
import jp.yaegaki.theta.home.ui.PopPalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * エッジ：どのアプリの上でも、画面の端の細い取っ手を内側へなぞる（または押す）と、カテゴリーのタブとアプリが出る。
 * 「ほかのアプリの上に表示」の権限で窓を重ねる。消されないよう、常駐の通知を出すフォアグラウンドサービスにする。
 * ホームを開いている間は取っ手を隠す（ホームには右端のタブがあるので）。
 */
class EdgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private var handle: Handle? = null
    private var panel: View? = null
    private var owner: OverlayOwner? = null

    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var selected by mutableStateOf("")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        store = Store(this)
        selected = store.edgeTab
        scope.launch { homeShown.collect { refreshHandle() } }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            store.edgeOn = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!store.edgeOn || !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 権限があり、取っ手が見えているので、裏からでもフォアグラウンドにできる
        if (!runCatching { goForeground() }.isSuccess) {
            stopSelf()
            return START_NOT_STICKY
        }
        removeHandle()
        refreshHandle()
        return START_STICKY
    }

    private fun goForeground() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "エッジ（端からタブ）", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, EdgeService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("エッジ：画面の端からタブを出せます")
            .setContentText("この通知は設定の「通知」でしまえます")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "止める", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, note)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // 折りたたみ・回転で画面の高さが変わる
        removeHandle()
        refreshHandle()
        if (panel != null) {
            closePanel()
        }
    }

    override fun onDestroy() {
        closePanel()
        removeHandle()
        scope.cancel()
        super.onDestroy()
    }

    // ---- 取っ手 ----

    private fun refreshHandle() {
        if (!store.edgeOn || !Settings.canDrawOverlays(this)) return
        val h = handle ?: addHandle() ?: return
        h.visibility = if (homeShown.value || panel != null) View.GONE else View.VISIBLE
    }

    private fun addHandle(): Handle? {
        val d = resources.displayMetrics.density
        val left = store.edgeSide != "right"
        val heightPx = (HANDLE_DP * d).toInt()
        val screen = resources.displayMetrics.heightPixels
        val lp = WindowManager.LayoutParams(
            (18 * d).toInt(), heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or if (left) Gravity.START else Gravity.END
            y = ((screen - heightPx) * store.edgePos.coerceIn(0, 100) / 100f).toInt()
            title = "Theta Home edge"
        }
        val h = Handle(this, left, handleColor()) { openPanel() }
        return runCatching { wm.addView(h, lp); h }.getOrNull()?.also { handle = it }
    }

    private fun removeHandle() {
        handle?.let { runCatching { wm.removeView(it) } }
        handle = null
    }

    private fun palette() = when (store.themeMode) {
        "pop" -> PopPalette
        "light" -> LightPalette
        "dark" -> DarkPalette
        else -> if (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES) DarkPalette else LightPalette
    }

    private fun handleColor() = palette().accent.toArgb()

    // ---- 引き出したタブ ----

    private fun tabs(): List<EdgeTab> =
        store.tabs().filter { it.kind == TabKind.CATEGORY && !it.folderUri.isNullOrEmpty() }.map { EdgeTab(it.folderUri!!, it.label) } + EdgeTab("", "ALL")

    private fun appsOf(catId: String): List<AppEntry> {
        if (catId.isEmpty()) return apps
        val cats = store.appCategories()
        val order = store.appOrder()[catId].orEmpty().withIndex().associate { it.value to it.index }
        return apps.filter { cats[it.key] == catId }.sortedWith(compareBy({ order[it.key] ?: Int.MAX_VALUE }, { it.label.lowercase() }))
    }

    private fun openPanel() {
        if (panel != null) return
        scope.launch { apps = withContext(Dispatchers.IO) { Apps.load(this@EdgeService) } }
        val tabs = tabs()
        if (tabs.none { it.catId == selected }) selected = tabs.first().catId
        val left = store.edgeSide != "right"
        val shortcuts = store.shortcuts()
        val p = palette()
        val look = IconLook(store.iconMode, 0, store.iconColors)
        val o = OverlayOwner().also { owner = it }
        val compose = ComposeView(this).apply {
            setContent {
                val notices by HatchNotificationListener.notices.collectAsState()
                val counts = notices.groupingBy { it.packageName }.eachCount()
                HomeTheme(dark = p === DarkPalette, pop = p.pop) {
                    CompositionLocalProvider(LocalIconLook provides look) {
                        EdgePanel(
                            tabs = tabs,
                            selected = selected,
                            apps = appsOf(selected),
                            shortcuts = shortcuts.filter { selected.isNotEmpty() && it.catId == selected },
                            left = left,
                            counts = counts,
                            onSelect = { selected = it; store.edgeTab = it },
                            onApp = { launch(Apps.launchIntent(it)) },
                            onShortcut = { sc -> startShortcut(sc) },
                            onHome = { launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                            onClose = { closePanel() },
                        )
                    }
                }
            }
        }
        val root = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) closePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        root.setViewTreeLifecycleOwner(o)
        root.setViewTreeSavedStateRegistryOwner(o)
        root.addView(compose)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_INSET_DECOR,
            PixelFormat.TRANSLUCENT,
        ).apply { title = "Theta Home edge panel" }
        if (runCatching { wm.addView(root, lp) }.isSuccess) {
            panel = root
            o.resume()
        } else {
            o.destroy()
            owner = null
        }
        refreshHandle()
    }

    private fun closePanel() {
        panel?.let { runCatching { wm.removeView(it) } }
        panel = null
        owner?.destroy()
        owner = null
        refreshHandle()
    }

    private fun launch(intent: Intent) {
        runCatching { startActivity(intent) }
        closePanel()
    }

    private fun startShortcut(sc: PinnedShortcut) {
        if (!Shortcuts.start(this, sc)) android.widget.Toast.makeText(this, "開けませんでした（このアプリが既定のホームのときだけ開けます）", android.widget.Toast.LENGTH_SHORT).show()
        closePanel()
    }

    /** 端の細い取っ手。内側へなぞるか、押すと開く。 */
    @SuppressLint("ViewConstructor", "ClickableViewAccessibility")
    private class Handle(ctx: Context, private val left: Boolean, color: Int, private val open: () -> Unit) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; alpha = 170 }
        private val d = ctx.resources.displayMetrics.density
        private var downX = 0f
        private var downY = 0f
        private var opened = false
        private val bar = RectF()

        init {
            contentDescription = "タブを引き出す"
            setOnTouchListener { _, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; opened = false }
                    MotionEvent.ACTION_MOVE -> {
                        val inward = if (left) e.rawX - downX else downX - e.rawX
                        if (!opened && inward > 16 * d) { opened = true; open() }
                    }
                    MotionEvent.ACTION_UP -> if (!opened && abs(e.rawX - downX) < 8 * d && abs(e.rawY - downY) < 8 * d) open()
                }
                true
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            // 端からのなぞりを「戻る」の操作に取られないように
            systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
        }

        override fun onDraw(canvas: Canvas) {
            val w = 4 * d
            val x = if (left) 0f else width - w
            bar.set(x, height * 0.1f, x + w, height * 0.9f)
            canvas.drawRoundRect(bar, w, w, paint)
        }
    }

    /** サービスの中で Compose を動かすための持ち主（ライフサイクルと保存の仕組み）。 */
    private class OverlayOwner : SavedStateRegistryOwner {
        private val registry = LifecycleRegistry(this)
        private val saved = SavedStateRegistryController.create(this)
        override val lifecycle: Lifecycle get() = registry
        override val savedStateRegistry get() = saved.savedStateRegistry

        init { saved.performRestore(null) }

        fun resume() = registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        fun destroy() = registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }

    companion object {
        private const val CHANNEL = "edge"
        private const val NOTIFICATION_ID = 42
        private const val HANDLE_DP = 150
        const val ACTION_STOP = "jp.yaegaki.theta.home.edge.STOP"

        /** ホームを開いている間は取っ手を隠す。MainActivity が見えたとき・裏へ回ったときに変える。 */
        val homeShown = MutableStateFlow(false)

        /** 設定に合わせて、動かす・止める。 */
        fun sync(ctx: Context) {
            val store = Store(ctx)
            val intent = Intent(ctx, EdgeService::class.java)
            if (store.edgeOn && Settings.canDrawOverlays(ctx)) runCatching { ContextCompat.startForegroundService(ctx, intent) }
            else ctx.stopService(intent)
        }
    }
}
