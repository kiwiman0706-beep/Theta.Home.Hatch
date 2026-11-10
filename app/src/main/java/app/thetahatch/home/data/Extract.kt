package app.thetahatch.home.data

import android.content.ContentValues
import android.content.Intent
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 文章・画像（メールの署名・本文、スクショ、写真）から、Gemini で連絡先・予定を読み取る。
 * 結果は下書き。端末の「連絡先に追加」「予定を追加」の画面に渡し、利用者が確かめて保存する（このアプリは書き込まない）。
 */
object Extract {
    data class Contact(
        val name: String = "", val kana: String = "", val company: String = "", val department: String = "", val title: String = "",
        val phones: List<String> = emptyList(), val emails: List<String> = emptyList(), val address: String = "", val url: String = "", val note: String = "",
    )

    /** start・end は "yyyy-MM-ddTHH:mm"（終日なら "yyyy-MM-dd"）。 */
    data class Event(val title: String = "", val start: String = "", val end: String = "", val allDay: Boolean = false, val location: String = "", val note: String = "")

    private fun str(type: String = "STRING") = JSONObject().put("type", type)
    private fun obj(vararg props: Pair<String, JSONObject>) =
        JSONObject().put("type", "OBJECT").put("properties", JSONObject().apply { props.forEach { put(it.first, it.second) } })
    private fun arr(item: JSONObject) = JSONObject().put("type", "ARRAY").put("items", item)

    private val contactSchema = arr(obj(
        "name" to str(), "kana" to str(), "company" to str(), "department" to str(), "title" to str(),
        "phones" to arr(str()), "emails" to arr(str()), "address" to str(), "url" to str(), "note" to str(),
    ))
    private val eventSchema = arr(obj(
        "title" to str(), "start" to str(), "end" to str(), "allDay" to str("BOOLEAN"), "location" to str(), "note" to str(),
    ))

    /** Gemini に送る中身。images は JPEG のバイト列。 */
    fun request(kind: String, text: String, images: List<ByteArray>, today: LocalDate = LocalDate.now()): JSONObject {
        val ask = if (kind == "contact") {
            "次の文章・画像（メールの署名、名刺、スクショなど）から、人の連絡先を読み取ってください。" +
                "複数の人がいればそれぞれ。kana は名前の読み（分かるときだけ、ひらがな）。電話は書いてある形のまま。無い項目は空にしてください。"
        } else {
            "次の文章・画像（メールの本文、案内、チラシ、スクショなど）から、予定を読み取ってください。今日は $today（${today.dayOfWeek}）、日本時間です。" +
                "「来週の火曜」などは日付にしてください。start・end は yyyy-MM-ddTHH:mm、時刻が無い終日の予定は yyyy-MM-dd にして allDay を true に。" +
                "end が分からなければ start の 1 時間後。note には持ち物・URL・連絡先など大事なことを短く。予定が無ければ空の配列。"
        }
        val parts = JSONArray().put(JSONObject().put("text", ask + if (text.isNotBlank()) "\n\n---\n$text" else ""))
        images.forEach { parts.put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/jpeg").put("data", Base64.encodeToString(it, Base64.NO_WRAP)))) }
        return JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", parts)))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json").put("responseSchema", if (kind == "contact") contactSchema else eventSchema))
    }

    private fun JSONObject.s(k: String) = optString(k).trim()
    private fun JSONObject.list(k: String) = optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it).trim() }.filter { it.isNotEmpty() } }.orEmpty()

    fun parseContacts(json: String): List<Contact> {
        val a = JSONArray(json)
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Contact(it.s("name"), it.s("kana"), it.s("company"), it.s("department"), it.s("title"), it.list("phones"), it.list("emails"), it.s("address"), it.s("url"), it.s("note"))
        }.filter { it.name.isNotEmpty() || it.company.isNotEmpty() || it.phones.isNotEmpty() || it.emails.isNotEmpty() }
    }

    fun parseEvents(json: String): List<Event> {
        val a = JSONArray(json)
        return (0 until a.length()).map { a.getJSONObject(it) }.map {
            Event(it.s("title"), it.s("start"), it.s("end"), it.optBoolean("allDay"), it.s("location"), it.s("note"))
        }.filter { it.title.isNotEmpty() && it.start.isNotEmpty() }
    }

    /** "2026-11-10T14:00" か "2026-11-10" をミリ秒に。読めなければ null。 */
    fun millis(s: String, zone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
        if (s.length <= 10) LocalDate.parse(s).atStartOfDay(zone).toInstant().toEpochMilli()
        else LocalDateTime.parse(s.take(16)).atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** 端末の「連絡先に追加」の画面。 */
    fun contactIntent(c: Contact): Intent {
        val i = Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE)
        i.putExtra(ContactsContract.Intents.Insert.NAME, c.name)
        if (c.kana.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.PHONETIC_NAME, c.kana)
        if (c.company.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.COMPANY, c.company)
        listOf(c.department, c.title).filter { it.isNotEmpty() }.joinToString(" ").takeIf { it.isNotEmpty() }?.let { i.putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        listOf(ContactsContract.Intents.Insert.PHONE, ContactsContract.Intents.Insert.SECONDARY_PHONE, ContactsContract.Intents.Insert.TERTIARY_PHONE)
            .zip(c.phones).forEach { (k, v) -> i.putExtra(k, v) }
        listOf(ContactsContract.Intents.Insert.EMAIL, ContactsContract.Intents.Insert.SECONDARY_EMAIL, ContactsContract.Intents.Insert.TERTIARY_EMAIL)
            .zip(c.emails).forEach { (k, v) -> i.putExtra(k, v) }
        if (c.address.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.POSTAL, c.address)
        if (c.note.isNotEmpty()) i.putExtra(ContactsContract.Intents.Insert.NOTES, c.note)
        if (c.url.isNotEmpty()) {
            val web = ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Website.URL, c.url)
            }
            i.putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, arrayListOf(web))
        }
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 端末の「予定を追加」の画面。 */
    fun eventIntent(e: Event): Intent {
        val i = Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, e.title)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, e.allDay)
        millis(e.start)?.let { i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
        (millis(e.end) ?: millis(e.start)?.plus(if (e.allDay) 86_400_000L else 3_600_000L))?.let { i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
        if (e.location.isNotEmpty()) i.putExtra(CalendarContract.Events.EVENT_LOCATION, e.location)
        if (e.note.isNotEmpty()) i.putExtra(CalendarContract.Events.DESCRIPTION, e.note)
        return i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
