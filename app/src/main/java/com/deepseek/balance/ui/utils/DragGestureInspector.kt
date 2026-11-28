package com.deepseek.balance.ui.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.util.fastFirstOrNull
import kotlin.math.abs

// 移植自 AndroidLiquidGlass demo（Apache-2.0）：catalog/utils/DragGestureInspector.kt
suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit
) {
    awaitEachGesture {
        val initialDown = awaitFirstDown(false, PointerEventPass.Initial)

        val down = awaitFirstDown(false)
        val drag = initialDown

        onDragStart(down)
        onDrag(drag, Offset.Zero)
        val upEvent =
            drag(
                pointerId = drag.id,
                onDrag = { onDrag(it, it.positionChange()) }
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
    pointerId: PointerId
): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}

/**
 * 方向仲裁版拖拽手势：供「可滚动列表内的横向拖拽控件」使用（LiquidToggle）。
 *
 * 与 [inspectDragGestures] 的差别只在手势归属判定，拖拽本身的手感不变：
 * - 按下后先等 touch slop。**竖直**位移先越界 → 直接放弃整个手势，不消费事件，
 *   交给外层 LazyColumn 滚动（不会把「滚动」误判成点击）；
 * - **水平**位移先越界 → 进入拖拽并 consume，外层列表不再抢这次手势；
 * - 未越过 slop 就抬手，且期间事件没被外层消费 → 才算点击；
 * - 任何时刻事件被外层消费 → 只回调 onCancel，绝不触发 onTap。
 *
 * 这正是原版 [inspectDragGestures] 缺失的部分：它把「被消费」直接转成 onDragCancel，
 * 而取消与抬手在上层无法区分，导致竖直滑动开关时被当成点击翻转。
 */
suspend fun PointerInputScope.inspectHorizontalDragGestures(
    onPressStart: () -> Unit = {},
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
    onDragEnd: () -> Unit = {},
    onTap: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (down.isConsumed) {
            onCancel()
            return@awaitEachGesture
        }
        onPressStart()
        val touchSlop = viewConfiguration.touchSlop
        var accumulated = Offset.Zero
        var dragging = false
        while (true) {
            val change = awaitPointerEvent().changes.fastFirstOrNull { it.id == down.id }
            if (change == null) {
                onCancel()
                break
            }
            if (change.isConsumed) {
                onCancel()
                break
            }
            if (change.changedToUpIgnoreConsumed()) {
                if (dragging) onDragEnd() else onTap()
                break
            }
            val delta = change.positionChange()
            if (!dragging) {
                accumulated += delta
                if (abs(accumulated.y) > touchSlop && abs(accumulated.y) > abs(accumulated.x)) {
                    // 竖直为主：放弃本次手势，让外层列表接管滚动
                    onCancel()
                    break
                }
                if (abs(accumulated.x) > touchSlop) {
                    dragging = true
                    change.consume()
                    onDragStart(down)
                    // 补发 slop 之前的累计位移，避免起步丢一小段
                    onDrag(change, accumulated)
                }
            } else {
                change.consume()
                onDrag(change, delta)
            }
        }
    }
}
