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
    // API<31 无渲染效果时加深蒙层保证可读；API>=31 用轻蒙层模拟 iOS 毛玻璃的材质感
    val scrimColor = MaterialTheme.colorScheme.background.copy(
        alpha = if (isRenderEffectSupported()) 0.35f else 0.86f,
    )

    Row(
        modifier = modifier
            .height(56.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { Capsule() },
                effects = {
                    vibrancy()
                    blur(24f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = { drawRect(scrimColor) },
            )
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "余额",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
