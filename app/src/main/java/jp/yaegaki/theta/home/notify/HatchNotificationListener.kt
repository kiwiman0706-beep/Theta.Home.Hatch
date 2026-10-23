package jp.yaegaki.theta.home.notify

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Notice(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val time: Long,
    val intent: PendingIntent?,
) {
    val isLine get() = packageName == LINE
    val initial get() = title.trim().take(1).ifEmpty { "?" }

    companion object {
        const val LINE = "jp.naver.line.android"
        val MAIL = setOf(
            "com.google.android.gm",
            "com.samsung.android.email.provider",
            "com.microsoft.office.outlook",
            "jp.co.yahoo.android.ymail",
            "com.android.email",
        )
    }
}

/**
 * LINE には公開 API がないため、届いた通知の内容をドロワーに出す。メールも同じ仕組みで「届いたもの」を表示する。
 * カテゴリーのタブでは、そのカテゴリーのアプリの通知をアプリの下に並べ、アイコンに件数を付ける。
 * 再生中・通話中などの「消せない通知」は除く。通知は端末内のメモリだけに持ち、保存も送信もしない。
 */
class HatchNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        refresh()
    }

    override fun onListenerDisconnected() {
        state.value = emptyList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (watched(sbn.packageName)) refresh()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (watched(sbn.packageName)) refresh()
    }

    private fun watched(pkg: String) = pkg != packageName

    private fun refresh() {
        val list = runCatching { activeNotifications }.getOrNull() ?: emptyArray()
        state.value = list
            .filter {
                val f = it.notification.flags
                watched(it.packageName) && f and Notification.FLAG_GROUP_SUMMARY == 0 &&
                    f and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) == 0
            }
            .mapNotNull { sbn ->
                val ex = sbn.notification.extras
                val title = ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                    ?: ex.getCharSequence(Notification.EXTRA_TITLE) ?: return@mapNotNull null
                val text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT) ?: ""
                Notice(sbn.key, sbn.packageName, title.toString(), text.toString(), sbn.postTime, sbn.notification.contentIntent)
            }
            .sortedByDescending { it.time }
    }

    companion object {
        private val state = MutableStateFlow<List<Notice>>(emptyList())
        val notices: StateFlow<List<Notice>> = state

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun component(context: Context) = ComponentName(context, HatchNotificationListener::class.java)

        /** 通知を押したのと同じ動作（LINE の該当トークなど）を開く。開けなければ false。 */
        fun open(context: Context, notice: Notice): Boolean {
            val pi = notice.intent ?: return false
            return runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(context, 0, null, null, null, null, opts.toBundle())
                } else {
                    pi.send()
                }
                true
            }.getOrDefault(false)
        }
    }
}
