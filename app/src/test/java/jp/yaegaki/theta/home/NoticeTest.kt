package jp.yaegaki.theta.home

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import jp.yaegaki.theta.home.notify.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NoticeTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<HomeApp>()
    private fun pi(n: Int) = PendingIntent.getBroadcast(ctx, n, Intent("x$n"), PendingIntent.FLAG_IMMUTABLE)
    private fun action(title: String, n: Int) = Notification.Action.Builder(null, title, pi(n)).build()

    @Test
    fun findsReadAndReply() {
        val reply = Notification.Action.Builder(null, "返信", pi(1)).addRemoteInput(RemoteInput.Builder("text").build()).build()
        val read = action("既読にする", 2)
        val n = Notice("k", "jp.naver.line.android", "田中", "こんにちは", 0, null, listOf(reply, read, action("ミュート", 3)))
        assertEquals("既読にする", n.readAction?.title)
        assertEquals("返信", n.replyAction?.title)
    }

    @Test
    fun englishMarkAsRead() {
        val n = Notice("k", "com.example", "a", "b", 0, null, listOf(action("Archive", 1), action("Mark as read", 2)))
        assertEquals("Mark as read", n.readAction?.title)
        assertNull(n.replyAction)
    }
}
