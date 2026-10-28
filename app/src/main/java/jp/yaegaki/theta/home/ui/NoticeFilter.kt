package jp.yaegaki.theta.home.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yaegaki.theta.home.HomeModel
import jp.yaegaki.theta.home.data.Store
import jp.yaegaki.theta.home.notify.HatchNotificationListener
import jp.yaegaki.theta.home.notify.Notice
import jp.yaegaki.theta.home.notify.NoticeRule

/** 条件に合う通知（新しい順）。条件が無ければ空。 */
@Composable
internal fun filteredNotices(model: HomeModel): List<Pair<Notice, NoticeRule>> {
    val all by HatchNotificationListener.notices.collectAsState()
    return NoticeRule.pick(model.noticeRules, all)
}

/**
 * ホームの「通知」の区画の中身：条件に合う通知だけ。押すと開く、右へスワイプで既読（タブの中の通知と同じ）。
 * 目立たせる条件に合ったものは、左にアクセントの帯。
 */
@Composable
internal fun FilteredNotices(model: HomeModel, list: List<Pair<Notice, NoticeRule>>, max: Int) {
    val p = LocalPalette.current
    val actions = LocalActions.current
    var editing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(model.noticeRules.joinToString(" · ") { it.name.ifEmpty { "条件" } }, color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("FILTER ⚙", color = p.faint, fontSize = 10.sp, fontFamily = Hud.mono,
                modifier = Modifier.clickable(onClickLabel = "通知の条件") { editing = true }.padding(horizontal = 6.dp, vertical = 2.dp))
        }
        if (!HatchNotificationListener.isEnabled(actions.activity)) {
            Text("通知へのアクセスを許可すると、条件に合う通知がここに出ます（内容は端末の中だけで使います）", color = p.muted, fontSize = 13.sp,
                modifier = Modifier.clickable { actions.start(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
        }
        list.take(max).forEachIndexed { i, (n, rule) ->
            val bar = if (rule.alert) p.accent else p.line
            Box(Modifier.fillMaxWidth().drawBehind { drawRect(bar, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height)) }.padding(start = 3.dp)) {
                NoticeRow(model, n, i, model.apps.firstOrNull { it.packageName == n.packageName })
            }
        }
        if (list.size > max) Text("+${list.size - max} 件", color = p.accent, fontSize = 12.sp, fontFamily = Hud.mono, modifier = Modifier.padding(vertical = 4.dp))
    }
    if (editing) NoticeRulesDialog(model) { editing = false }
}

/** たたんだときの 1 行：件数と、いちばん新しいもの。 */
@Composable
internal fun FilteredNoticesFolded(list: List<Pair<Notice, NoticeRule>>) {
    val p = LocalPalette.current
    val first = list.firstOrNull() ?: return
    Text("${list.size} 件  ${first.first.title}：${first.first.text}", color = if (list.any { it.second.alert }) p.accent else p.muted,
        fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/** 条件の一覧：足す・直す・外す。 */
@Composable
internal fun NoticeRulesDialog(model: HomeModel, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var editing by remember { mutableStateOf<NoticeRule?>(null) }
    val current = editing
    if (current != null) NoticeRuleDialog(model, current) { editing = null }
    else AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ホームに出す通知") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Note("条件に合う通知だけを、ホームの時計・予定・天気のところに出します。合うものが無いときは区画ごと隠します。")
                model.noticeRules.forEach { r ->
                    RowButton(onClick = { editing = r }) {
                        Box(Modifier.width(3.dp).height(28.dp).background(if (r.alert) p.accent else p.line))
                        TwoLines(r.name.ifEmpty { "条件" }, ruleSummary(model, r), Modifier.weight(1f).padding(start = 8.dp))
                    }
                }
                FlowRow2 { PillButton("＋ 条件を足す", { editing = NoticeRule(Store.newId(), "") }, primary = true) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("閉じる") } },
    )
}

internal fun ruleSummary(model: HomeModel, r: NoticeRule): String = listOfNotNull(
    if (r.apps.isEmpty()) "どのアプリでも" else r.apps.joinToString("・") { pkg -> model.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg },
    r.include.takeIf { it.isNotEmpty() }?.let { "「${it.joinToString("」「")}」を含む" },
    r.exclude.takeIf { it.isNotEmpty() }?.let { "「${it.joinToString("」「")}」は除く" },
).joinToString(" / ")

/** 条件 1 つを決める：名前・アプリ・含む言葉・除く言葉・目立たせるか。 */
@Composable
internal fun NoticeRuleDialog(model: HomeModel, rule: NoticeRule, onDismiss: () -> Unit) {
    val p = LocalPalette.current
    var name by remember { mutableStateOf(rule.name) }
    var apps by remember { mutableStateOf(rule.apps.toSet()) }
    var include by remember { mutableStateOf(rule.include.joinToString("、")) }
    var exclude by remember { mutableStateOf(rule.exclude.joinToString("、")) }
    var alert by remember { mutableStateOf(rule.alert) }
    var pickingApps by remember { mutableStateOf(false) }
    val exists = model.noticeRules.any { it.id == rule.id }
    if (pickingApps) {
        NoticeAppsDialog(model, apps) { chosen -> if (chosen != null) apps = chosen; pickingApps = false }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exists) "通知の条件" else "通知の条件を足す") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(name, { name = it }, "名前（例：至急・家族・配達）")
                SectionTitle("含む言葉（どれか 1 つ。空ならすべて）")
                Field(include, { include = it }, "例：至急、配達、〇〇さん", singleLine = false)
                SectionTitle("除く言葉")
                Field(exclude, { exclude = it }, "例：広告、セール", singleLine = false)
                Note("言葉は「、」で区切ります。題名と本文の両方から探します。")
                RowButton(onClick = { alert = !alert }) {
                    Text("目立たせる（アクセントの色）", color = p.fg, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(checked = alert, onCheckedChange = { alert = it })
                }
                SectionTitle("アプリ")
                Text(if (apps.isEmpty()) "どのアプリでも" else apps.joinToString("・") { pkg -> model.apps.firstOrNull { it.packageName == pkg }?.label ?: pkg },
                    color = p.muted, fontSize = 13.sp)
                FlowRow2 { PillButton("アプリを選ぶ…", { pickingApps = true }) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val inc = NoticeRule.words(include)
                val r = rule.copy(
                    name = name.trim().ifEmpty { inc.firstOrNull() ?: apps.firstOrNull()?.let { pkg -> model.apps.firstOrNull { it.packageName == pkg }?.label } ?: "通知" },
                    apps = apps.toList(), include = inc, exclude = NoticeRule.words(exclude), alert = alert,
                )
                model.saveNoticeRule(r)
                onDismiss()
            }) { Text("決める") }
        },
        dismissButton = {
            Row {
                if (exists) TextButton(onClick = { model.removeNoticeRule(rule.id); onDismiss() }) { Text("外す", color = p.accent) }
                TextButton(onClick = onDismiss) { Text("やめる") }
            }
        },
    )
}

