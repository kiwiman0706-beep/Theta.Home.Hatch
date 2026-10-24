package jp.yaegaki.theta.home.data

import java.text.Normalizer
import java.util.Locale

/**
 * 話した言葉からアプリを選ぶ。「LINE 開いて」「カメラを起動」のような言い方の後ろを取り、
 * ひらがなはカタカナにそろえ、全角・半角・大文字小文字・空白の違いを無くして比べる。
 */
object VoiceMatch {
    private val SUFFIXES = listOf("を開いて", "開いて", "をひらいて", "ひらいて", "を起動して", "を起動", "起動して", "起動", "を開く", "開く", "アプリ", "お願い", "して")

    fun normalize(s: String): String {
        val n = Normalizer.normalize(s, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        val sb = StringBuilder()
        for (ch in n) {
            when {
                ch.isWhitespace() || ch in "・･-_.,、。!?！？「」()（）" -> Unit
                ch in 'ぁ'..'ゖ' -> sb.append(ch + 0x60)
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    /** 言い方の後ろ（開いて・起動など）を取ったもの。 */
    fun core(spoken: String): String {
        var s = normalize(spoken)
        var changed = true
        while (changed) {
            changed = false
            for (suf in SUFFIXES.map { normalize(it) }) if (s.length > suf.length && s.endsWith(suf)) {
                s = s.dropLast(suf.length); changed = true
            }
        }
        return s
    }

    /** 0〜100。同じ 100、前が同じ 80、含む 60、あとは似ている度合い。 */
    fun score(label: String, spoken: String): Int {
        val a = normalize(label)
        val b = core(spoken)
        if (a.isEmpty() || b.isEmpty()) return 0
        return when {
            a == b -> 100
            b.length >= 2 && (a.startsWith(b) || b.startsWith(a)) -> 80
            b.length >= 2 && (a.contains(b) || b.contains(a)) -> 60
            else -> (lcs(a, b) * 50) / maxOf(a.length, b.length)
        }
    }

    /**
     * 聞き取りの候補（よい順）から、開くアプリを 1 つ決める。はっきり 1 つに決まらなければ null。
     */
    fun <T> pick(items: List<T>, label: (T) -> String, heard: List<String>): T? {
        for (h in heard.take(3)) {
            val scored = items.map { it to score(label(it), h) }.sortedByDescending { it.second }
            val best = scored.firstOrNull() ?: return null
            val second = scored.getOrNull(1)?.second ?: 0
            if (best.second >= 80 && best.second > second) return best.first
        }
        return null
    }

    private fun lcs(a: String, b: String): Int {
        val dp = IntArray(b.length + 1)
        for (i in 1..a.length) {
            var prev = 0
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev + 1 else maxOf(dp[j], dp[j - 1])
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
