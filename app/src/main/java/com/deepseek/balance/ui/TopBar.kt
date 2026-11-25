package com.deepseek.balance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.shapes.Capsule

// 悬浮液态玻璃顶栏：通过 drawBackdrop 折射其下方的滚动内容
// （设置入口已移至 MainActivity 的悬浮底部标签栏 BottomTabs）
@Composable
internal fun TopBar(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
) {
    val tuning = LocalLiquidGlassTuning.current
    val surfaceColor = liquidGlassSurfaceColor()

    Row(
        modifier = modifier
            .height(UiComponentSize.TopBarHeight)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    if (tuning.vibrancy) vibrancy()
                    blur(tuning.blurDp.dp.toPx())
                    lens(
                        tuning.refractionHeightDp.dp.toPx(),
                        tuning.refractionAmountDp.dp.toPx(),
                        chromaticAberration = tuning.chromaticAberration,
                        depthEffect = tuning.depthEffect,
                    )
                },
                highlight = { liquidGlassHighlight() },
                onDrawSurface = { drawRect(surfaceColor) },
            )
            .padding(horizontal = UiSpacing.Xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "余额",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
