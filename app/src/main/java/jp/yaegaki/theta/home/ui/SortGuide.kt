package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.AppEntry
import jp.yaegaki.theta.home.data.Apps
import jp.yaegaki.theta.home.data.Categories
import jp.yaegaki.theta.home.data.IconStyle
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * アプリの仕分けガイド。
 * 1. カテゴリーを用意する（候補から選ぶ・自分で作る）
 * 2. アプリを 1 つずつ出し、振り分け先を押していく。合うものが無ければその場で新しく作る
 * 3. 終わったらカテゴリーごとの件数を見せる
 */
@Composable
fun SortGuide(model: HomeModel, onDismiss: () -> Unit) {
    var step by remember { mutableIntStateOf(if (model.categories.isEmpty()) 0 else 1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val p = LocalPalette.current
        Box(Modifier.fillMaxSize().background(p.bg).windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Stripes(Modifier.fillMaxWidth().height(5.dp))
                Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text("[ APP SORTER ] // STEP ${step + 1} / 2", color = p.accent, fontSize = 11.sp, letterSpacing = 2.sp, fontFamily = Hud.mono)
                        Text(if (step == 0) "カテゴリーを用意" else "アプリを仕分ける", color = p.fg, fontSize = 30.sp, fontFamily = Hud.display, lineHeight = 36.sp)
                    }
                    GlyphButton(Glyph.CLOSE, "閉じる", onDismiss)
                }
                Ticks(Modifier.fillMaxWidth().height(6.dp))
                Gap(12.dp)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (step == 0) PrepareStep(model) { step = 1 } else SortStep(model, onBackToPrepare = { step = 0 }, onDone = onDismiss)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrepareStep(model: HomeModel, onNext: () -> Unit) {
    val p = LocalPalette.current
    val existing = model.categories.map { it.name }.toSet()
    var picked by remember { mutableStateOf(if (existing.isEmpty()) model.categoryDefaults.toSet() else existing) }
    var custom by remember { mutableStateOf("") }
    val presets = model.categoryPresets
    val extra = picked.filter { it !in presets }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("振り分け先を選んでください。あとから仕分けの途中でも足せます。", color = p.muted, fontSize = 14.sp)
        SectionTitle("PRESET / 候補")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (presets + extra + existing.filter { it !in presets && it !in extra }).distinct().forEach { name ->
                val on = name in picked
                ChoiceButton(name, on) { picked = if (on) picked - name else picked + name }
            }
        }
        SectionTitle("NEW / 自分で作る")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(custom, { custom = it.take(12) }, "例：病院、子ども、趣味", Modifier.weight(1f))
            PillButton("追加", {
                if (custom.isNotBlank()) {
                    picked = picked + custom.trim(); custom = ""
                }
            })
        }
        Note("選ばなかった候補のうち、すでに作ってあるカテゴリーは消しません（中のアプリもそのまま）。候補そのものは、設定の「仕分けの候補」で名前の変更・削除ができます。")
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("${picked.size} 個で仕分けを始める", {
                model.ensureCategories(picked.toList())
                onNext()
            }, primary = true, enabled = picked.isNotEmpty())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortStep(model: HomeModel, onBackToPrepare: () -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    val total = model.apps.size
    val queue = model.unsorted()
    val app = queue.firstOrNull()
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (app == null) {
        Done(model, onBackToPrepare, onDone)
        return
    }
    val done = total - queue.size
    val suggestion = model.suggestedCategory(app)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 進み具合
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Readout("SORTED", "%03d / %03d".format(done, total), accent = true)
            Readout("LEFT", "%03d".format(queue.size))
            Box(Modifier.weight(1f).height(8.dp).border(1.dp, p.line)) {
                Box(Modifier.fillMaxWidth(if (total == 0) 0f else done.toFloat() / total).height(8.dp).background(p.accent))
            }
        }
        // 今のアプリ
        Row(
            Modifier.fillMaxWidth().border(1.dp, p.line).corners(p.accent).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AppIcon(app, 64.dp)
            Column(Modifier.weight(1f)) {
                Text("TARGET", color = p.faint, fontSize = 10.sp, letterSpacing = 2.sp, fontFamily = Hud.mono)
                Text(app.label, color = p.fg, fontSize = 26.sp, fontFamily = Hud.display, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(app.packageName, color = p.faint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (app.key in model.skipped) Text("あとでにしたアプリ", color = p.accent, fontSize = 11.sp)
            }
        }
        // 振り分け先
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("ASSIGN TO / 振り分け先", Modifier.weight(1f))
            if (suggestion != null) Text("おすすめ：${suggestion.name}", color = p.accent, fontSize = 12.sp)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.categories.forEach { c ->
                val count = model.appCategories.count { it.value == c.id }
                ChoiceButton("${c.name}  ${count}", selected = false, suggested = c == suggestion) { model.assign(app, c) }
            }
            ChoiceButton("＋ 新しいカテゴリー", selected = creating) { creating = !creating }
        }
        if (creating) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(newName, { newName = it.take(12) }, "カテゴリー名", Modifier.weight(1f))
                PillButton("作って振り分け", {
                    if (newName.isNotBlank()) {
                        model.assign(app, model.addCategory(newName)); newName = ""; creating = false
                    }
                }, primary = true, enabled = newName.isNotBlank())
            }
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("戻す", { model.undoSort() }, enabled = done > 0 || model.skipped.isNotEmpty())
            PillButton("あとで", { model.skip(app) })
            if (Apps.isSystem(actions.activity, app.packageName)) {
                PillButton("無効化（アプリ情報）", { actions.start(Apps.infoIntent(app.packageName)) })
            } else {
                PillButton("アンインストール", { actions.start(Apps.uninstallIntent(app.packageName)) })
                PillButton("アプリ情報", { actions.start(Apps.infoIntent(app.packageName)) })
            }
            PillButton("カテゴリーを見直す", onBackToPrepare)
            PillButton("ここで終わる", onDone)
        }
        Note("押すとすぐ次のアプリへ進みます。分からないものは「あとで」で後ろに回せます。要らないアプリは「アンインストール」（確認画面が出ます）。消すと一覧から外れます。")
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Done(model: HomeModel, onBackToPrepare: () -> Unit, onDone: () -> Unit) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("COMPLETE", color = p.accent, fontSize = 13.sp, letterSpacing = 3.sp, fontFamily = Hud.mono)
        Text("すべてのアプリを仕分けました", color = p.fg, fontSize = 22.sp, fontFamily = Hud.display)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            model.categories.forEach { c ->
                Readout(c.name, "%03d".format(model.appCategories.count { it.value == c.id }), Modifier.border(1.dp, p.line).padding(10.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("閉じる", onDone, primary = true)
            PillButton("カテゴリーを見直す", onBackToPrepare)
        }
    }
}

