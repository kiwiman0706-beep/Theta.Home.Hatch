package jp.yaegaki.theta.home

import androidx.test.core.app.ApplicationProvider
import jp.yaegaki.theta.home.data.BoardItem
import jp.yaegaki.theta.home.data.PinnedShortcut
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabOpts
import jp.yaegaki.theta.home.data.WidgetCell
import jp.yaegaki.theta.home.data.WidgetTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetTabTest {
    @Test
    fun cells() {
        val t = WidgetTab(5, "カメラ", 300, 200, listOf(WidgetCell(7, 250, 150)))
        assertEquals(listOf(5, 7), t.cells.map { it.id })
        // 1 つ目を外すと 2 つ目がタブの id になる
        val n = t.withCells(t.cells.drop(1))!!
        assertEquals(7, n.id)
        assertEquals(250, n.w)
        assertEquals(emptyList<WidgetCell>(), n.extra)
        assertNull(t.withCells(emptyList()))
    }

    private fun tab(id: Int, w: Int, h: Int = 200, extra: String = "") = """{"id":$id,"label":"W$id","w":$w,"h":$h,"extra":[$extra]}"""

    /** 0.20.0 までの画面ごとのウィジェットのタブは、ふつうのタブに移す。高さ・幅はそれぞれの画面のものを残す。 */
    @Test
    fun migrateToBoards() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val store = app.store
        store.prefs.edit().clear().commit()
        store.prefs.edit()
            .putString("wtabs_narrow", "[${tab(1, 300, extra = """{"id":4,"w":280,"h":150}""")},${tab(2, 300)}]")
            .putString("wtabs_wide", "[${tab(2, 700, 400)},${tab(3, 500)}]")
            .putString("tabOpts", """{"w:2":{"show":"wide","apps":["a/b"]}}""")
            .commit()
        store.saveShortcuts(listOf(PinnedShortcut("s", "p", "x", "トーク", "tab:w:2")))
        val boards = store.tabs().filter { it.kind == TabKind.BOARD && it.label.matches(Regex("W[0-9]")) }
        assertEquals(listOf("W1", "W2", "W3"), boards.map { it.label })
        val items = store.boards()
        assertEquals(listOf("1", "4"), items[boards[0].id]!!.map { it.ref })
        val w2 = items[boards[1].id]!!.single()
        assertEquals(BoardItem.WIDGET, w2.type)
        assertEquals(200, w2.height("narrow"))
        assertEquals(400, w2.height("wide"))
        // タブの設定と置いたショートカットも新しいタブへ
        val o = store.tabOpts()[boards[1].id]!!
        assertEquals("wide", o.show)
        assertEquals(listOf("a/b"), o.apps)
        assertEquals(716, o.width["wide"])
        assertEquals("tab:${boards[1].id}", store.shortcuts().single().catId)
        // 2 回目は何もしない
        assertEquals(3, store.tabs().count { it.kind == TabKind.BOARD && it.label.matches(Regex("W[0-9]")) })
    }

    @Test
    fun boardItemsRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val item = BoardItem("i1", BoardItem.WEB, "https://example.com", "例", half = true, h = mapOf("wide" to 500))
        app.store.saveBoards(mapOf("t" to listOf(item)))
        assertEquals(listOf(item), app.store.boards()["t"])
        assertEquals(500, item.height("narrow"))
        assertEquals(BoardItem.defaultHeight(BoardItem.FOLDER), BoardItem("x", BoardItem.FOLDER).height("wide"))
    }

    @Test
    fun tabOptsRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val o = TabOpts(show = "wide", width = mapOf("narrow" to 320), apps = listOf("a/b"), split = true)
        app.store.saveTabOpts(mapOf("t1" to o, "t2" to TabOpts()))
        assertEquals(mapOf("t1" to o), app.store.tabOpts())
        assertEquals(false, o.shownOn("narrow"))
    }

    /** 0.21.0 までの専用のタブは、中身が 1 つ（縦いっぱい）のふつうのタブに。id・名前・フォルダはそのまま。 */
    @Test
    fun migratePanels() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val store = app.store
        store.prefs.edit().clear().commit()
        store.prefs.edit()
            .putBoolean("gdocsTab", true)
            .putString("tabs", """[{"id":"work","kind":"FOLDER","label":"WORK","folder":"nas:/home"},{"id":"cal","kind":"CALENDAR","label":"予定","folder":""},{"id":"c","kind":"CATEGORY","label":"COM","folder":"cat1"}]""")
            .putString("location", "tab:work")
            .commit()
        val tabs = store.tabs()
        assertEquals(listOf(TabKind.BOARD, TabKind.BOARD, TabKind.CATEGORY), tabs.map { it.kind })
        assertEquals(listOf("work", "cal", "c"), tabs.map { it.id })
        val work = store.boards()["work"]!!.single()
        assertEquals(BoardItem.FOLDER, work.type)
        assertEquals("nas:/home", work.ref)
        assertEquals(true, work.fill)
        assertEquals("calendar", store.boards()["cal"]!!.single().type)
        assertEquals("tab:m-work", store.location)
        // 2 回目は何もしない
        assertEquals(tabs, store.tabs())
        assertEquals(1, store.boards()["work"]!!.size)
    }
}
