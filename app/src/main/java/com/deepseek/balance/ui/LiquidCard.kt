package com.deepseek.balance.ui

import androidx.compose.foundation.layout.Box
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

// 全 app 玻璃共用的 backdrop 录制层：由 MainActivity 在根层录制 AmbientBackground（环境光斑，
// 官方 BackdropDemoScaffold 中"壁纸"的同角色），并用 CompositionLocalProvider 下发。
// 调用方位于该录制层之外（兄弟子树），不会触发"录制层包含消费者"的 prepareTree 递归崩溃；
// 且光斑层静止不随滚动重录。
val LocalLiquidCardBackdrop = staticCompositionLocalOf<Backdrop> {
    error("LiquidCard 必须在 LocalLiquidCardBackdrop 提供者内使用（MainActivity 根层已提供）")
}

// ============================================================================
// 2026-09-26 恢复官方 AndroidLiquidGlass 架构（用户要求参照官方项目）：
// 每张卡片自己一层 drawBackdrop —— 官方 LazyScrollContainerContent.kt 逐字配方：
//   drawBackdrop(backdrop, RoundedRectangle(32dp), effects = { vibrancy(); lens(16dp, 32dp) })
// 官方 demo 用这个配方在 LazyColumn 里滚动 100 张玻璃卡，流畅度经过官方验证。
// （v1.4.7 曾因 RT 帧耗时改为"整页 GlassPage 单层玻璃板 + 卡片轻量面片"，
//   代价是卡片失去各自的折射细节；现按用户要求回归官方逐卡玻璃。
//   若滚动掉帧回归，可再议：降 lens 参数或恢复混合方案，需用户拍板。）
// ============================================================================

/**
 * 液态玻璃卡（官方配方）：卡片自身折射壁纸层（LocalLiquidCardBackdrop）。
 * - surfaceColor：官方 LiquidButton.kt 同名参数的用法——画在玻璃表面保证文字可读；
 * - surfaceOverlay：官方 drawBackdrop onDrawSurface 扩展点的透传（余额卡品牌渐变蒙层）。
 */
@Composable
internal fun LiquidCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32f.dp,
    surfaceColor: Color = Color.Unspecified,
    surfaceOverlay: (DrawScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier.drawBackdrop(
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

/** 整页玻璃板：一个 drawBackdrop 层提供整块玻璃面。现仅用于调参弹窗（弹窗本身就是"一块玻璃"） */
@Composable
internal fun GlassPage(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0f.dp,
) {
    Box(
        modifier.drawBackdrop(
            backdrop = LocalLiquidCardBackdrop.current,
            shape = { RoundedRectangle(cornerRadius) },
            effects = {
                vibrancy()
                lens(8f.dp.toPx(), 16f.dp.toPx())
            },
        )
    )
}
