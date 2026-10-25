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
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import jp.yaegaki.theta.home.MainActivity
import jp.yaegaki.theta.home.R
import jp.yaegaki.theta.home.data.Store
import jp.yaegaki.theta.home.ui.paletteFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * エッジ：どのアプリの上でも、画面の端の細い取っ手を内側へなぞる（または押す）と、ホームと同じタブと中身（EdgeActivity）が出る。
 * 取っ手は「ほかのアプリの上に表示」の権限で重ねる窓。消されないよう、常駐の通知を出すフォアグラウンドサービスにする。
 * ホーム・エッジを開いている間は取っ手を隠す。
 */
class EdgeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var wm: WindowManager
    private lateinit var store: Store
    private var handle: Handle? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WindowManager::class.java)
        store = Store(this)
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
    }

    override fun onDestroy() {
        removeHandle()
        scope.cancel()
        super.onDestroy()
    }

    // ---- 取っ手 ----

    private fun refreshHandle() {
        if (!store.edgeOn || !Settings.canDrawOverlays(this)) return
        val h = handle ?: addHandle() ?: return
        h.visibility = if (homeShown.value) View.GONE else View.VISIBLE
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

    private fun palette() = paletteFor(store.themeMode, resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)

    private fun handleColor() = palette().accent.toArgb()

    // ---- 引き出す ----

    /** ホームと同じタブ・中身を、透けた画面（EdgeActivity）で上に重ねる。 */
    private fun openPanel() {
        runCatching {
            startActivity(
                Intent(this, EdgeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION),
            )
        }
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

    companion object {
        private const val CHANNEL = "edge"
        private const val NOTIFICATION_ID = 42
        private const val HANDLE_DP = 150
        const val ACTION_STOP = "jp.yaegaki.theta.home.edge.STOP"

        /** ホーム・エッジを開いている間は取っ手を隠す。見えたとき・裏へ回ったときに変える。 */
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
