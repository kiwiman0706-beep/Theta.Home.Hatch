package jp.yaegaki.theta.home.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * アプリのアイコンを世界観に合わせた 3 色（地・基本・アクセント）にする。
 * まず「型（マスク）」を作り、表示のときに設定の 3 色で塗る。型は色に依らないので、テーマや配色を変えても作り直さない。
 * 型の作り：地＝黒 (0,0,0)、基本＝赤 (255,0,0)、アクセント＝緑 (0,255,0)。塗るときは赤と緑の量で 3 色を混ぜる（[threeTone]）。
 * - local：端末内で色の違いから 3 段に分ける（無料・すぐ）
 * - gemini：Gemini に 3 色で描き直してもらう（形を整理してくれる。1 アプリ 1 回の呼び出し）
 * 0.15 までの 2 色の型（白地に黒）も読める（基本だけの型として使う）。
 */
object IconStyle {
    const val ORIGINAL = "original"
    const val MONO = "mono"
    const val LOCAL = "local"
    const val GEMINI = "gemini"

    const val DEFAULT_MODEL = "gemini-3.1-flash-image"
    private val FALLBACK_MODELS = listOf("gemini-3.1-flash-image", "gemini-2.5-flash-image")
    private const val SIZE = 192

    private const val PROMPT =
        "Redraw this app icon as a minimal three-tone stencil for a dark sci-fi HUD launcher. " +
            "Output a square 1:1 image using ONLY three flat colors: pure white (#FFFFFF) for the background, " +
            "pure black (#000000) for the main symbol, and pure red (#FF0000) for one small accent part " +
            "(for example a dot, an inner detail or a highlight that was a different color in the original). " +
            "Keep the recognizable silhouette or logo shape of the original so people can still tell which app it is. " +
            "Remove gradients, shadows, textures and small text. Use bold, clean, flat shapes, centered with some margin. " +
            "No border, no frame, no extra elements, no other colors."

    private val BG = Color.BLACK
    private val BASE = Color.RED
    private val ACCENT = Color.GREEN

    private fun hash(appKey: String) = MessageDigest.getInstance("SHA-1").digest(appKey.toByteArray()).joinToString("") { "%02x".format(it) }

    /** Gemini で作った 3 色の型の保存場所。アプリごとに 1 枚。 */
    fun cacheFile(context: Context, appKey: String): File =
        File(File(context.filesDir, "icons3").apply { mkdirs() }, hash(appKey) + ".png")

    /** 0.15 までの 2 色の型（白地に黒）。 */
    fun legacyFile(context: Context, appKey: String): File = File(File(context.filesDir, "icons"), hash(appKey) + ".png")

