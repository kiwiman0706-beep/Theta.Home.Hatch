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
 * アプリのアイコンを世界観に合わせた 2 色（ツートーン）にする。
 * まず白地に黒の「型（マスク）」を作り、表示のときにテーマの 2 色で塗る。型はテーマに依らないので、ダーク・ライトを切り替えても作り直さない。
 * - local：端末内で明るさを 2 段に分けるだけ（無料・すぐ）
 * - gemini：Gemini に型を描き直してもらう（形を整理してくれる。1 アプリ 1 回の呼び出し）
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
        "Redraw this app icon as a minimal two-tone stencil for a dark sci-fi HUD launcher. " +
            "Output a square 1:1 image using ONLY pure black (#000000) for the symbol and pure white (#FFFFFF) for the background. " +
            "Keep the recognizable silhouette or logo shape of the original so people can still tell which app it is. " +
            "Remove gradients, shadows, colors, textures and small text. Use bold, clean, flat shapes, centered with some margin. " +
            "No border, no frame, no extra elements, no other colors."

    /** Gemini で作った型の保存場所。アプリごとに 1 枚。 */
    fun cacheFile(context: Context, appKey: String): File {
        val name = MessageDigest.getInstance("SHA-1").digest(appKey.toByteArray()).joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, "icons").apply { mkdirs() }, "$name.png")
    }

    fun cached(context: Context, appKey: String): Bitmap? =
        cacheFile(context, appKey).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }

    /** 端末内で作った型は、作り直さないようメモリに置いておく。 */
    private val localCache = android.util.LruCache<String, Bitmap>(400)

    fun localMaskCached(appKey: String, icon: Bitmap): Bitmap =
        localCache.get(appKey) ?: localMask(icon).also { localCache.put(appKey, it) }

    /**
     * 型（白地に黒い形）を作る。透明な部分は必ず地。不透明な部分では一番多い色を「アイコンの地」とみなし、
     * それと色が大きく違う部分を形にする（明るさの近い赤と緑も分けられる）。
     * 形が多すぎる（写真のようなアイコン）ときは、明るさで 2 段に分ける方法に切り替える。
     */
    fun localMask(icon: Bitmap): Bitmap {
        val src = Bitmap.createScaledBitmap(icon, SIZE, SIZE, true)
        val n = SIZE * SIZE
        val px = IntArray(n)
        src.getPixels(px, 0, SIZE, 0, 0, SIZE, SIZE)
        val opaque = BooleanArray(n) { Color.alpha(px[it]) >= 128 }
        val opaqueCount = opaque.count { it }
        val out = IntArray(n) { Color.WHITE }
        if (opaqueCount == 0) return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)

        // 一番多い色（各色 16 段に丸めて数える）
        val counts = HashMap<Int, Int>()
        for (i in 0 until n) if (opaque[i]) {
            val c = px[i]
            val q = (Color.red(c) shr 4 shl 8) or (Color.green(c) shr 4 shl 4) or (Color.blue(c) shr 4)
            counts[q] = (counts[q] ?: 0) + 1
        }
        val mode = counts.maxBy { it.value }.key
        val dr = ((mode shr 8) and 15) * 16 + 8
        val dg = ((mode shr 4) and 15) * 16 + 8
        val db = (mode and 15) * 16 + 8
        fun far(c: Int): Boolean {
            val r = Color.red(c) - dr
            val g = Color.green(c) - dg
            val b = Color.blue(c) - db
            return r * r + g * g + b * b > 70 * 70
        }
        val shape = BooleanArray(n) { opaque[it] && far(px[it]) }
        if (shape.count { it } <= opaqueCount * 0.6) {
            for (i in 0 until n) if (shape[i]) out[i] = Color.BLACK
            return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
        }

        // 地の色がはっきりしないアイコン：不透明な部分を明るさで 2 段に分け、少ない方を形にする
        val lum = IntArray(n) { val c = px[it]; (Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000 }
        val t = otsu(lum.filterIndexed { i, _ -> opaque[i] }.toIntArray())
        val darkCount = (0 until n).count { opaque[it] && lum[it] < t }
        val darkIsShape = darkCount <= opaqueCount / 2
        for (i in 0 until n) if (opaque[i] && (lum[i] < t) == darkIsShape) out[i] = Color.BLACK
        return Bitmap.createBitmap(out, SIZE, SIZE, Bitmap.Config.ARGB_8888)
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

    /** Gemini の画像を 192px に縮め、黒と白の 2 色にそろえる。 */
    private fun binarize(img: Bitmap): Bitmap = localMask(img)

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

    /** 型を 2 色で塗る色フィルター：黒い部分を glyph、白い部分を plate に。明るさに対して色を線形に割り当てる。 */
    fun twoTone(glyph: androidx.compose.ui.graphics.Color, plate: androidx.compose.ui.graphics.Color): ColorFilter {
        fun row(g: Float, p: Float) = floatArrayOf((p - g) * 0.299f, (p - g) * 0.587f, (p - g) * 0.114f, 0f, g * 255f)
        val m = row(glyph.red, plate.red) + row(glyph.green, plate.green) + row(glyph.blue, plate.blue) + floatArrayOf(0f, 0f, 0f, 1f, 0f)
        return ColorFilter.colorMatrix(ColorMatrix(m))
    }
}
