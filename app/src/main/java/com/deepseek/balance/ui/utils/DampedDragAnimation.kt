package com.deepseek.balance.ui.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.time.Clock

// 移植自 AndroidLiquidGlass demo（Apache-2.0）：catalog/utils/DampedDragAnimation.kt
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    // 本 app 自定义交互：把拖拽点的局部坐标传出去，供「胶囊中心跟随手指位置」用
    val onDrag: DampedDragAnimation.(position: Offset, dragAmount: Offset) -> Unit,
) {

    // 按压看门狗超时：press() 后若 N 秒内没有 release()，强制归位（兜底，见 press() 注释）
    private companion object {
        const val PRESS_WATCHDOG_MS = 4000L
    }

    private val valueAnimationSpec =
        spring(1f, 1000f, visibilityThreshold)
    private val velocityAnimationSpec =
        spring(0.5f, 300f, visibilityThreshold * 10f)
    private val pressProgressAnimationSpec =
        spring(1f, 1000f, 0.001f)
    private val scaleXAnimationSpec =
        spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec =
        spring(0.7f, 250f, 0.001f)

    private val valueAnimation =
        Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation =
        Animatable(0f, 5f)
    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val scaleXAnimation =
        Animatable(initialScale, 0.001f)
    private val scaleYAnimation =
        Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()

    private val velocityTracker = VelocityTracker()

    // 按压看门狗：press() 时启动，释放时取消防。
    // 若 onDragEnd/onDragCancel 因输入竞态丢失（冷启动首帧、事件被 clickable 抢占等），
    // 按压态（pressProgress/scale 停在按压值）会永久卡死 → 胶囊被拉伸且永不恢复。
    // 看门狗在 N 秒后强制 release() 兜底归位 —— 任何场景下最多 N 秒恢复。
    private var pressWatchdog: Job? = null

    val value: Float get() = valueAnimation.value
    val progress: Float get() = (value - valueRange.start) / (valueRange.endInclusive - valueRange.start)
    val targetValue: Float get() = valueAnimation.targetValue
    val pressProgress: Float get() = pressProgressAnimation.value
    val scaleX: Float get() = scaleXAnimation.value
    val scaleY: Float get() = scaleYAnimation.value
    val velocity: Float get() = velocityAnimation.value

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                onDragStarted(down.position)
                press()
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                onDragStopped()
                release()
            }
        ) { change, dragAmount ->
            onDrag(change.position, dragAmount)
        }
    }

    fun press() {
        velocityTracker.resetTracking()
        animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, scaleYAnimationSpec) }
        }
        // 看门狗：正常情况 release() 会先取消它；只有 release 丢失/被取消时才兜底
        pressWatchdog?.cancel()
        pressWatchdog = animationScope.launch {
            delay(PRESS_WATCHDOG_MS)
            release()
        }
    }

    fun release() {
        pressWatchdog?.cancel()
        pressWatchdog = null
        animationScope.launch {
            awaitFrame()
            if (value != targetValue) {
                val threshold = (valueRange.endInclusive - valueRange.start) * 0.025f
                // 兜底：等 value 稳定后再收按压（保持官方"弹到目标再收"的观感）。
                // 但不能无界等待——冷启动立刻点击时 valueAnimation 会被 updateValue /
                // animateToValue 的动画互斥反复取消重启，value 可能停在中间值且不再变化，
                // snapshotFlow 不再发射 → first() 无限挂起 → pressProgress/scale 永远停在
                // 按压态（胶囊被拉长且不恢复）。加超时：正常 300ms 内必稳定；异常强制归位。
                withTimeoutOrNull(300) {
                    snapshotFlow { valueAnimation.value }
                        .filter { abs(it - valueAnimation.targetValue) < threshold }
                        .first()
                }
            }
            launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(initialScale, scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(initialScale, scaleYAnimationSpec) }
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        animationScope.launch {
            launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) { updateVelocity() } }
        }
    }

    fun animateToValue(value: Float) {
        animationScope.launch {
            mutatorMutex.mutate {
                press()
                val targetValue = value.coerceIn(valueRange)
                launch { valueAnimation.animateTo(targetValue, valueAnimationSpec) }
                if (velocity != 0f) {
                    launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
                }
                release()
            }
        }
    }

    private fun updateVelocity() {
        velocityTracker.addPosition(
            Clock.System.now().toEpochMilliseconds(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / (valueRange.endInclusive - valueRange.start)
        animationScope.launch {
            // 防御：极端时序下计算出的速度可能非有限（NaN/Infinity），会让
            // velocityAnimation 进入 NaN → 胶囊 scale 形变项异常（拉伸/压扁且不恢复）
            velocityAnimation.animateTo(
                targetVelocity.takeIf { it.isFinite() } ?: 0f,
                velocityAnimationSpec
            )
        }
    }
}
