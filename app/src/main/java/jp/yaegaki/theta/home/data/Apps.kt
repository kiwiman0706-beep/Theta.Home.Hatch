package jp.yaegaki.theta.home.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.provider.Settings
import java.text.Collator
import java.util.Locale

/** systemCategory はアプリ自身が申告している種類（ApplicationInfo.CATEGORY_*、無ければ -1）。仕分けのおすすめに使う。 */
data class AppEntry(val component: ComponentName, val label: String, val systemCategory: Int = -1) {
    val key: String get() = component.flattenToString()
    val packageName: String get() = component.packageName
}

object Apps {
    fun load(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val collator = Collator.getInstance(Locale.JAPANESE)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadLabel(pm).toString(),
                    it.activityInfo.applicationInfo?.category ?: -1,
                )
            }
            .distinctBy { it.key }
            .sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun launchIntent(app: AppEntry): Intent =
        Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(app.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)

    /** Android のアンインストール確認画面。 */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))

    /** 端末に最初から入っているアプリ（アンインストールできず、無効化だけできる）。 */
    fun isSystem(context: Context, packageName: String): Boolean = runCatching {
        val flags = context.packageManager.getApplicationInfo(packageName, 0).flags
        flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0 && flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
    }.getOrDefault(false)

    fun infoIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    fun icon(context: Context, app: AppEntry, sizePx: Int): Bitmap? = runCatching {
        val d = context.packageManager.getActivityIcon(app.component)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(Canvas(bmp))
        bmp
    }.getOrNull()

    fun isInstalled(context: Context, packageName: String): Boolean = versionName(context, packageName) != null

    fun versionName(context: Context, packageName: String): String? = try {
        context.packageManager.getPackageInfo(packageName, 0).versionName ?: ""
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** A–Z の見出し。日本語名はひらがな・カタカナ・漢字の区分で見出しを付ける。 */
    fun heading(label: String): String {
        val c = label.trim().firstOrNull() ?: return "#"
        return when {
            c in 'a'..'z' || c in 'A'..'Z' -> c.uppercaseChar().toString()
            c in 'ぁ'..'ゖ' || c in 'ァ'..'ヺ' -> "あ"
            Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN -> "漢"
            else -> "#"
        }
    }
}
