package app.thetahatch.home.hub

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri

/**
 * Hatch のアプリの「棚」：そのアプリが Home に見せたいもの（MemoHatch のピン留めメモ、Music のお気に入り・最近のアルバムなど）。
 * 各アプリの設定のプロバイダ（authority <パッケージ>.theta、権限 app.thetahatch.permission.SHARE）の /shelf を読む。
 *
 * 列：section（"pinned"・"favorites"・"recent" など）、sectionTitle、id、title、subtitle、text（メモの本文など）、
 * image（画像の content:// URI。無ければ空）、action（押したときの Intent の URI。Intent.toUri(URI_INTENT_SCHEME)）、color（"#RRGGBB"。無ければ空）。
 * 決まりの正本は Theta.Hatch.Series の spec（Home から依頼中）。
 */
object HatchShelf {
    data class Item(
        val section: String, val sectionTitle: String, val id: String, val title: String, val subtitle: String,
        val text: String, val image: String, val action: String, val color: String,
    )

    fun uri(pkg: String): Uri = Uri.parse("content://$pkg.theta/shelf")

    /** 読めなければ null（そのアプリが棚に対応していない・権限が無い）。空のリストは「対応しているが何も無い」。 */
    fun read(context: Context, pkg: String): List<Item>? = runCatching {
        context.contentResolver.query(uri(pkg), null, null, null, null)?.use { c ->
            fun col(name: String, i: Int) = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getString(it) }.orEmpty()
            val out = ArrayList<Item>()
            while (c.moveToNext()) {
                out += Item(
                    col("section", 0), col("sectionTitle", 0), col("id", 0), col("title", 0), col("subtitle", 0),
                    col("text", 0), col("image", 0), col("action", 0), col("color", 0),
                )
            }
            out
        }
    }.getOrNull()

    /**
     * MemoHatch の全部のメモ（/notes、新しい順・ピン留めが先、500 件まで）を棚と同じ形で。検索に使う。
     * 押すとそのメモを開く（MemoHatch の起動の画面に「open」でメモの id を渡す。棚の action と同じ）。
     */
    fun memoNotes(context: Context, pkg: String): List<Item>? = runCatching {
        val main = context.packageManager.getLaunchIntentForPackage(pkg)?.component ?: return@runCatching null
        context.contentResolver.query(Uri.parse("content://$pkg.theta/notes?limit=500"), null, null, null, null)?.use { c ->
            fun col(name: String) = c.getColumnIndex(name).takeIf { it >= 0 }?.let { c.getString(it) }.orEmpty()
            val out = ArrayList<Item>()
            while (c.moveToNext()) {
                val id = col("id")
                val cover = col("cover")
                val image = if (cover.matches(Regex("(?i).*\\.(jpe?g|png|gif|webp|heic|heif)$"))) "content://$pkg.theta/image/$cover" else ""
                val action = Intent(Intent.ACTION_VIEW, Uri.parse("memo-note://$id")).setComponent(main).putExtra("open", id)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).toUri(Intent.URI_INTENT_SCHEME)
                out += Item("search", "", id, col("title").ifEmpty { "メモ" }, "", col("snippet"), image, action, col("color"))
            }
            out
        }
    }.getOrNull()

    /** 言葉に合うメモ（題名・本文の頭。空白で区切った言葉が全部入っているもの）。 */
    fun filter(list: List<Item>, q: String): List<Item> {
        val words = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.lowercase() }
        if (words.isEmpty()) return list
        return list.filter { i -> val t = (i.title + "\n" + i.text).lowercase(); words.all { t.contains(it) } }
    }

    fun image(context: Context, item: Item, max: Int = 256): Bitmap? = item.image.takeIf { it.isNotEmpty() }?.let { u ->
        runCatching {
            context.contentResolver.openInputStream(Uri.parse(u))?.use { BitmapFactory.decodeStream(it) }?.let { b ->
                val s = max.toFloat() / maxOf(b.width, b.height)
                if (s >= 1f) b else Bitmap.createScaledBitmap(b, (b.width * s).toInt(), (b.height * s).toInt(), true)
            }
        }.getOrNull()
    }

    /** 押したときの Intent。そのアプリ宛てに限る（ほかのアプリを勝手に開かせない）。 */
    fun intent(pkg: String, item: Item): Intent? = runCatching {
        require(item.action.startsWith("intent:")) { "Intent の URI ではありません" }
        Intent.parseUri(item.action, Intent.URI_INTENT_SCHEME).apply {
            if (component?.packageName != null && component?.packageName != pkg) component = null
            selector = null
            setPackage(pkg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }.getOrNull()
}
