package jp.yaegaki.theta.home.data

import java.net.HttpURLConnection
import java.net.URL

/** 回線の速さ。down は Mbps、ping はミリ秒、wifi は Wi-Fi で測ったか。 */
data class Speed(val down: Double, val ping: Long, val at: Long, val wifi: Boolean)

/**
 * 回線の速さを測る（Cloudflare の速度計測の入口から決まった大きさを取る）。
 * モバイルデータを使いすぎないよう、1 回に取るのは Wi-Fi で 1MB、モバイルで 0.5MB だけ。
 */
object NetSpeed {
    private const val BASE = "https://speed.cloudflare.com/__down?bytes="

    fun measure(wifi: Boolean): Speed {
        val ping = timeToFirstByte(BASE + "0")
        val bytes = if (wifi) 1_000_000 else 500_000
        val start = System.nanoTime()
        val got = download(BASE + bytes)
        val sec = (System.nanoTime() - start) / 1e9
        return Speed(mbps(got, sec), ping, System.currentTimeMillis(), wifi)
    }

    /** 取ったバイト数と秒から Mbps。 */
    fun mbps(bytes: Long, seconds: Double): Double = if (seconds <= 0) 0.0 else bytes * 8 / seconds / 1_000_000

    /** 画面に出す形：100 以上は整数、10 以上は整数、それより下は小数 1 桁。 */
    fun label(mbps: Double): String = if (mbps >= 10) "%.0f".format(mbps) else "%.1f".format(mbps)

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 15_000
        useCaches = false
    }

    private fun timeToFirstByte(url: String): Long {
        val c = open(url)
        val start = System.nanoTime()
        try {
            if (c.responseCode / 100 != 2) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.use { it.read() }
        } finally {
            c.disconnect()
        }
        return (System.nanoTime() - start) / 1_000_000
    }

    private fun download(url: String): Long {
        val c = open(url)
        try {
            if (c.responseCode / 100 != 2) throw java.io.IOException("HTTP ${c.responseCode}")
            var total = 0L
            val buf = ByteArray(16 * 1024)
            c.inputStream.use { s ->
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    total += n
                }
            }
            return total
        } finally {
            c.disconnect()
        }
    }
}