/** 押せる大きめの選択肢。selected は塗り、suggested はオレンジの枠と印。 */
@Composable
fun ChoiceButton(text: String, selected: Boolean, suggested: Boolean = false, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .defaultMinSize(minHeight = 48.dp)
            .widthIn(min = 72.dp)
            .popShadow(p, hudShape)
            .background(if (selected) p.accent else if (suggested) p.raised else if (p.pop) p.panel else Color.Transparent, hudShape)
            .border(if (suggested) 2.dp else lineWidth, if ((selected || suggested) && !p.pop) p.accent else p.line, hudShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = text + if (suggested) "（おすすめ）" else "" }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (suggested) Box(Modifier.size(6.dp).background(p.accent))
        Text(text, color = if (selected) p.onAccent else p.fg, fontSize = 15.sp)
    }
}

/** アイコンの見せ方（設定の ICON）。version は型を作り直したときに増え、描き直しのきっかけになる。 */
data class IconLook(val mode: String, val version: Int, val colors: String = "hud")

/** アイコンの 3 色（地・基本・アクセント）を、配色の設定とテーマから決める。 */
fun iconTriple(p: Palette, spec: String): Triple<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> = when {
    spec == "orange" -> Triple(p.raised, p.accent, p.fg)
    spec == "solid" -> Triple(p.accent, p.onAccent, p.fg)
    spec == "mono" -> Triple(p.raised, p.fg, p.faint)
    spec.startsWith("custom:") -> spec.removePrefix("custom:").split(',').mapNotNull { it.toLongOrNull(16) }.let { c ->
        if (c.size == 3) Triple(androidx.compose.ui.graphics.Color(c[0].toInt()), androidx.compose.ui.graphics.Color(c[1].toInt()), androidx.compose.ui.graphics.Color(c[2].toInt()))
        else Triple(p.raised, p.fg, p.accent)
    }
    else -> Triple(p.raised, p.fg, p.accent)
}

val LocalIconLook = androidx.compose.runtime.staticCompositionLocalOf { IconLook(IconStyle.MONO, 0) }

/**
 * アプリのアイコン。ツートーンのときは白黒の型をテーマの 2 色（形＝オレンジ、地＝面の色）で塗る。
 * Gemini で加工していないアプリは、端末内で作った型で代わりに見せる（世界観をそろえるため）。
 */
@Composable
fun AppIcon(app: AppEntry, size: Dp) {
    val ctx = LocalActions.current.activity
    val px = with(LocalDensity.current) { size.roundToPx() }
    LookIcon(app.key, app.label, size) { Apps.icon(ctx, app, maxOf(px, 192)) }
}

/** アプリのショートカット（LINE のトークルームなど）のアイコン。置いたときに保存した絵を、アプリと同じ見た目で。 */
@Composable
fun ShortcutIcon(sc: jp.yaegaki.theta.home.data.PinnedShortcut, size: Dp) {
    val ctx = LocalActions.current.activity
    LookIcon("sc:" + sc.id, sc.label, size) { jp.yaegaki.theta.home.data.Shortcuts.icon(ctx, sc.id) }
}

/** アイコンを、設定の見た目（そのまま・モノクロ・ツートーン）で描く。key は簡易加工の型を使い回すため。 */
@Composable
fun LookIcon(key: String, label: String, size: Dp, load: () -> android.graphics.Bitmap?) {
    val ctx = LocalActions.current.activity
    val look = LocalIconLook.current
    val p = LocalPalette.current
    val icon by produceState<android.graphics.Bitmap?>(null, key, look) {
        value = withContext(Dispatchers.IO) {
            val original = load() ?: return@withContext null
            when (look.mode) {
                IconStyle.GEMINI -> IconStyle.cached(ctx, key) ?: IconStyle.localMaskCached(key, original)
                IconStyle.LOCAL -> IconStyle.localMaskCached(key, original)
                else -> original
            }
        }
    }
    val filter = remember(look.mode, look.colors, p) {
        when (look.mode) {
            IconStyle.MONO -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
            IconStyle.LOCAL, IconStyle.GEMINI -> iconTriple(p, look.colors).let { (plate, base, accent) -> IconStyle.threeTone(plate, base, accent) }
            else -> null
        }
    }
    val inset = if (look.mode == IconStyle.LOCAL || look.mode == IconStyle.GEMINI) 0.dp else 8.dp
    Box(Modifier.size(size).border(1.dp, p.line), contentAlignment = Alignment.Center) {
        icon?.let { Image(it.asImageBitmap(), contentDescription = null, colorFilter = filter, modifier = Modifier.size(size - inset)) }
            ?: Text(label.take(1), color = p.fg, fontSize = (size.value * 0.4f).sp)
    }
}
