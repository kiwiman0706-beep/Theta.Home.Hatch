package jp.yaegaki.theta.home.data

import android.content.pm.ApplicationInfo

/** 仕分けの最初に選べるカテゴリーの候補と、アプリの申告からのおすすめ。 */
object Categories {
    const val WORK = "仕事"
    const val TALK = "連絡"
    const val PHOTO = "写真・カメラ"
    const val MEDIA = "音楽・動画"
    const val NEWS = "ニュース・読む"
    const val MAP = "地図・移動"
    const val MONEY = "お金・買い物"
    const val TOOL = "ツール"
    const val HATCH = "Hatch"
    const val GAME = "ゲーム"
    const val SYSTEM = "設定・端末"

    val presets = listOf(WORK, TALK, PHOTO, MEDIA, NEWS, MAP, MONEY, TOOL, HATCH, GAME, SYSTEM)

    /** はじめから選んでおく候補。 */
    val defaults = listOf(WORK, TALK, PHOTO, MEDIA, MAP, TOOL, HATCH, SYSTEM)

    private val hatchPackages = setOf(
        "jp.theta.travelhatch", "jp.yaegaki.equipment", "jp.omoide.closet", "jp.yaegaki.theta.mensetsu",
        "jp.yaegaki.tepravoice", "jp.yaegaki.omoide", "jp.yaegaki.kururihair", "jp.theta.oekakihatch",
        "jp.shinwakai.dsbooks.preview", "jp.yaegaki.meishiclaude",
    )

    /** おすすめのカテゴリー名。分からなければ null。 */
    fun suggest(app: AppEntry): String? {
        val pkg = app.packageName
        return when {
            pkg in hatchPackages -> HATCH
            pkg == "jp.naver.line.android" || pkg.contains("mail") || pkg.contains(".gm") || pkg.contains("messag") || pkg.contains("dialer") || pkg.contains("contacts") -> TALK
            pkg.contains("camera") || pkg.contains("photos") || pkg.contains("gallery") -> PHOTO
            pkg.contains("settings") || pkg.startsWith("com.samsung.android.app.") && pkg.contains("care") -> SYSTEM
            else -> when (app.systemCategory) {
                ApplicationInfo.CATEGORY_GAME -> GAME
                ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> MEDIA
                ApplicationInfo.CATEGORY_IMAGE -> PHOTO
                ApplicationInfo.CATEGORY_SOCIAL -> TALK
                ApplicationInfo.CATEGORY_NEWS -> NEWS
                ApplicationInfo.CATEGORY_MAPS -> MAP
                ApplicationInfo.CATEGORY_PRODUCTIVITY -> WORK
                ApplicationInfo.CATEGORY_ACCESSIBILITY -> SYSTEM
                else -> null
            }
        }
    }
}
