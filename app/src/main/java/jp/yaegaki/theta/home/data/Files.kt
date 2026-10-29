package jp.yaegaki.theta.home.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

data class Entry(
    val uri: Uri,
    val name: String,
    val mime: String?,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
)

/** Storage Access Framework で許可されたフォルダ（ツリー）を読む。 */
object Files {
    /** 選んだツリー URI の最上位ドキュメント。 */
    fun rootOf(tree: Uri): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))

    fun list(context: Context, dir: Uri): List<Entry> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(dir, DocumentsContract.getDocumentId(dir))
        val cols = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val out = ArrayList<Entry>()
        context.contentResolver.query(children, cols, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                val mime = c.getString(2)
                out += Entry(
                    uri = DocumentsContract.buildDocumentUriUsingTree(dir, c.getString(0)),
                    name = name,
                    mime = mime,
                    isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (c.isNull(3)) 0 else c.getLong(3),
                    modified = if (c.isNull(4)) 0 else c.getLong(4),
                )
            }
        }
        return out.sortedWith(compareBy<Entry>({ !it.isDir }, { it.name.lowercase(Locale.ROOT) }))
    }

    /** アプリ専用フォルダ（記録・Clip）を Entry として読む。新しい順。 */
    fun listLocal(context: Context, dir: File): List<Entry> =
        (dir.listFiles() ?: emptyArray())
            .filter { it.isFile && !it.name.startsWith(".") }
            .sortedByDescending { it.lastModified() }
            .map { f ->
                Entry(
                    uri = localUri(context, f),
                    name = f.name,
                    mime = mimeOf(f.name),
                    isDir = false,
                    size = f.length(),
                    modified = f.lastModified(),
                )
            }

    fun localUri(context: Context, f: File): Uri =
        androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".files", f)

    fun mimeOf(name: String): String? {
        val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (ext == "md") return "text/markdown"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
    }

    /** 一覧に出す短い種類名（PDF・JPG など）。 */
    fun badge(e: Entry): String = when {
        e.isDir -> "DIR"
        else -> e.name.substringAfterLast('.', "").uppercase(Locale.ROOT).take(4).ifEmpty { "FILE" }
    }

    fun sizeText(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toFloat())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toFloat())
        bytes >= 1024 -> "${bytes / 1024} KB"
        else -> "$bytes B"
    }

    fun openIntent(uri: Uri, mime: String?): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime ?: "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    fun shareIntent(uri: Uri, mime: String?, name: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime ?: "*/*")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(name, uri)
        return Intent.createChooser(send, "共有")
    }

    /** 保存しているツリー許可がまだ有効か。 */
    // ---- 端末のフォルダをパスで読む（Download など、フォルダを選ぶ画面では選べないところ） ----

    /** パスで読むフォルダ（file://）か。 */
    fun isPath(uri: Uri) = uri.scheme == "file"

    /** 端末の Download フォルダ。 */
    fun downloadsUri(): Uri = Uri.fromFile(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS))

    /** パスで読む許可（「すべてのファイルへのアクセス」）があるか。 */
    fun canReadAll(): Boolean = allFilesForTest ?: (android.os.Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager())

    /** 試験で、許可のあり・なしを決める。 */
    @androidx.annotation.VisibleForTesting
    var allFilesForTest: Boolean? = null

    /** パスのフォルダの中身。フォルダが先、ファイルは新しい順。フォルダは file://、ファイルはほかのアプリに渡せる URI。 */
    fun listPath(context: Context, dir: File): List<Entry> {
        val all = dir.listFiles() ?: throw java.io.IOException("読めません")
        return all.filter { !it.name.startsWith(".") }.map { f ->
            if (f.isDirectory) Entry(Uri.fromFile(f), f.name, null, true, 0, f.lastModified())
            else Entry(runCatching { localUri(context, f) }.getOrElse { Uri.fromFile(f) }, f.name, mimeOf(f.name), false, f.length(), f.lastModified())
        }.sortedWith(compareBy<Entry>({ !it.isDir }, { if (it.isDir) 0L else -it.modified }, { it.name.lowercase(Locale.ROOT) }))
    }

    fun hasGrant(context: Context, tree: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
}
