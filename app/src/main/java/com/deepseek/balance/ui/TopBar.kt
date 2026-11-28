package com.deepseek.balance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import androidx.compose.foundation.shape.RoundedCornerShape

// 悬浮液态玻璃顶栏：通过 drawBackdrop 折射其下方的滚动内容
// （设置入口已移至 MainActivity 的悬浮底部标签栏 BottomTabs）
@Composable
internal fun TopBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val tuningState = LocalLiquidGlassTuningState.current
    val darkTheme = isSystemInDarkTheme()
    val luminanceMapState = LocalBackdropLuminanceMap.current
    val windowSize = LocalWindowInfo.current.containerSize
    // 与卡片共用同一套亮度自适应，保证玻璃面观感一致
    val adaptiveState = rememberGlassAdaptiveState()
    val contentColor = rememberAdaptiveContentColor(adaptiveState.tracker)

    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Row(
            modifier = modifier
                .height(UiComponentSize.TopBarHeight)
                .glassAdaptiveBounds(adaptiveState)
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedCornerShape(tuningState.value.cornerRadiusDp.dp) },
                    effects = {
                        val t = tuningState.value
                        val adaptive = adaptiveState.response(t)
                        colorControls(
                            brightness = adaptive.brightness,
                            contrast = adaptive.contrast,
                            saturation = if (t.vibrancy) 1.5f else 1f,
                        )
                        blur(adaptive.blurDp.dp.toPx())
                        lens(
                            t.refractionHeightDp.dp.toPx(),
                            t.refractionAmountDp.dp.toPx(),
                            chromaticAberration = t.chromaticAberration,
                            depthEffect = t.depthEffect,
                        )
                    },
                    highlight = { liquidGlassHighlight() },
                    onDrawSurface = { drawRect(liquidGlassSurfaceColorOf(tuningState.value, darkTheme)) },
                )
                .padding(horizontal = UiSpacing.Xl),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "余额",
                style = MaterialTheme.typography.headlineSmall,
                color = LocalContentColor.current,
            )
        }
    }
}