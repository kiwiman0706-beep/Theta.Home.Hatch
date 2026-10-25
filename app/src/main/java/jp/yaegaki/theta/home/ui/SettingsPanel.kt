package jp.yaegaki.theta.home.ui

import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import jp.yaegaki.theta.home.data.IconStyle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.BuildConfigInfo
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.TabKind
import jp.yaegaki.theta.home.data.TabSpec
import jp.yaegaki.theta.home.notify.HatchNotificationListener

@Composable
fun SettingsPanel(model: HomeModel) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var renaming by remember { mutableStateOf<TabSpec?>(null) }
    var adding by remember { mutableStateOf(false) }
    var nasDialog by remember { mutableStateOf(false) }
    var addingWidget by remember { mutableStateOf(false) }
    var driveDialog by remember { mutableStateOf(false) }
    var sorting by remember { mutableStateOf(false) }
    var renamingCategory by remember { mutableStateOf<jp.yaegaki.theta.home.data.Category?>(null) }
    var renamingPreset by remember { mutableStateOf<String?>(null) }
    var importConfirm by remember { mutableStateOf(false) }
    var geminiDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("設定", color = p.fg, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            GlyphButton(Glyph.CLOSE, "閉じる", { model.settingsOpen = false })
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(p.line))
        DrawerList {
            item {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Readout("HOME", if (model.isDefaultHome) "既定のホーム" else "未設定", accent = !model.isDefaultHome)
                    Readout("VERSION", model.versionName)
                }
                FlowRow2 {
                    PillButton(if (model.isDefaultHome) "既定のホーム：設定済み" else "既定のホームにする", { requestHomeRole(actions) }, primary = !model.isDefaultHome)
                    PillButton("機能を頼む（開発の会話を開く）↗", { openRequest(actions) })
                    PillButton(
                        if (HatchNotificationListener.isEnabled(actions.activity)) "通知へのアクセス：許可済み" else "通知へのアクセス（LINE・メール）",
                        { actions.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    )
                }
                Gap(18.dp)
                SectionTitle("UPDATE / 更新", Modifier.padding(horizontal = 10.dp))
                val u = model.update
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    Readout("NOW", model.versionName)
                    Readout("LATEST", u.latest?.version ?: if (u.checking) "確認中…" else "----", accent = u.newer)
                    if (u.progress != null) Readout("DOWNLOAD", "%d%%".format((u.progress * 100).toInt()), accent = true)
                }
                u.error?.let { Note("確認・取得できませんでした：$it") }
                FlowRow2 {
                    if (u.newer) PillButton("v${u.latest?.version} に更新", { installUpdate(model, actions) }, primary = true)
                    PillButton("今すぐ確認", { model.checkUpdate(force = true) })
                    PillButton("更新履歴（Releases）↗", { actions.start(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(jp.yaegaki.theta.home.hatch.SelfUpdate.RELEASES_URL))) })
                }
                Toggle("起動・復帰のときに新しい版を確かめる（1 日 1 回）", model.autoUpdate) { model.changeAutoUpdate(it) }
                Note("新しい版があると、画面上部に「UPDATE」が出ます。押すと取得して SHA-256 を照合し、Android の確認画面へ進みます（2 回目からは確認を省けることがあります）。")
                Gap(18.dp)
                SectionTitle("RESCUE / もしものとき", Modifier.padding(horizontal = 10.dp))
                FlowRow2 {
                    PillButton("別のホームアプリを選ぶ", { jp.yaegaki.theta.home.rescue.Rescue.chooseHome(actions.activity) })
                    PillButton("レスキュー画面を開く", { actions.start(Intent(actions.activity, jp.yaegaki.theta.home.rescue.RescueActivity::class.java)) })
                    if (android.os.Build.VERSION.SDK_INT >= 33) PillButton("クイック設定にタイルを足す", { addRescueTile(actions) })
                }
                Note("ホームが固まったら、通知パネルのクイック設定「ホームのレスキュー」から別のホームを選べます。起動してすぐ止まるのが続いたときは、自動でレスキュー画面になります。")
                Gap(18.dp)
                SectionTitle("NAS・GOOGLE DRIVE", Modifier.padding(horizontal = 10.dp))
                FlowRow2 {
                    PillButton(if (model.nas.configured()) "NAS の接続先：${model.nas.base()}" else "NAS を登録", { nasDialog = true })
                    PillButton(if (model.driveOn) "Google ドライブ：${model.driveEmail.ifEmpty { "接続中" }}" else "Google ドライブにつなぐ", { driveDialog = true }, primary = !model.driveOn)
                }
                Gap(18.dp)
                SectionTitle("アプリのカテゴリー（押すと名前・並び・削除）", Modifier.padding(horizontal = 10.dp))
                model.categories.forEachIndexed { i, c ->
                    var menu by remember(c.id) { mutableStateOf(false) }
                    Box {
                        RowButton(zebra = i, onClick = { menu = true }) {
                            Text("%02d".format(i + 1), color = p.faint, fontSize = 12.sp)
                            Text(c.name, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text("%d 個".format(model.appCategories.count { it.value == c.id }), color = p.muted, fontSize = 12.sp)
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("名前を変える") }, onClick = { menu = false; renamingCategory = c })
                            if (i > 0) DropdownMenuItem(text = { Text("上へ") }, onClick = { menu = false; model.moveCategory(c, -1) })
                            if (i < model.categories.lastIndex) DropdownMenuItem(text = { Text("下へ") }, onClick = { menu = false; model.moveCategory(c, 1) })
                            DropdownMenuItem(text = { Text("消す（中のアプリは未分類に）") }, onClick = { menu = false; model.deleteCategory(c) })
                        }
                    }
                }
                FlowRow2 { PillButton("仕分けガイドを開く", { sorting = true }, primary = model.categories.isEmpty()) }
                Gap(18.dp)
                SectionTitle("仕分けの候補（規定のカテゴリー）", Modifier.padding(horizontal = 10.dp))
                Note("仕分けガイドの最初に並ぶ候補。● は初めから選んでおくもの。押すと名前の変更・削除など。")
                model.categoryPresets.forEachIndexed { i, name ->
                    var menu by remember(name) { mutableStateOf(false) }
                    Box {
                        RowButton(zebra = i, onClick = { menu = true }) {
                            Text(if (name in model.categoryDefaults) "●" else "○", color = if (name in model.categoryDefaults) p.accent else p.faint, fontSize = 12.sp)
                            Text(name, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("名前を変える") }, onClick = { menu = false; renamingPreset = name })
                            DropdownMenuItem(text = { Text(if (name in model.categoryDefaults) "初めから選ばない" else "初めから選んでおく") }, onClick = { menu = false; model.toggleDefault(name) })
                            if (i > 0) DropdownMenuItem(text = { Text("上へ") }, onClick = { menu = false; model.movePreset(name, -1) })
                            if (i < model.categoryPresets.lastIndex) DropdownMenuItem(text = { Text("下へ") }, onClick = { menu = false; model.movePreset(name, 1) })
                            DropdownMenuItem(text = { Text("候補から消す") }, onClick = { menu = false; model.deletePreset(name) })
                        }
                    }
                }
                FlowRow2 {
                    PillButton("＋ 候補を追加", { renamingPreset = "" })
                    PillButton("初期の候補に戻す", { model.resetPresets() })
                }
                Gap(18.dp)
                SectionTitle("BACKUP / 設定の書き出し・読み込み", Modifier.padding(horizontal = 10.dp))
                Note("タブ・テーマ・カテゴリーと仕分け・候補・栞・ウィジェットの並び・NAS の接続先（パスワードを除く）・アプリの一覧を 1 つのファイルに。新しいスマホで読み込むと、入っていないアプリを一覧にして Play ストアへ案内します。")
                FlowRow2 {
                    PillButton("設定を書き出す", { actions.createDocument(jp.yaegaki.theta.home.data.Backup.fileName(), "application/json") { model.exportSettings(it) } }, primary = true)
                    PillButton("設定を読み込む", { importConfirm = true })
                }
                Gap(18.dp)
                SectionTitle("見た目", Modifier.padding(horizontal = 10.dp))
                Toggle("タブを左端に置く", model.railLeft) { model.changeRailLeft(it) }
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("THEME", color = p.faint, fontSize = 11.sp, letterSpacing = 1.5.sp, modifier = Modifier.width(56.dp))
                    listOf("dark" to "ダーク", "light" to "ライト", "pop" to "POP（カラフル）", "system" to "端末に合わせる").forEach { (mode, label) ->
                        PillButton(label, { model.changeTheme(mode) }, primary = model.themeMode == mode)
                    }
                }
                Gap(18.dp)
                SectionTitle("VOICE / 声でアプリを開く", Modifier.padding(horizontal = 10.dp))
                FlowRow2 {
                    listOf("off" to "使わない", "tap" to "START を押したとき", "long" to "START を長押ししたとき").forEach { (k, label) ->
                        PillButton(label, { model.changeVoiceApps(k) }, primary = model.voiceApps == k)
                    }
                }
                Note("START（または検索の横のアプリのボタン）でアプリの一覧を開くと同時に聞き取り、「LINE」「カメラ開いて」のように言うとそのアプリを開きます。決まらないときは、聞き取った言葉で一覧を絞り込みます。一覧の中のマイクのボタンからも使えます。")
                Gap(18.dp)
                SectionTitle("EDGE / ほかのアプリの上で、端からタブ", Modifier.padding(horizontal = 10.dp))
                Toggle("どのアプリの上でも、画面の端をなぞるとタブを出す", model.edgeOn) {
                    model.changeEdge(it)
                    if (it && !model.edgeAllowed) actions.start(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + actions.activity.packageName)))
                }
                if (model.edgeOn && !model.edgeAllowed) {
                    Note("「ほかのアプリの上に表示」の許可が要ります。許可して戻ると使えるようになります。")
                    FlowRow2 {
                        PillButton("許可する画面を開く", {
                            actions.start(Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + actions.activity.packageName)))
                        }, primary = true)
                    }
                }
                if (model.edgeOn) {
                    FlowRow2 {
                        listOf("left" to "左の端", "right" to "右の端").forEach { (k, label) ->
                            PillButton(label, { model.changeEdgeSide(k) }, primary = model.edgeSide == k)
                        }
                    }
                    FlowRow2 {
                        listOf(10 to "上", 35 to "やや上", 55 to "まん中", 75 to "やや下", 90 to "下").forEach { (k, label) ->
                            PillButton(label, { model.changeEdgePos(k) }, primary = model.edgePos == k)
                        }
                    }
                }
                Note("端の細い線を内側へなぞる（または押す）と、カテゴリーのタブとアプリが出ます。HOME でホーム全体へ。外側を押すか「戻る」で閉じます。ホームを開いている間は線を隠します。消されないように常駐の通知が出ます（通知は長押しでしまえます）。右の端は Galaxy のエッジパネルと重なりやすいので、初めは左にしています。")
                Gap(18.dp)
                SectionTitle("ONE UI / Galaxy の見た目を合わせる", Modifier.padding(horizontal = 10.dp))
                Note("One UI のテーマは自分では配れないので、材料を作ります。壁紙を HUD にすると、One UI が壁紙から色を取って、クイックパネル・設定・キーボードなども黒とオレンジ寄りになります。アイコンは今の 3 色・配色で PNG に書き出し、Good Lock の Theme Park でアプリごとに選べます（手順は README）。")
                FlowRow2 {
                    // 今の画面（閉じた／開いた）全体の大きさ。端まで描いているので、窓の大きさがそのまま画面の大きさ
                    val root = actions.activity.window.decorView
                    PillButton("壁紙を HUD に（ダーク）", { model.applyHudWallpaper(root.width, root.height, dark = true) }, primary = true)
                    PillButton("壁紙を HUD に（ライト）", { model.applyHudWallpaper(root.width, root.height, dark = false) })
                    val job = model.artJob
                    if (job != null) PillButton("書き出し中 %d / %d".format(job.first, job.second), {}, enabled = false)
                    else PillButton("アイコンを PNG で書き出す（${model.apps.size} 個）", { model.exportIcons() })
                }
                Gap(18.dp)
                SectionTitle("NOTICE / 通知のスワイプ", Modifier.padding(horizontal = 10.dp))
                Note("タブの中の通知（MAIL・LINE・カテゴリーのタブ）は、右へスワイプで既読（「既読」のボタンがある通知はそれを押す。無ければ消す）。左へスワイプは次から選べます。長押しで、その通知のボタン（アーカイブなど）も選べます。")
                FlowRow2 {
                    NOTICE_LEFT.forEach { (k, label) -> PillButton("左：$label", { model.changeNoticeLeft(k) }, primary = model.noticeLeft == k) }
                }
                Gap(18.dp)
                SectionTitle("ICON / アプリのアイコン", Modifier.padding(horizontal = 10.dp))
                Note("世界観をそろえるため、アイコンを 3 色（地・基本・アクセント）に塗れます。色は下の配色で変えられ、型を作り直す必要はありません。Gemini はアイコンの形を整理して 3 色で描き直します（1 アプリ 1 回、自分の API キーで課金。アイコンの画像を Google に送ります）。まだ加工していないアプリは端末内の簡易加工で表示します。")
                FlowRow2 {
                    listOf(
                        IconStyle.ORIGINAL to "そのまま", IconStyle.MONO to "モノクロ",
                        IconStyle.LOCAL to "3 色（簡易）", IconStyle.GEMINI to "3 色（Gemini）",
                    ).forEach { (mode, label) -> PillButton(label, { model.changeIconMode(mode) }, primary = model.iconMode == mode) }
                }
                if (model.iconMode == IconStyle.LOCAL || model.iconMode == IconStyle.GEMINI) IconColorPicker(model)
                if (model.iconMode == IconStyle.GEMINI) {
                    val job = model.iconJob
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        Readout("API KEY", if (model.hasGeminiKey) "SET" else "未設定", accent = !model.hasGeminiKey)
                        Readout("MODEL", model.geminiModel)
                        Readout("DONE", "%03d / %03d".format(model.geminiDone(), model.apps.size))
                    }
                    if (job != null) {
                        Column(Modifier.padding(horizontal = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.fillMaxWidth().height(8.dp).border(1.dp, p.line)) {
                                Box(Modifier.fillMaxWidth(if (job.total == 0) 0f else job.done.toFloat() / job.total).height(8.dp).background(p.accent))
                            }
                            Text(
                                if (job.running) "加工中 %d / %d：%s".format(job.done + 1, job.total, job.current) else "終わりました（%d 件、失敗 %d 件）".format(job.total, job.failed),
                                color = p.muted, fontSize = 12.sp,
                            )
                            job.lastError?.let { Text("直近の失敗：$it", color = p.accent, fontSize = 12.sp) }
                        }
                    }
                    FlowRow2 {
                        PillButton(if (model.hasGeminiKey) "API キー・モデル" else "API キーを入れる", { geminiDialog = true }, primary = !model.hasGeminiKey)
                        if (job?.running == true) PillButton("止める", { model.cancelGeminiIcons() })
                        else {
                            PillButton("未加工のアプリを Gemini で加工", { model.startGeminiIcons() }, primary = model.hasGeminiKey, enabled = model.hasGeminiKey)
                            val legacy = model.geminiLegacy()
                            if (legacy > 0) PillButton("2 色のもの（$legacy 件）を 3 色で作り直す", { model.startGeminiIcons(upgrade = true) }, enabled = model.hasGeminiKey)
                        }
                        PillButton("加工した型をすべて消す", { model.clearGeminiIcons() })
                    }
                }
                Gap(18.dp)
                SectionTitle("端のタブ（押すと名前の変更・並べ替え）", Modifier.padding(horizontal = 10.dp))
            }
            itemsIndexed(model.tabs, key = { _, t -> t.id }) { i, t ->
                var menu by remember { mutableStateOf(false) }
                // 中身の一覧（フォルダは場所も）
                val folder = model.boardItems(t.id).firstOrNull { it.type == jp.yaegaki.theta.home.data.BoardItem.FOLDER }?.let(model::specOf)
                val key = model.keyOf(t)?.let { " · Alt+$it" }.orEmpty()
                Box {
                    RowButton(zebra = i, onClick = { menu = true }) {
                        Dot(p.group(model.groupOf(t)))
                        TwoLines(
                            t.label,
                            when {
                                t.kind == TabKind.CATEGORY -> "アプリのカテゴリー"
                                folder != null && folder.folderUri == null -> "フォルダ · 未割り当て"
                                folder?.nasPath != null -> "フォルダ · NAS " + folder.nasPath!!.ifEmpty { "/" }
                                folder?.driveFolder != null -> "フォルダ · DRIVE " + folder.driveFolder!!.second
                                folder != null -> "フォルダ · " + android.net.Uri.decode(folder.folderUri!!.substringAfterLast('/')).substringAfter(':')
                                else -> model.boardItems(t.id).joinToString(" · ") { it.kind?.defaultLabel ?: it.type.uppercase() }.ifEmpty { "空のタブ" } + key
                            },
                            Modifier.weight(1f),
                        )
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("名前を変える") }, onClick = { menu = false; renaming = t })
                        // フォルダを置いたタブは、そのフォルダの場所をここでも変えられる
                        if (folder != null) DropdownMenuItem(text = { Text("端末のフォルダを選ぶ") }, onClick = { menu = false; actions.pickFolder(folder.id) })
                        if (folder != null && model.nas.configured()) DropdownMenuItem(
                            text = { Text("NAS の今のフォルダ（${model.nasState.path.ifEmpty { "/" }}）にする") },
                            onClick = { menu = false; model.assignNasFolder(folder.id, model.nasState.path) },
                        )
                        val dTop = model.driveState.stack.lastOrNull()
                        if (folder != null && model.driveOn && dTop != null && !dTop.first.startsWith("~") && dTop.first.isNotEmpty()) DropdownMenuItem(
                            text = { Text("ドライブの今のフォルダ（${dTop.second}）にする") },
                            onClick = { menu = false; model.assignDriveFolder(folder.id, dTop.first, dTop.second) },
                        )
                        if (i > 0) DropdownMenuItem(text = { Text("上へ") }, onClick = { menu = false; model.updateTabs(model.tabs.move(i, i - 1)) })
                        if (i < model.tabs.lastIndex) DropdownMenuItem(text = { Text("下へ") }, onClick = { menu = false; model.updateTabs(model.tabs.move(i, i + 1)) })
                        DropdownMenuItem(text = { Text("このタブを外す") }, onClick = { menu = false; model.removeTab(t.id) })
                    }
                }
            }
            item {
                FlowRow2 { PillButton("＋ タブを追加", { adding = true }) }
                if (adding) AddTabDialog(model, onWidget = { adding = false; addingWidget = true }) { adding = false }
                if (addingWidget) AddWidgetDialog(model) { addingWidget = false }
                Gap(18.dp)
                Note("物理キーボード：Alt＋1〜9 でフォルダを置いたタブ、Alt＋C 予定・M メール・L LINE・P 人・A アプリ・B 栞・X Clip・R 記録・H Hatch。ホームで文字を打つとすぐ検索、Esc で閉じる。")
                Note("タブは端のタブの下の「＋」でも足せます（ふつうのタブ・アプリのカテゴリー）。ふつうのタブには、歯車からウィジェット・フォルダ・ページ・予定などを好きな数だけ足せます。長押しで並べ替え・名前・外す。")
                Note("アプリの一覧は、画面上部の検索の横のボタン（Alt＋A）で開きます。")
                Note("Theta Home Hatch ${BuildConfigInfo.versionName(actions.activity)}")
            }
        }
    }

    if (nasDialog) NasDialog(model) { nasDialog = false }
    if (driveDialog) DriveDialog(model) { driveDialog = false }
    if (sorting) SortGuide(model) { sorting = false }
    renamingPreset?.let { old ->
        var name by remember(old) { mutableStateOf(old) }
        AlertDialog(
            onDismissRequest = { renamingPreset = null },
            title = { Text(if (old.isEmpty()) "候補を追加" else "候補の名前") },
            text = { Field(name, { name = it.take(12) }, "名前") },
            confirmButton = {
                TextButton(onClick = {
                    if (old.isEmpty()) model.addPreset(name) else model.renamePreset(old, name)
                    renamingPreset = null
                }, enabled = name.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renamingPreset = null }) { Text("やめる") } },
        )
    }
    if (geminiDialog) {
        var key by remember { mutableStateOf("") }
        var modelName by remember { mutableStateOf(model.geminiModel) }
        AlertDialog(
            onDismissRequest = { geminiDialog = false },
            title = { Text("Gemini（アイコンの加工）") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(key, { key = it.trim() }, if (model.hasGeminiKey) "API キー（変えるときだけ）" else "API キー", password = true)
                    Field(modelName, { modelName = it.trim() }, IconStyle.DEFAULT_MODEL)
                    Text(
                        "Google AI Studio で作った自分の API キーを使います（画像の生成は有料のプロジェクトのキーが必要なことがあります）。キーは端末の鍵で暗号化して保存し、設定の書き出しには含めません。モデルが使えないときは gemini-2.5-flash-image も試します。",
                        color = p.muted, fontSize = 12.sp, lineHeight = 18.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { model.saveGemini(key.ifEmpty { null }, modelName); geminiDialog = false }) { Text("保存") } },
            dismissButton = {
                Row {
                    if (model.hasGeminiKey) TextButton(onClick = { model.saveGemini("", modelName); geminiDialog = false }) { Text("キーを消す") }
                    TextButton(onClick = { geminiDialog = false }) { Text("やめる") }
                }
            },
        )
    }
    if (importConfirm) {
        AlertDialog(
            onDismissRequest = { importConfirm = false },
            title = { Text("設定を読み込む") },
            text = { Text("今の設定（タブ・カテゴリー・栞など）は、読み込んだファイルの内容で置き換わります。ウィジェットはこの端末のものを残します。") },
            confirmButton = {
                TextButton(onClick = {
                    importConfirm = false
                    actions.openDocument(arrayOf("application/json", "text/plain", "application/octet-stream")) { model.importSettings(it) }
                }) { Text("ファイルを選ぶ") }
            },
            dismissButton = { TextButton(onClick = { importConfirm = false }) { Text("やめる") } },
        )
    }

    renamingCategory?.let { c ->
        var name by remember(c.id) { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { renamingCategory = null },
            title = { Text("カテゴリーの名前") },
            text = { Field(name, { name = it.take(12) }, "名前") },
            confirmButton = { TextButton(onClick = { model.renameCategory(c, name); renamingCategory = null }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { renamingCategory = null }) { Text("やめる") } },
        )
    }

    renaming?.let { t ->
        var name by remember(t.id) { mutableStateOf(t.label) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("タブの名前") },
            text = { Field(name, { name = it.take(8) }, "短い名前（縦に並べて表示）") },
            confirmButton = {
                TextButton(onClick = {
                    model.updateTabs(model.tabs.map { if (it.id == t.id) it.copy(label = name.ifBlank { t.label }) else it })
                    renaming = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("やめる") } },
        )
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    val p = LocalPalette.current
    RowButton(onClick = { onChange(!value) }) {
        Text(label, color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked = value, onCheckedChange = onChange)
    }
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> = toMutableList().also { it.add(to, it.removeAt(from)) }

/** クイック設定に「ホームのレスキュー」タイルを足すよう、Android に頼む（Android 13 以降）。 */
private fun addRescueTile(actions: Actions) {
    if (android.os.Build.VERSION.SDK_INT < 33) return
    val ctx = actions.activity
    val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java) ?: return
    runCatching {
        sbm.requestAddTileService(
            android.content.ComponentName(ctx, jp.yaegaki.theta.home.rescue.RescueTile::class.java),
            "ホームのレスキュー",
            android.graphics.drawable.Icon.createWithResource(ctx, jp.yaegaki.theta.home.R.drawable.ic_rescue),
            ctx.mainExecutor,
        ) { r ->
            actions.toast(if (r == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED || r == android.app.StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED) "クイック設定にあります" else "足しませんでした（通知パネルの編集からも足せます）")
        }
    }.onFailure { actions.toast("足せませんでした。通知パネルのクイック設定の編集から「ホームのレスキュー」を足してください") }
}

