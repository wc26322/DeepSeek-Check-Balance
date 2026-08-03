package com.deepseek.balance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import java.util.Locale

/**
 * 金额数字滚动：每次 restartKey 变化时，先瞬间归零，再从 0 平滑过渡到 target。
 * 由刷新完成信号驱动，保证每次刷新后都播放完整的 0 → 当前值 动画。
 */
@Composable
internal fun AnimatedAmount(
    target: Double,
    style: TextStyle,
    color: Color,
    restartKey: Int,
    modifier: Modifier = Modifier,
    durationMs: Int = 700,
) {
    val animatable = remember { Animatable(0f) }
    LaunchedEffect(restartKey) {
        animatable.snapTo(0f)
        animatable.animateTo(target.toFloat(), animationSpec = tween(durationMs))
    }
    Text(
        text = String.format(Locale.US, "%.2f", animatable.value),
        modifier = modifier,
        style = style,
        color = color,
    )
}
