package jp.yaegaki.theta.home.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 録音・撮影・手書き・テキストの保存先（アプリ専用領域）。外へは「書き出す」でコピーする。 */
enum class CaptureKind(val dir: String, val label: String) {
    RECORDING("Recordings", "録音"),
    CAMERA("Camera", "撮影"),
    INK("Handwriting", "手書き"),
    TEXT("Text", "テキスト"),
}

object Capture {
    fun root(context: Context): File = File(context.filesDir, "Capture").apply { mkdirs() }

    fun dir(context: Context, kind: CaptureKind): File = File(root(context), kind.dir).apply { mkdirs() }

    fun newFile(context: Context, kind: CaptureKind, ext: String, title: String? = null): File {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = title?.trim()?.replace(Regex("[\\\\/:*?\"<>|\\n]"), "_")?.take(40)?.ifEmpty { null }
        return File(dir(context, kind), if (safe == null) "$stamp$ext" else "${stamp}_$safe$ext")
    }

    /** すべての記録を新しい順に。 */
    fun all(context: Context): List<Pair<CaptureKind, File>> =
        CaptureKind.entries.flatMap { k -> (dir(context, k).listFiles() ?: emptyArray()).filter { it.isFile && it.length() > 0 }.map { k to it } }
            .sortedByDescending { it.second.lastModified() }
}

/** 手書きの 1 本の線。点ごとに筆圧を持つ。 */
class Stroke(val points: MutableList<FloatArray> = mutableListOf())

object Ink {
    const val BASE_WIDTH = 2f
    const val PRESSURE_WIDTH = 6f

    fun widthAt(pressure: Float, density: Float): Float = (BASE_WIDTH + pressure.coerceIn(0.05f, 1f) * PRESSURE_WIDTH) * density / 2f

    fun render(strokes: List<Stroke>, width: Int, height: Int, density: Float, ink: Int, paper: Int): Bitmap {
        val bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(paper)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        for (s in strokes) {
            if (s.points.size == 1) {
                val pt = s.points[0]
                c.drawCircle(pt[0], pt[1], widthAt(pt[2], density) / 2f, p)
            }
            for (i in 1 until s.points.size) {
                val a = s.points[i - 1]
                val b = s.points[i]
                p.strokeWidth = widthAt(b[2], density)
                c.drawLine(a[0], a[1], b[0], b[1], p)
            }
        }
        return bmp
    }
}
