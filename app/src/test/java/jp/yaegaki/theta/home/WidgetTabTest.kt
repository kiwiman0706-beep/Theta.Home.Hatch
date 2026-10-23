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
}