/** アイコンの配色：決まった組み合わせか、地・基本・アクセントを 1 色ずつ。見本のアイコンで確かめられる。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IconColorPicker(model: HomeModel) {
    val p = LocalPalette.current
    val spec = model.iconColors
    val (plate, base, accent) = iconTriple(p, spec)
    var slot by remember { mutableStateOf(-1) }
    Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("COLORS / 配色", color = p.faint, fontSize = 11.sp, letterSpacing = 1.5.sp, fontFamily = Hud.mono)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconStyle.PRESETS.forEach { (id, label) ->
                val on = if (id == "custom") spec.startsWith("custom:") else spec == id
                PillButton(label, {
                    if (id == "custom") {
                        if (!spec.startsWith("custom:")) model.changeIconColors(customSpec(plate, base, accent))
                        slot = 1
                    } else {
                        model.changeIconColors(id); slot = -1
                    }
                }, primary = on)
            }
        }
        // 見本：3 色の型をその場で塗る
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("地" to plate, "基本" to base, "アクセント" to accent).forEachIndexed { i, (label, c) ->
                Column(
                    Modifier.clickable(enabled = spec.startsWith("custom:")) { slot = i }.padding(2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(36.dp).background(c).border(if (slot == i) 2.dp else 1.dp, if (slot == i) p.accent else p.line))
                    Text(label, color = p.muted, fontSize = 10.sp)
                }
            }
            Box(Modifier.size(52.dp).background(plate).border(1.dp, p.line), contentAlignment = Alignment.Center) {
                Box(Modifier.size(28.dp).background(base), contentAlignment = Alignment.TopEnd) { Box(Modifier.size(10.dp).background(accent)) }
            }
        }
        if (spec.startsWith("custom:") && slot in 0..2) {
            Text("「${listOf("地", "基本", "アクセント")[slot]}」の色", color = p.muted, fontSize = 12.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                IconStyle.SWATCHES.forEach { argb ->
                    val c = androidx.compose.ui.graphics.Color(argb.toInt())
                    Box(Modifier.size(32.dp).background(c).border(1.dp, p.line).clickable {
                        val now = listOf(plate, base, accent).toMutableList().also { it[slot] = c }
                        model.changeIconColors(customSpec(now[0], now[1], now[2]))
                    })
                }
            }
        }
    }
}

private fun customSpec(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color, c: androidx.compose.ui.graphics.Color) =
    "custom:" + listOf(a, b, c).joinToString(",") { "%08X".format(it.toArgb()) }
