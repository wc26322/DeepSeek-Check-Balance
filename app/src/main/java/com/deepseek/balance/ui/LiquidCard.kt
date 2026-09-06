package com.deepseek.balance.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle

// 全 app 玻璃卡片共用的 backdrop 录制层：由 MainActivity 在根层录制 AmbientBackground（环境光斑，
// 官方 BackdropDemoScaffold 中"壁纸"的同角色），并用 CompositionLocalProvider 下发。
// 卡片位于该录制层之外（兄弟子树），不会触发"录制层包含消费者"的 prepareTree 递归崩溃；
// 且光斑层静止不随滚动重录，比底栏折射两屏内容便宜得多。
val LocalLiquidCardBackdrop = staticCompositionLocalOf<Backdrop> {
    error("LiquidCard 必须在 LocalLiquidCardBackdrop 提供者内使用（MainActivity 根层已提供）")
}

// 逐字照搬 AndroidLiquidGlass demo（Apache-2.0）的玻璃卡配方：
//   - LazyScrollContainerContent.kt（官方滚动列表卡片）：
//       drawBackdrop(backdrop, shape = RoundedRectangle(32dp), effects = { vibrancy(); lens(16dp, 32dp) })
//   - LiquidButton.kt 的 surfaceColor 参数：官方用于在玻璃表面叠一层颜色保证文字可读；
//   - surfaceOverlay：drawBackdrop 官方 onDrawSurface 扩展点的透传（余额卡用它画品牌渐变蒙层）。
@Composable
internal fun LiquidCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32f.dp,
    surfaceColor: Color = Color.Unspecified,
    surfaceOverlay: (DrawScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier
            .drawBackdrop(
                backdrop = LocalLiquidCardBackdrop.current,
                shape = { RoundedRectangle(cornerRadius) },
                effects = {
                    vibrancy()
                    lens(16f.dp.toPx(), 32f.dp.toPx())
                },
                onDrawSurface = {
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                    surfaceOverlay?.invoke(this)
                }
            ),
        content = content
    )
}
