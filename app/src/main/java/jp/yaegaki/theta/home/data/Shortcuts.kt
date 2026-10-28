package jp.yaegaki.theta.home.data

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Process
import java.io.File

/**
 * タブに置いたアプリのショートカット（LINE のトークルームなど）。catId のカテゴリーのタブに、アプリと並べて出す。
 * アイコンは置いたときに保存しておく（あとで問い合わせられなくても出せるように）。
 */
data class PinnedShortcut(
    val id: String,
    val packageName: String,
    val shortcutId: String,
    val label: String,
    val catId: String,
)

/**
 * アプリのショートカット（Android の ShortcutManager）。読む・開く・固定するには、このアプリが既定のホームであることが要る。
 * アプリからの「ホーム画面に追加」は [jp.yaegaki.theta.home.PinShortcutActivity] で受ける。
 */
object Shortcuts {
    private fun la(ctx: Context) = ctx.getSystemService(LauncherApps::class.java)

    /** ショートカットを扱えるか（既定のホームのときだけ true）。 */
    fun available(ctx: Context): Boolean = runCatching { la(ctx).hasShortcutHostPermission() }.getOrDefault(false)

    /** アプリの持っているショートカット（静的・動的・固定済み）。 */
    fun forApp(ctx: Context, pkg: String): List<ShortcutInfo> = runCatching {
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(
            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
        )
        la(ctx).getShortcuts(q, Process.myUserHandle()).orEmpty().filter { it.isEnabled }.sortedBy { it.rank }
    }.getOrDefault(emptyList())

    /**
     * 共有の宛先に使えるショートカット（LINE のトークルームなど）。最近の会話（動的・保存済み）と、置いたもの。
     * 送るときは共有の Intent に EXTRA_SHORTCUT_ID を付ける（共有メニューの「直接共有」と同じ）。
     */
    fun shareTargets(ctx: Context, pkg: String): List<ShortcutInfo> = runCatching {
        var flags = LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
        if (android.os.Build.VERSION.SDK_INT >= 30) flags = flags or LauncherApps.ShortcutQuery.FLAG_MATCH_CACHED
        val q = LauncherApps.ShortcutQuery().setPackage(pkg).setQueryFlags(flags)
        la(ctx).getShortcuts(q, Process.myUserHandle()).orEmpty().filter { it.isEnabled }.distinctBy { it.id }.sortedBy { it.rank }
    }.getOrDefault(emptyList())

    fun label(s: ShortcutInfo): String = (s.shortLabel ?: s.longLabel ?: s.id).toString()

    fun start(ctx: Context, s: PinnedShortcut): Boolean = runCatching {
        la(ctx).startShortcut(s.packageName, s.shortcutId, null, null, Process.myUserHandle())
        true
    }.getOrDefault(false)

    fun start(ctx: Context, s: ShortcutInfo): Boolean = runCatching {
        la(ctx).startShortcut(s, null, null)
        true
    }.getOrDefault(false)

    /** このアプリが固定しているショートカットを ids にする（外したものは固定をやめる）。 */
    fun setPinned(ctx: Context, pkg: String, ids: List<String>) {
        runCatching { la(ctx).pinShortcuts(pkg, ids, Process.myUserHandle()) }
    }

    fun iconFile(ctx: Context, id: String) = File(File(ctx.filesDir, "shortcuts").apply { mkdirs() }, "$id.png")

    /** アイコンを PNG にして保存する。 */
    fun saveIcon(ctx: Context, s: ShortcutInfo, id: String) {
        runCatching {
            val d = la(ctx).getShortcutIconDrawable(s, ctx.resources.displayMetrics.densityDpi) ?: return
            val size = 192
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, size, size)
            d.draw(Canvas(bmp))
            iconFile(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    fun icon(ctx: Context, id: String): Bitmap? = iconFile(ctx, id).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
}
