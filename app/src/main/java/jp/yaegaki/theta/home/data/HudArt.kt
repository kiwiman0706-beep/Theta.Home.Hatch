package jp.yaegaki.theta.home.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.res.ResourcesCompat
import jp.yaegaki.theta.home.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Theta Home Hatch の見た目を、ホームの外（One UI の壁紙・テーマ）へ持ち出すための絵を作る。
 * - 壁紙：黒地（ライトは紙色）に細い格子・目盛り・四隅のかぎ括弧・斜線の帯。One UI は壁紙から色を取るので、システムの色も黒とオレンジ寄りになる
 * - アイコン：3 色の型を、今の配色で塗った PNG（Theme Park でアプリごとに選べる）
 */
object HudArt {
    data class Colors(val bg: Int, val panel: Int, val line: Int, val faint: Int, val fg: Int, val accent: Int)

    val DARK = Colors(0xFF0A0A0B.toInt(), 0xFF111113.toInt(), 0xFF26262A.toInt(), 0xFF858585.toInt(), 0xFFEDEDED.toInt(), 0xFFFF6B1A.toInt())
    val LIGHT = Colors(0xFFF4F3EE.toInt(), 0xFFFFFFFF.toInt(), 0xFFE3E2DA.toInt(), 0xFF5E5E5E.toInt(), 0xFF121212.toInt(), 0xFFB8400C.toInt())

    /** w×h（density は端末の 1dp の画素数）の壁紙。上は時計・通知のために空けておく（ロック画面と重ならないよう）。 */
    fun wallpaper(ctx: Context, w: Int, h: Int, dark: Boolean, density: Float = ctx.resources.displayMetrics.density): Bitmap {
        val c = if (dark) DARK else LIGHT
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val u = density // 1dp 相当
        cv.drawColor(c.bg)
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.line; strokeWidth = u * 0.6f }
        // 細い格子（24dp）
        var x = 0f
        while (x < w) { cv.drawLine(x, 0f, x, h.toFloat(), line); x += 24 * u }
        var y = 0f
        while (y < h) { cv.drawLine(0f, y, w.toFloat(), y, line); y += 24 * u }
        // 下の区画：パネル＋四隅のかぎ括弧
        val m = 20 * u
        // 下に高さ 150dp の区画（広い画面では幅を 420dp までにして左に寄せる）
        val boxW = minOf(w - 2 * m, 420 * u)
        val box = RectF(m, h - m * 2.5f - 150 * u, m + boxW, h - m * 2.5f)
        cv.drawRect(box, Paint().apply { color = c.panel })
        cv.drawRect(box, Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = c.line; strokeWidth = u })
        val acc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.accent; strokeWidth = 1.6f * u; style = Paint.Style.STROKE }
        val k = 14 * u
        listOf(box.left to box.top, box.right to box.top, box.left to box.bottom, box.right to box.bottom).forEachIndexed { i, (bx, by) ->
            val sx = if (i % 2 == 0) 1 else -1
            val sy = if (i < 2) 1 else -1
            cv.drawLine(bx, by, bx + sx * k, by, acc)
            cv.drawLine(bx, by, bx, by + sy * k, acc)
        }
        // 目盛り（区画の上）
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.faint; strokeWidth = u * 0.8f }
        var tx = box.left
        var n = 0
        while (tx <= box.right) {
            val len = if (n % 5 == 0) 8 * u else 4 * u
            cv.drawLine(tx, box.top - 6 * u - len, tx, box.top - 6 * u, tick)
            tx += 6 * u; n++
        }
        // 斜線の帯（区画の下）
        val stripe = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.accent; strokeWidth = 2.4f * u }
        val sy0 = box.bottom + 10 * u
        val sh = 8 * u
        cv.save()
        cv.clipRect(box.left, sy0, box.left + (box.right - box.left) * 0.45f, sy0 + sh)
        var s = box.left - sh
        while (s < box.right) { cv.drawLine(s, sy0 + sh, s + sh, sy0, stripe); s += 7 * u }
        cv.restore()
        // ラベル
        val mono = font(ctx, R.font.share_tech_mono)
        val oswald = font(ctx, R.font.oswald_light)
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.faint; typeface = mono; textSize = 10 * u; letterSpacing = 0.2f }
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = c.fg; typeface = oswald; textSize = 44 * u; letterSpacing = 0.04f }
        val pad = 14 * u
        cv.drawRect(box.left + pad, box.top + pad + 2 * u, box.left + pad + 6 * u, box.top + pad + 8 * u, Paint().apply { color = c.accent })
        cv.drawText("SYS.HOME // THETA HATCH", box.left + pad + 12 * u, box.top + pad + 8 * u, label)
        cv.drawText("HOME.HATCH", box.left + pad, box.top + pad + 58 * u, big)
        val code = Paint(label).apply { color = c.accent }
        cv.drawText("[ READY ]", box.left + pad, box.bottom - pad, code)
        val stamp = "BUILD " + SimpleDateFormat("yyyy.MM.dd", Locale.US).format(Date())
        cv.drawText(stamp, box.right - pad - label.measureText(stamp), box.bottom - pad, label)
        // 上の端に細いオレンジの線（ステータスバーの下）
        cv.drawRect(m, 44 * u, m + 28 * u, 45.5f * u, Paint().apply { color = c.accent })
        cv.drawText("SYS.TIME", m + 34 * u, 48 * u, label)
        return bmp
    }

    private fun font(ctx: Context, id: Int): Typeface = runCatching { ResourcesCompat.getFont(ctx, id) }.getOrNull() ?: Typeface.MONOSPACE

    /** 3 色の型を塗った PNG 用の絵。plate・base・accent は ARGB。 */
    fun paintMask(mask: Bitmap, size: Int, plate: Int, base: Int, accent: Int): Bitmap {
        fun ch(v: Int, shift: Int) = ((v shr shift) and 0xFF) / 255f
        fun row(shift: Int) = floatArrayOf(ch(base, shift) - ch(plate, shift), ch(accent, shift) - ch(plate, shift), 0f, 0f, ch(plate, shift) * 255f)
        val m = ColorMatrix(row(16) + row(8) + row(0) + floatArrayOf(0f, 0f, 0f, 1f, 0f))
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(m) }
        Canvas(out).drawBitmap(Bitmap.createScaledBitmap(mask, size, size, true), 0f, 0f, p)
        return out
    }

    /** 画像を「写真」の Pictures/ThetaHomeHatch/<folder> に PNG で保存する。 */
    fun savePng(ctx: Context, bmp: Bitmap, folder: String, name: String): Uri? {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name.replace(Regex("[\\\\/:*?\"<>|]"), "_") + ".png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ThetaHomeHatch/$folder")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return null
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uri
    }

}
