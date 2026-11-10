package app.thetahatch.home

import android.provider.CalendarContract
import android.provider.ContactsContract
import app.thetahatch.home.data.Extract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** 貼り付けて登録：Gemini の答えの読み方と、端末の追加画面へ渡す中身。 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExtractTest {
    @Test
    fun contacts() {
        val list = Extract.parseContacts("""[{"name":"山田 太郎","kana":"やまだ たろう","company":"テータ株式会社","department":"開発部","title":"部長",
            "phones":["03-1234-5678","090-1111-2222"],"emails":["taro@example.com"],"address":"東京都","url":"https://example.com","note":""},
            {"name":"","company":"","phones":[],"emails":[]}]""")
        assertEquals(1, list.size)
        val i = Extract.contactIntent(list[0])
        assertEquals("山田 太郎", i.getStringExtra(ContactsContract.Intents.Insert.NAME))
        assertEquals("090-1111-2222", i.getStringExtra(ContactsContract.Intents.Insert.SECONDARY_PHONE))
        assertEquals("開発部 部長", i.getStringExtra(ContactsContract.Intents.Insert.JOB_TITLE))
        assertEquals(1, i.getParcelableArrayListExtra<android.content.ContentValues>(ContactsContract.Intents.Insert.DATA)!!.size)
    }

    @Test
    fun events() {
        val list = Extract.parseEvents("""[{"title":"歯医者","start":"2026-11-17T10:00","end":"","allDay":false,"location":"駅前","note":"保険証"},
            {"title":"運動会","start":"2026-11-23","end":"","allDay":true}, {"title":"","start":""}]""")
        assertEquals(2, list.size)
        val i = Extract.eventIntent(list[0])
        val begin = i.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, 0)
        assertEquals(3_600_000L, i.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, 0) - begin)
        assertEquals("駅前", i.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertTrue(Extract.eventIntent(list[1]).getBooleanExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false))
        assertNull(Extract.millis("来週"))
        assertEquals(LocalDate.of(2026, 11, 23).atStartOfDay(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli(), Extract.millis("2026-11-23", ZoneId.of("Asia/Tokyo")))
    }

    @Test
    fun requestHasTodayAndSchema() {
        val r = Extract.request("event", "来週火曜 10 時 歯医者", emptyList(), LocalDate.of(2026, 11, 10))
        val text = r.getJSONArray("contents").getJSONObject(0).getJSONArray("parts").getJSONObject(0).getString("text")
        assertTrue(text.contains("2026-11-10") && text.contains("来週火曜"))
        assertEquals("ARRAY", r.getJSONObject("generationConfig").getJSONObject("responseSchema").getString("type"))
    }
}
