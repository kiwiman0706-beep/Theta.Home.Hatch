package app.thetahatch.home

import android.content.Intent
import app.thetahatch.home.drive.Gmail
import app.thetahatch.home.hub.HatchShelf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Hatch の棚（押したときはそのアプリ宛てだけ）と、Gmail の差出人の名前。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ShelfTest {
    @Test
    fun shelfIntentStaysInApp() {
        val action = Intent("app.thetahatch.action.OPEN").setPackage("com.evil").putExtra("id", "m1").toUri(Intent.URI_INTENT_SCHEME)
        val item = HatchShelf.Item("pinned", "ピン留め", "m1", "買い物", "", "牛乳", "", action, "#ffcc00")
        val i = HatchShelf.intent("app.thetahatch.memo", item)!!
        assertEquals("app.thetahatch.memo", i.`package`)
        assertEquals("m1", i.getStringExtra("id"))
        assertNull(HatchShelf.intent("app.thetahatch.memo", item.copy(action = "壊れた")))
    }

    @Test
    fun gmailFrom() {
        assertEquals("山田 太郎", Gmail.fromName("\"山田 太郎\" <taro@example.com>"))
        assertEquals("taro@example.com", Gmail.fromName("<taro@example.com>"))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaTest {
    @Test
    fun mediaRootHasAlbumsFirst() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val list = app.thetahatch.home.data.Files.listMedia(ctx, android.net.Uri.parse(app.thetahatch.home.data.Files.MEDIA_ALL))
        assertEquals("アルバム", list.first().name)
        org.junit.Assert.assertTrue(app.thetahatch.home.data.Files.isMedia(list.first().uri))
        // アルバムの一覧（試験の環境では空）
        org.junit.Assert.assertTrue(app.thetahatch.home.data.Files.listMedia(ctx, list.first().uri).isEmpty())
    }
}
