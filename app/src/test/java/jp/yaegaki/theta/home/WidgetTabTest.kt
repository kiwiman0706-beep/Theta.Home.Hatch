package jp.yaegaki.theta.home

import androidx.test.core.app.ApplicationProvider
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

    @Test
    fun storeRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val t = WidgetTab(5, "カメラ", 300, 200, listOf(WidgetCell(7, 250, 150), WidgetCell(9, 200, 120)))
        app.store.saveWidgetTabs("narrow", listOf(t))
        assertEquals(listOf(t), app.store.widgetTabs("narrow"))
    }

    /** タブと中身は両方の画面で共通、大きさだけ画面ごと。 */
    @Test
    fun sharedTabsSizesPerScreen() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        app.store.prefs.edit().clear().commit()
        val t = WidgetTab(5, "カメラ", 300, 200, listOf(WidgetCell(7, 250, 150)))
        app.store.saveWidgetTabs("narrow", listOf(t))
        // 開いた画面にも同じタブがある
        assertEquals(listOf(5, 7), app.store.widgetTabs("wide").single().cells.map { it.id })
        // 開いた画面で大きくしても、閉じた画面の大きさはそのまま
        app.store.saveWidgetTabs("wide", listOf(t.copy(w = 600, h = 400)))
        assertEquals(600, app.store.widgetTabs("wide").single().w)
        assertEquals(300, app.store.widgetTabs("narrow").single().w)
    }

    /** 0.19.0 までの画面ごとのタブは 1 つにまとめ、大きさはそれぞれの画面のものを残す。 */
    @Test
    fun mergeOldProfiles() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val prefs = app.store.prefs
        prefs.edit().clear().commit()
        fun tab(id: Int, w: Int) = """{"id":$id,"label":"W$id","w":$w,"h":200,"extra":[]}"""
        prefs.edit()
            .putString("wtabs_narrow", "[${tab(1, 300)},${tab(2, 300)}]")
            .putString("wtabs_wide", "[${tab(2, 700)},${tab(3, 500)}]")
            .commit()
        assertEquals(listOf(1, 2, 3), app.store.widgetTabs("narrow").map { it.id })
        assertEquals(700, app.store.widgetTabs("wide").first { it.id == 2 }.w)
        assertEquals(300, app.store.widgetTabs("narrow").first { it.id == 2 }.w)
    }

    @Test
    fun tabOptsRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<HomeApp>()
        val o = jp.yaegaki.theta.home.data.TabOpts(show = "wide", width = mapOf("narrow" to 320), apps = listOf("a/b"), split = true)
        app.store.saveTabOpts(mapOf("t1" to o, "t2" to jp.yaegaki.theta.home.data.TabOpts()))
        assertEquals(mapOf("t1" to o), app.store.tabOpts())
        assertEquals(false, o.shownOn("narrow"))
    }
}
