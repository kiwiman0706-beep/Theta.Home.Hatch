package app.thetahatch.home

import app.thetahatch.home.data.Giga
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** ギガの残り：容量＋今月足した分−使った量、月が変わったら足した分は数えない。 */
class GigaTest {
    @Test
    fun remainingAndLow() {
        val s = Giga.state(usedBytes = 3_800_000_000, planGb = 10.0, extraGb = 2.0, extraMonth = "2026-11", warnGb = 1.0, thisMonth = "2026-11")
        assertEquals(12.0, s.totalGb, 1e-9)
        assertEquals(8.2, s.remainingGb, 1e-9)
        assertFalse(s.low)
        val low = Giga.state(11_500_000_000, 10.0, 2.0, "2026-11", 1.0, "2026-11")
        assertTrue(low.low)
        // 先月の足した分は数えない
        assertEquals(10.0, Giga.state(0, 10.0, 2.0, "2026-10", 1.0, "2026-11").totalGb, 1e-9)
        // 使いすぎても 0 より下にしない
        assertEquals(0.0, Giga.state(20_000_000_000, 10.0, 0.0, "", 1.0, "2026-11").remainingGb, 1e-9)
    }

    @Test
    fun alignToMyIijmio() {
        // My IIJmio で残り 6.8GB、端末では 4GB 使った、容量 10GB → 足した分 0.8GB
        assertEquals(0.8, Giga.extraFor(6.8, 4_000_000_000, 10.0), 1e-9)
    }

    @Test
    fun monthStartsOnFirst() {
        assertEquals("2026-11", Giga.month(LocalDate.of(2026, 11, 30)))
        val zone = ZoneId.of("Asia/Tokyo")
        assertEquals(LocalDate.of(2026, 11, 1).atStartOfDay(zone).toInstant().toEpochMilli(), Giga.monthStartMillis(LocalDate.of(2026, 11, 7), zone))
    }
}
