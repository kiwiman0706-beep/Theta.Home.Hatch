package jp.yaegaki.theta.home.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.exifinterface.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache
import android.util.Size
import jp.yaegaki.theta.home.drive.GDrive
import jp.yaegaki.theta.home.nas.Nas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.Collections

/**
 * ファイラーのサムネイル。写真・動画・PDF。端末のファイルは提供元のサムネイル → 自前で縮小、NAS は DSM のサムネイル。
 * 作ったものはメモリに置き、作れなかったものは覚えておいて二度と試さない。
 */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val failed: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val nasGate = Semaphore(3)
    private val localGate = Semaphore(4)

    fun kind(name: String, mime: String?): String? {
        val m = mime ?: Files.mimeOf(name) ?: return null
        return when {
            m.startsWith("image/") -> "image"
            m.startsWith("video/") -> "video"
            m == "application/pdf" -> "pdf"
            else -> null
        }
    }

    fun cached(key: String, px: Int): Bitmap? = cache.get(cacheKey(key, px))

    private fun cacheKey(key: String, px: Int) = key + "@" + bucket(px)

    /** 大きさを数段にまとめ、サイズを少し変えただけで作り直さない。 */
    private fun bucket(px: Int) = when {
        px <= 160 -> 160
        px <= 320 -> 320
        else -> 512
    }

    suspend fun local(context: Context, uri: Uri, name: String, mime: String?, px: Int): Bitmap? {
        val kind = kind(name, mime) ?: return null
        val b = bucket(px)
        val key = cacheKey(uri.toString(), px)
        cache.get(key)?.let { return it }
        if (key in failed) return null
        return localGate.withPermit {
            withContext(Dispatchers.IO) {
                val bmp = runCatching { context.contentResolver.loadThumbnail(uri, Size(b, b), null) }.getOrNull()
                    ?: runCatching {
                        when (kind) {
                            "image" -> decodeImage(context, uri, b)
                            "video" -> MediaMetadataRetriever().run {
                                try { setDataSource(context, uri); getScaledFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, b, b) } finally { release() }
                            }
                            else -> renderPdf(context, uri, b)
                        }
                    }.getOrNull()
                remember(key, bmp)
            }
        }
    }

    suspend fun nas(nas: Nas, item: Nas.Item, px: Int): Bitmap? {
        val kind = kind(item.name, null)
        if (kind != "image" && kind != "video") return null
        val b = bucket(px)
        val key = cacheKey("nas:" + item.path + ":" + item.modified, px)
        cache.get(key)?.let { return it }
        if (key in failed) return null
        return nasGate.withPermit {
            withContext(Dispatchers.IO) {
                val bytes = runCatching { nas.thumb(item.path, b > 160) }.getOrNull()
                remember(key, bytes?.let { decodeBytes(it, b) })
            }
        }
    }

    suspend fun drive(drive: GDrive, item: GDrive.Item, px: Int): Bitmap? {
        if (item.thumb == null) return null
        val b = bucket(px)
        val key = cacheKey("drive:" + item.id + ":" + item.modified, px)
        cache.get(key)?.let { return it }
        if (key in failed) return null
        return nasGate.withPermit {
            val t = runCatching { drive.token() }.getOrNull() ?: return@withPermit null
            withContext(Dispatchers.IO) {
                remember(key, drive.thumb(t, item, b)?.let { decodeBytes(it, b) })
            }
        }
    }

    private fun remember(key: String, bmp: Bitmap?): Bitmap? {
        if (bmp == null) failed += key else cache.put(key, bmp)
        return bmp
    }

    private fun sample(w: Int, h: Int, px: Int): Int {
        var s = 1
        while (minOf(w, h) / (s * 2) >= px) s *= 2
        return s
    }

    private fun decodeImage(context: Context, uri: Uri, px: Int): Bitmap? {
        val cr = context.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample(o.outWidth, o.outHeight, px) })
        } ?: return null
        val deg = runCatching {
            cr.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees }
        }.getOrNull() ?: 0
        return if (deg == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
    }

    private fun decodeBytes(bytes: ByteArray, px: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        if (o.outWidth <= 0) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample(o.outWidth, o.outHeight, px) })
    }

    private fun renderPdf(context: Context, uri: Uri, px: Int): Bitmap? =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { page ->
                    val scale = px.toFloat() / maxOf(page.width, page.height)
                    val bmp = Bitmap.createBitmap(maxOf(1, (page.width * scale).toInt()), maxOf(1, (page.height * scale).toInt()), Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
}
