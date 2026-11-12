package app.thetahatch.home

import app.thetahatch.home.data.FavPerson
import app.thetahatch.home.data.LineLink
import app.thetahatch.home.data.PeopleLinks
import app.thetahatch.home.notify.Notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 人と LINE のトークの結び付け・よく連絡する人の保存・名前の近さ・未読。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PeopleLinksTest {
    @Test
    fun saveAndLoad() {
        val links = mapOf("abc" to LineLink("mid123", "山田太郎"))
        assertEquals(links, PeopleLinks.linksFromJson(PeopleLinks.linksToJson(links)))
        val favs = listOf(FavPerson("c:abc", "山田太郎"), FavPerson("line:g1", "家族"))
        assertEquals(favs, PeopleLinks.favsFromJson(PeopleLinks.favsToJson(favs)))
        assertEquals("abc", favs[0].contactKey)
        assertEquals("g1", favs[1].lineId)
        assertEquals(emptyMap<String, LineLink>(), PeopleLinks.linksFromJson("壊れた"))
        // LINE WORKS などほかのアプリのトーク
        assertEquals("com.example.works" to "room|7", FavPerson("talk:com.example.works|room|7", "チーム").talk)
        assertEquals("jp.naver.line.android" to "g1", favs[1].talk)
    }

    @Test
    fun similarity() {
        assertEquals(100, PeopleLinks.similarity("山田 太郎", "山田太郎"))
        assertTrue(PeopleLinks.similarity("たろう", "たろうさん") >= 80)
        assertTrue(PeopleLinks.similarity("山田太郎", "鈴木花子") < 30)
    }

    @Test
    fun unread() {
        val line = "jp.naver.line.android"
        val n = listOf(
            Notice("1", line, "家族", "ごはん", 0, null, shortcutId = "g1"),
            Notice("2", line, "家族", "まだ？", 0, null),
            Notice("3", line, "山田太郎", "了解", 0, null, shortcutId = "mid123"),
            Notice("4", "com.example", "家族", "x", 0, null),
        )
        assertEquals(2, PeopleLinks.unread(n, LineLink("g1", "家族")))
        assertEquals(1, PeopleLinks.unread(n, LineLink("mid123", "やまだ")))
        assertEquals(0, PeopleLinks.unread(n, null))
        assertEquals(1, PeopleLinks.unread(n, LineLink("x", "家族"), "com.example"))
    }
}
