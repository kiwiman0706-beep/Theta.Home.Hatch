package jp.yaegaki.theta.home.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import jp.yaegaki.theta.home.MainActivity
import jp.yaegaki.theta.home.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** 録音はマイク用のフォアグラウンドサービスで行い、通知に「停止」を常に出す。 */
class RecordingService : Service() {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRecording()
            return START_NOT_STICKY
        }
        if (recorder != null) return START_NOT_STICKY

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "録音", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("録音中")
            .setContentText("停止すると「記録」に保存します")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", stop).build())
            .build()
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION_ID, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, note)
        }

        val file = Capture.newFile(this, CaptureKind.RECORDING, ".m4a")
        output = file
        try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            state.value = System.currentTimeMillis()
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            if (file.length() == 0L) file.delete()
            stopRecording()
        }
        return START_NOT_STICKY
    }

    private fun stopRecording() {
        recorder?.let { r ->
            try {
                r.stop()
            } catch (_: RuntimeException) {
                // 開始直後に止めると stop() が失敗し、壊れたファイルが残る
                output?.delete()
            } finally {
                r.release()
            }
        }
        recorder = null
        state.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (recorder != null) stopRecording()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 77
        const val ACTION_STOP = "stop"

        /** 録音を始めた時刻。録音していなければ null。 */
        private val state = MutableStateFlow<Long?>(null)
        val startedAt: StateFlow<Long?> = state

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, RecordingService::class.java).setAction(ACTION_STOP))
        }
    }
}