/** 条件のアプリを選ぶ。今通知が来ているアプリには「•」。null で閉じたら変えない。 */
@Composable
private fun NoticeAppsDialog(model: HomeModel, initial: Set<String>, onDone: (Set<String>?) -> Unit) {
    val p = LocalPalette.current
    var chosen by remember { mutableStateOf(initial) }
    var q by remember { mutableStateOf("") }
    val all by HatchNotificationListener.notices.collectAsState()
    val noticing = remember(all) { all.map { it.packageName }.toSet() }
    // 選んだアプリ → 今通知が来ているアプリ → ほか（並びは開いたときのまま）
    val order = remember { model.apps.sortedWith(compareBy({ it.packageName !in initial }, { it.packageName !in noticing }, { it.label })) }
    val list = order.filter { model.appMatches(it, q) }
    AlertDialog(
        onDismissRequest = { onDone(null) },
        title = { Text(if (chosen.isEmpty()) "アプリ（選ばなければどれでも）" else "アプリ（${chosen.size}）") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Field(q, { q = it }, "アプリを探す")
                androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list.size, key = { list[it].key }) { i ->
                        val a = list[i]
                        RowButton(onClick = { chosen = if (a.packageName in chosen) chosen - a.packageName else chosen + a.packageName }) {
                            Checkbox(checked = a.packageName in chosen, onCheckedChange = null)
                            AppIcon(a, 24.dp)
                            Text(a.label + if (a.packageName in noticing) "  •" else "", color = p.fg, fontSize = 14.sp, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(chosen) }) { Text("決める") } },
        dismissButton = { TextButton(onClick = { onDone(null) }) { Text("やめる") } },
    )
}
