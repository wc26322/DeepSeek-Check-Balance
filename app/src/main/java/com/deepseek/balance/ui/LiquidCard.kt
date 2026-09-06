package com.deepseek.balance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
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
// 120Hz 性能架构（2026-09-06 重构）：
// 此前「每张卡片各自一层 drawBackdrop」→ 滚动/拖动时 RenderThread 为可见的每张全宽卡
// 逐层重建渲染命令（gfxinfo：GPU 3ms 但帧 18-44ms、legacy jank 68%——瓶颈在 RT 不在 GPU）。
// 改为「@Composable GlassPage 整页一块玻璃板 + LiquidCard 轻量面片」：
//   滚动时 RT 只需重建 1 块玻璃板，视觉上与逐卡玻璃等价（折射源同为 LocalLiquidCardBackdrop，
//   该层不包含滚动内容，玻璃折射的始终是壁纸层）。
// drawBackdrop 效果参数取官方 demo 的 lens(8,16)（较 16,32 再减半），降 RT 构建成本。
// ============================================================================

/** 整页玻璃板：一个 drawBackdrop 层为整屏提供玻璃折射底。放在页面滚动容器的最底层 */
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

/**
 * 玻璃卡**轻量面片**（配合整页 GlassPage 使用）：不再自建 backdrop 层，
 * 只做圆角 + 半透明蒙层（surfaceColor），让下层整页玻璃板透出 → 保"玻璃卡"观感。
 * surfaceOverlay：原 drawBackdrop onDrawSurface 扩展点的透传（余额卡品牌渐变蒙层等）。
 */
@Composable
internal fun LiquidCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 32f.dp,
    surfaceColor: Color = Color.Unspecified,
    surfaceOverlay: (DrawScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.clip(RoundedRectangle(cornerRadius))
    val colored = if (surfaceColor.isSpecified) base.background(surfaceColor) else base
    Column(
        colored.drawWithContent {
            surfaceOverlay?.invoke(this)
            drawContent()
        },
        content = content
    )
}