    /** Gemini の型。3 色が無ければ前の 2 色の型を基本だけの型にして使う。 */
    fun cached(context: Context, appKey: String): Bitmap? {
        cacheFile(context, appKey).takeIf { it.exists() }?.let { f -> BitmapFactory.decodeFile(f.path)?.let { return it } }
        val old = legacyFile(context, appKey).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) } ?: return null
        return fromLegacy(old)
    }

    /** 白地に黒の型 → 地＝黒・基本＝赤。 */
    fun fromLegacy(m: Bitmap): Bitmap {
        val w = m.width
        val h = m.height
        val px = IntArray(w * h)
        m.getPixels(px, 0, w, 0, 0, w, h)
        for (i in px.indices) px[i] = if (lum(px[i]) < 128) BASE else BG
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
    }

    private fun lum(c: Int) = (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000

    /** 端末内で作った型は、作り直さないようメモリに置いておく。 */
    private val localCache = android.util.LruCache<String, Bitmap>(400)

    fun localMaskCached(appKey: String, icon: Bitmap): Bitmap =
        localCache.get(appKey) ?: localMask(icon).also { localCache.put(appKey, it) }

    /**
     * 3 色の型を作る。透明な部分は必ず地。不透明な部分では一番多い色を「アイコンの地」とみなし、それと色が大きく違う部分を形にする。
     * 形の中でもう一度一番多い色を「基本」とし、それと大きく違う部分を「アクセント」にする（少なすぎる・多すぎるときはアクセント無し）。
     * 形が多すぎる（写真のようなアイコン）ときは、明るさで 2 段に分けて基本だけにする。
     */
    fun localMask(icon: Bitmap): Bitmap {
        val src = Bitmap.createScaledBitmap(icon, SIZE, SIZE, true)
        val n = SIZE * SIZE
        val px = IntArray(n)
        src.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val opaque = BooleanArray(n) { Color.alpha(px[it]) >= 128 }
        val opaqueCount = opaque.count { it }
        val out = IntArray(n) { BG }
        if (opaqueCount == 0) return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)

        val bg = dominant(px) { opaque[it] }
        val shape = BooleanArray(n) { opaque[it] && far(px[it], bg) }
        val shapeCount = shape.count { it }
        if (shapeCount <= opaqueCount * 0.6) {
            // 形の中の一番多い色が基本、それと違う色がアクセント
            val base = dominant(px) { shape[it] }
            // 地と基本の境目のにじみ（2 色を混ぜた色）はアクセントにしない
            val accent = BooleanArray(n) { shape[it] && far(px[it], base) && !between(px[it], bg, base) }
            val ac = accent.count { it }
            val useAccent = ac >= shapeCount * 0.04 && ac <= shapeCount * 0.6
            for (i in 0 until n) if (shape[i]) out[i] = if (useAccent && accent[i]) ACCENT else BASE
            return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        }

        // 地の色がはっきりしないアイコン：不透明な部分を明るさで 2 段に分け、少ない方を基本にする
        val l = IntArray(n) { lum(px[it]) }
        val t = otsu(l.filterIndexed { i, _ -> opaque[i] }.toIntArray())
        val darkCount = (0 until n).count { opaque[it] && l[it] < t }
        val darkIsShape = darkCount <= opaqueCount / 2
        for (i in 0 until n) if (opaque[i] && (l[i] < t) == darkIsShape) out[i] = BASE
        return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    /** いちばん多い色（各色 16 段に丸めて数える）。 */
    private fun dominant(px: IntArray, take: (Int) -> Boolean): Int {
        val counts = HashMap<Int, Int>()
        for (i in px.indices) if (take(i)) {
            val c = px[i]
            val q = (Color.red(c) shr 4 shl 8) or (Color.green(c) shr 4 shl 4) or (Color.blue(c) shr 4)
            counts[q] = (counts[q] ?: 0) + 1
        }
        val mode = counts.maxByOrNull { it.value }?.key ?: return Color.WHITE
        return Color.rgb(((mode shr 8) and 15) * 16 + 8, ((mode shr 4) and 15) * 16 + 8, (mode and 15) * 16 + 8)
    }

    /** c が a と b を混ぜた色に近いか（線分 a–b までの距離が近い）。 */
    private fun between(c: Int, a: Int, b: Int): Boolean {
        val ax = Color.red(a).toFloat(); val ay = Color.green(a).toFloat(); val az = Color.blue(a).toFloat()
        val dx = Color.red(b) - ax; val dy = Color.green(b) - ay; val dz = Color.blue(b) - az
        val px = Color.red(c) - ax; val py = Color.green(c) - ay; val pz = Color.blue(c) - az
        val len = dx * dx + dy * dy + dz * dz
        if (len == 0f) return false
        val t = ((px * dx + py * dy + pz * dz) / len).coerceIn(0f, 1f)
        val ex = px - t * dx; val ey = py - t * dy; val ez = pz - t * dz
        return ex * ex + ey * ey + ez * ez < 40f * 40f
    }

    private fun far(c: Int, ref: Int): Boolean {
        val r = Color.red(c) - Color.red(ref)
        val g = Color.green(c) - Color.green(ref)
        val b = Color.blue(c) - Color.blue(ref)
        return r * r + g * g + b * b > 70 * 70
    }

    /** Gemini に型を描き直してもらう。通信するのでメインスレッドでは呼ばない。 */
    fun geminiMask(apiKey: String, model: String, icon: Bitmap): Bitmap {
        val png = ByteArrayOutputStream().also { onWhite(icon, SIZE).compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray()
                .put(JSONObject().put("text", PROMPT))
                .put(JSONObject().put("inline_data", JSONObject().put("mime_type", "image/png").put("data", Base64.encodeToString(png, Base64.NO_WRAP)))))))
            .put("generationConfig", JSONObject().put("responseModalities", JSONArray().put("TEXT").put("IMAGE")))
            .toString()
        val models = (listOf(model) + FALLBACK_MODELS).distinct()
        var last: Exception? = null
        for (m in models) {
            try {
                return binarize(call(apiKey, m, body))
            } catch (e: ModelNotFound) {
                last = e
            }
        }
        throw last ?: IOException("Gemini を呼べませんでした")
    }

    private class ModelNotFound(m: String) : IOException("モデル $m が見つかりません")

    private fun call(apiKey: String, model: String, body: String): Bitmap {
        val c = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent").openConnection() as HttpURLConnection
        c.connectTimeout = 20_000
        c.readTimeout = 90_000
        c.requestMethod = "POST"
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("x-goog-api-key", apiKey)
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        if (code == 404) throw ModelNotFound(model)
        if (code !in 200..299) {
            val err = c.errorStream?.bufferedReader()?.readText().orEmpty()
            val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrDefault(err.take(200))
            throw IOException("Gemini HTTP $code：$msg")
        }
        val json = JSONObject(c.inputStream.bufferedReader().readText())
        val parts = json.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IOException("Gemini から画像が返りませんでした")
        for (i in 0 until parts.length()) {
            val p = parts.getJSONObject(i)
            val data = (p.optJSONObject("inlineData") ?: p.optJSONObject("inline_data"))?.optString("data")
            if (!data.isNullOrEmpty()) {
                val bytes = Base64.decode(data, Base64.DEFAULT)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: throw IOException("画像を読めませんでした")
            }
        }
        throw IOException("Gemini から画像が返りませんでした")
    }

    /** Gemini の画像（白地・黒・赤）を 192px に縮め、いちばん近い色で 3 段の型にする。 */
    private fun binarize(img: Bitmap): Bitmap {
        val src = Bitmap.createScaledBitmap(img, SIZE, SIZE, true)
        val px = IntArray(SIZE * SIZE)
        src.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        fun d(c: Int, r: Int, g: Int, b: Int) = (Color.red(c) - r).let { it * it } + (Color.green(c) - g).let { it * it } + (Color.blue(c) - b).let { it * it }
        for (i in px.indices) {
            val c = px[i]
            val white = d(c, 255, 255, 255)
            val black = d(c, 0, 0, 0)
            val red = d(c, 230, 30, 30)
            px[i] = when (minOf(white, black, red)) {
                white -> BG
                black -> BASE
                else -> ACCENT
            }
        }
        return Bitmap.createBitmap(px, SIZE, SIZE, Bitmap.Config.ARGB_8888)
    }

    private fun onWhite(icon: Bitmap, size: Int): Bitmap {
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(Color.WHITE)
        c.drawBitmap(Bitmap.createScaledBitmap(icon, size, size, true), 0f, 0f, null)
        return out
    }

    private fun otsu(lum: IntArray): Int {
        val hist = IntArray(256)
        lum.forEach { hist[it.coerceIn(0, 255)]++ }
        val total = lum.size
        var sum = 0.0
        for (i in 0 until 256) sum += i * hist[i].toDouble()
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var threshold = 128
        for (i in 0 until 256) {
            wB += hist[i]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += i * hist[i].toDouble()
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) {
                best = between; threshold = i + 1
            }
        }
        return threshold
    }

    /** 型を 3 色で塗る色フィルター：地（黒）を plate、基本（赤）を base、アクセント（緑）を accent に。間の色は混ぜる。 */
    fun threeTone(
        plate: androidx.compose.ui.graphics.Color,
        base: androidx.compose.ui.graphics.Color,
        accent: androidx.compose.ui.graphics.Color,
    ): ColorFilter {
        fun row(p: Float, b: Float, a: Float) = floatArrayOf(b - p, a - p, 0f, 0f, p * 255f)
        val m = row(plate.red, base.red, accent.red) + row(plate.green, base.green, accent.green) + row(plate.blue, base.blue, accent.blue) +
            floatArrayOf(0f, 0f, 0f, 1f, 0f)
        return ColorFilter.colorMatrix(ColorMatrix(m))
    }

    /** 配色の決まった組み合わせ。値は Palette の名前（plate・base・accent の順）。 */
    val PRESETS = listOf(
        "hud" to "HUD（白＋オレンジ）",
        "orange" to "オレンジ（オレンジ＋白）",
        "solid" to "塗り（オレンジ地）",
        "mono" to "モノクロ（白＋灰）",
        "custom" to "自分で選ぶ",
    )

    /** 自分で選ぶときの色の候補。 */
    val SWATCHES = listOf(
        0xFF0A0A0B, 0xFF19191C, 0xFF333336, 0xFF7A7A7E, 0xFFA3A3A3, 0xFFEDEDED, 0xFFFFFFFF,
        0xFFFF6B1A, 0xFFB8400C, 0xFFFFC93C, 0xFF4CAF50, 0xFF29B6F6, 0xFF3F51B5, 0xFFE91E63, 0xFF9C27B0, 0xFFF44336,
    )
}
