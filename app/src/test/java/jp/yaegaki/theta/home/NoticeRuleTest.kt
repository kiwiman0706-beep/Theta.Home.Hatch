package jp.yaegaki.theta.home

import jp.yaegaki.theta.home.notify.Notice
import jp.yaegaki.theta.home.notify.NoticeRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NoticeRuleTest {
    private fun n(pkg: String, title: String, text: String) = Notice(title, pkg, title, text, 0, null)

    @Test
    fun appsAndWords() {
        val r = NoticeRule("1", "至急", apps = listOf("com.google.android.gm"), include = listOf("至急", "urgent"), exclude = listOf("広告"))
        assertTrue(r.matches(n("com.google.android.gm", "【至急】見積もり", "")))
        assertTrue(r.matches(n("com.google.android.gm", "Re", "URGENT please")))
        assertFalse(r.matches(n("jp.naver.line.android", "至急", "")))
        assertFalse(r.matches(n("com.google.android.gm", "至急セール", "広告です")))
        assertFalse(r.matches(n("com.google.android.gm", "お知らせ", "")))
    }

    @Test
    fun emptyMeansAll() {
        assertTrue(NoticeRule("1", "LINE", apps = listOf("jp.naver.line.android")).matches(n("jp.naver.line.android", "母", "")))
        // ひらがな・カタカナの違いは見ない
        assertTrue(NoticeRule("2", "配達", include = listOf("はいたつ")).matches(n("x", "ハイタツ予定", "")))
    }

    @Test
    fun pickAndJson() {
        val rules = listOf(NoticeRule("a", "A", include = listOf("A")), NoticeRule("b", "B", include = listOf("B"), alert = true))
        val list = NoticeRule.pick(rules, listOf(n("x", "B1", ""), n("x", "C", ""), n("x", "A1", "")))
        assertEquals(listOf("B1" to "b", "A1" to "a"), list.map { it.first.title to it.second.id })
        assertEquals(rules[1], NoticeRule.fromJson(rules[1].toJson()))
        assertEquals(listOf("至急", "配達"), NoticeRule.words("至急、配達,  ,至急"))
    }
}
