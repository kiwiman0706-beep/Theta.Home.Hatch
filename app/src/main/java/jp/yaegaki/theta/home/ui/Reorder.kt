package jp.yaegaki.theta.home.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.zIndex

/**
 * 長押し→ドラッグで並べ替える。指を動かさずに離したら onMenu（長押しのメニュー）。
 * 持ち上げたものは指に付いて動き、落とす先は枠で示す。落とすと onDrop(持ち上げたもの, 落とした先)。
 * group が同じものの間だけで並べ替える（タブとウィジェットのタブ、カテゴリーごとのアプリなど）。
 */
class ReorderState {
    var dragging by mutableStateOf<String?>(null)
    var offset by mutableStateOf(Offset.Zero)
    var target by mutableStateOf<String?>(null)
    internal val bounds = mutableStateMapOf<String, Rect>()
    internal val groups = HashMap<String, String>()

    internal fun findTarget(key: String): String? {
        val b = bounds[key] ?: return null
        val c = b.center + offset
        val g = groups[key]
        return bounds.entries.firstOrNull { it.key != key && groups[it.key] == g && it.value.contains(c) }?.key
    }
}

@Composable
fun rememberReorderState() = remember { ReorderState() }

@Composable
fun Modifier.reorderable(
    state: ReorderState,
    key: String,
    group: String,
    highlight: Color,
    onMenu: () -> Unit,
    onDrop: (from: String, to: String) -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val menu = rememberUpdatedState(onMenu)
    val drop = rememberUpdatedState(onDrop)
    val lifted = state.dragging == key
    val isTarget = state.target == key
    return this
        .onGloballyPositioned { state.bounds[key] = it.boundsInWindow(); state.groups[key] = group }
        .zIndex(if (lifted) 10f else 0f)
        .graphicsLayer {
            if (lifted) {
                translationX = state.offset.x
                translationY = state.offset.y
                scaleX = 1.06f
                scaleY = 1.06f
                alpha = 0.92f
            }
        }
        .drawWithContent {
            drawContent()
            if (isTarget) drawRect(highlight, style = Stroke(width = 3 * density))
        }
        .pointerInput(key, group) {
            // 押したまま動かさずに長押しの時間が過ぎたら持ち上げる。その前に動いたら（スクロール）何もしない。
            // 同じ要素の clickable が押下を受けても止まらないよう、自前で見張る
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var lifted = false
                var aborted = false
                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    while (true) {
                        val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id }
                        if (ch == null || !ch.pressed || (ch.position - down.position).getDistance() > viewConfiguration.touchSlop) {
                            aborted = true
                            break
                        }
                    }
                }
                if (aborted) return@awaitEachGesture
                lifted = true
                var moved = 0f
                state.dragging = key
                state.offset = Offset.Zero
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                while (lifted) {
                    val ch = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    if (!ch.pressed) {
                        ch.consume()
                        break
                    }
                    val d = ch.positionChangeIgnoreConsumed()
                    ch.consume()
                    moved += d.getDistance()
                    state.offset += d
                    state.target = state.findTarget(key)
                }
                val to = state.target
                state.dragging = null
                state.target = null
                state.offset = Offset.Zero
                if (moved < 12 * density) menu.value() else if (to != null) drop.value(key, to)
            }
        }
}
