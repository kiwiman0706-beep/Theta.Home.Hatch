package jp.yaegaki.theta.home.hatch

/** Theta Hatch シリーズの一覧。repo が null のものは配布先が未登録。packageName が null のものは Android 版がない。 */
data class HatchApp(
    val id: String,
    val short: String,
    val name: String,
    val summary: String,
    val packageName: String?,
    val repo: String?,
) {
    val releasesUrl: String? get() = repo?.let { "https://github.com/$OWNER/$it/releases" }

    companion object {
        const val OWNER = "kiwiman0706-beep"
    }
}

object HatchCatalog {
    val apps = listOf(
        HatchApp("travel", "TR", "TravelHatch", "旅の計画としおり、思い出", "jp.theta.travelhatch", "Theta.Travel.Hatch"),
        HatchApp("device", "DV", "機器台帳", "PC・電子機器の台帳とラベル", "jp.yaegaki.equipment", "Theta.Device.Hatch"),
        HatchApp("item", "IT", "おもいでクローゼット", "持ち物と思い出", "jp.omoide.closet", "Theta.Item.Hatch"),
        HatchApp("mensetsu", "MS", "Theta Mensetsu Hatch", "面接の管理", "jp.yaegaki.theta.mensetsu", "Theta.Mensetsu.Hatch"),
        HatchApp("label", "LB", "声でテプラ", "音声でテプラのラベル印刷", "jp.yaegaki.tepravoice", "Theta.Label.Hatch"),
        HatchApp("door", "DP", "おもいで年表", "玄関ドアの写真年表", "jp.yaegaki.omoide", "Theta.DoorPhoto.Hatch"),
        HatchApp("kamigata", "KG", "くるりヘア", "髪型を角度別の写真で残す", "jp.yaegaki.kururihair", "Theta.Kamigata.Hatch"),
        HatchApp("oekaki", "OE", "おえかきハッチ", "描いた絵がゲームで動く", "jp.theta.oekakihatch", "Theta.Oekaki.Hatch"),
        HatchApp("dsbooks", "DB", "DSBooks", "NAS の本棚", "jp.shinwakai.dsbooks.preview", null),
        HatchApp("meishi", "MC", "Theta Meishi Hatch", "名刺管理", "jp.yaegaki.meishiclaude", null),
        HatchApp("desk", "DH", "DeskHatch", "PC 用ランチャーバー（Windows・Mac）", null, "deskhatch"),
    )
}
