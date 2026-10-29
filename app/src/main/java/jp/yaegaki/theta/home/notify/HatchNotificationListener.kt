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
    /** 通知に付いているボタン（既読・返信・アーカイブなど）。 */
    val actions: List<Notification.Action> = emptyList(),
) {
    /** 「既読にする」のボタン（アプリによって名前が違うので、よくある言い方で探す）。 */
    val readAction: Notification.Action?
        get() = actions.firstOrNull { a ->
            Notification.Action.SEMANTIC_ACTION_MARK_AS_READ == a.semanticAction ||
                READ_WORDS.containsMatchIn(a.title?.toString().orEmpty())
        }

    /** 文字を入れて返せるボタン（返信）。 */
    val replyAction: Notification.Action?
        get() = actions.firstOrNull { a -> a.remoteInputs?.any { it.allowFreeFormInput } == true }

    val isLine get() = packageName == LINE
    val initial get() = title.trim().take(1).ifEmpty { "?" }

    companion object {
        const val LINE = "jp.naver.line.android"
        private val READ_WORDS = Regex("既読|開封|mark as read|mark read|^read$", RegexOption.IGNORE_CASE)
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
        instance = this
        refresh()
        // 通知へのアクセスがあれば、音楽アプリの再生も見られる
        MediaWatch.start(this)
    }

    override fun onListenerDisconnected() {
        instance = null
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
                Notice(sbn.key, sbn.packageName, title.toString(), text.toString(), sbn.postTime, sbn.notification.contentIntent, sbn.notification.actions?.toList().orEmpty())
            }
            .sortedByDescending { it.time }
    }

    companion object {
        private val state = MutableStateFlow<List<Notice>>(emptyList())
        @Volatile private var instance: HatchNotificationListener? = null

        /** 通知を消す（端末の通知からも）。 */
        fun dismiss(n: Notice) {
            runCatching { instance?.cancelNotification(n.key) }
            state.value = state.value.filter { it.key != n.key }
        }

        /** しばらく隠して、あとでもう一度出す。 */
        fun snooze(n: Notice, millis: Long) {
            runCatching { instance?.snoozeNotification(n.key, millis) }
            state.value = state.value.filter { it.key != n.key }
        }

        /** 既読にする：「既読」のボタンがあれば押して（相手にも既読が付く）、通知を消す。ボタンが無ければ消すだけ。true なら既読のボタンを押した。 */
        fun markRead(context: Context, n: Notice): Boolean {
            val a = n.readAction
            val sent = a != null && runCatching { a.actionIntent.send(); true }.getOrDefault(false)
            dismiss(n)
            return sent
        }

        /** 通知のボタンを押す。 */
        fun fire(context: Context, a: Notification.Action): Boolean = runCatching { a.actionIntent.send(); true }.getOrDefault(false)

        /** その場で返信する（通知の「返信」と同じ）。 */
        fun reply(context: Context, n: Notice, text: String): Boolean {
            val a = n.replyAction ?: return false
            return runCatching {
                val intent = android.content.Intent()
                val bundle = android.os.Bundle()
                a.remoteInputs.filter { it.allowFreeFormInput }.forEach { bundle.putCharSequence(it.resultKey, text) }
                android.app.RemoteInput.addResultsToIntent(a.remoteInputs, intent, bundle)
                a.actionIntent.send(context, 0, intent)
                true
            }.getOrDefault(false)
        }
        val notices: StateFlow<List<Notice>> = state

        /** 試験で、見本の通知を入れる。 */
        @androidx.annotation.VisibleForTesting
        internal fun setForTest(list: List<Notice>) { state.value = list }

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
