package com.deepseek.balance.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LayoutCoordinates

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import androidx.compose.foundation.shape.RoundedCornerShape

// 全 app 玻璃共用的 backdrop 录制层：由 MainActivity 在根层录制 AmbientBackground（环境光斑，
// 官方 BackdropDemoScaffold 中"壁纸"的同角色），并用 CompositionLocalProvider 下发。
// 调用方位于该录制层之外（兄弟子树），不会触发"录制层包含消费者"的 prepareTree 递归崩溃；
// 且光斑层静止不随滚动重录。
val LocalLiquidCardBackdrop = staticCompositionLocalOf<Backdrop> {
    error("LiquidCard 必须在 LocalLiquidCardBackdrop 提供者内使用（MainActivity 根层已提供）")
}

/**
 * 取样坐标系原点：由「卡片所在的页面容器」下发（渲染在滑动位移层内部）。
 *
 * 为什么需要：两屏切换是整页做 graphicsLayer 位移，`boundsInRoot()` 会把这段位移算进去。
 * 于是设置页在 App 启动时位于窗口外，取样坐标被亮度图 coerce 到边缘列；切页过程中 bounds
 * 又逐帧扫过整张壁纸 —— 自适应值只能等切完才稳定下来，观感上就是「切页后才自适应 / 切页时跳变」。
 *
 * 改为在页面坐标系里取矩形的意义：页面本身是全屏铺满且只在绘制阶段位移，页面坐标 == 页面停稳
 * 后卡片所在的屏幕位置。因此每张卡在**首次布局时**就拿到了最终值，切页全程不再变化。
 */
internal class GlassSamplingOrigin {
    var coordinates: LayoutCoordinates? by mutableStateOf(null)
}

internal val LocalGlassSamplingOrigin = staticCompositionLocalOf<GlassSamplingOrigin?> { null }

// ============================================================================
// 2026-09-26 恢复官方 AndroidLiquidGlass 架构（用户要求参照官方项目）：
// 每张卡片自己一层 drawBackdrop —— 官方 LazyScrollContainerContent.kt 逐字配方：
//   drawBackdrop(backdrop, RoundedCornerShape(32dp), effects = { vibrancy(); blur(3dp); lens(20dp, 36dp) })
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
    cornerRadius: Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tuningState = LocalLiquidGlassTuningState.current
    val darkTheme = isSystemInDarkTheme()
    val luminanceMapState = LocalBackdropLuminanceMap.current
    val colorScheme = MaterialTheme.colorScheme
    // 取样 + 亮度动画统一由 GlassAdaptiveState 提供（按钮/顶栏/弹窗背板共用同一套），
    // 保证同一片背景上所有玻璃面的反应一致。
    val adaptiveState = rememberGlassAdaptiveState()
    val sample = rememberGlassLuminanceSample(adaptiveState.tracker)
    val fallbackLuminance = LocalBackdropBackgroundLuminance.current
    val contentColorTarget = glassContentColor(
        rawLuminance = sample?.luminance ?: fallbackLuminance,
        tuning = LocalLiquidGlassTuning.current,
        darkTheme = darkTheme,
    )
    val contentColor by animateColorAsState(
        targetValue = contentColorTarget,
        animationSpec = tween(500),
        label = "liquidGlassContentColor",
    )
    val secondaryContentColor = contentColor.copy(alpha = 0.8f)

    MaterialTheme(
        colorScheme = colorScheme.copy(
            onSurface = contentColor,
            onSurfaceVariant = secondaryContentColor,
            onBackground = contentColor,
        ),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        motionScheme = MaterialTheme.motionScheme,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides contentColor,
            LocalLiquidSecondaryContentColor provides secondaryContentColor,
        ) {
            Column(
                modifier
                    .glassAdaptiveBounds(adaptiveState)
                    .drawBackdrop(
                        backdrop = LocalLiquidCardBackdrop.current,
                        shape = { RoundedCornerShape(cornerRadius ?: tuningState.value.cornerRadiusDp.dp) },
                        effects = {
                            val t = tuningState.value
                            // vibrancy 与亮度自适应合并成一次 colorControls：vibrancy() 内部
                            // 就是 colorControls(saturation = 1.5f) 的同一张色彩矩阵，
                            // 合并后少一趟 RenderEffect 采样，观感不变。
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
                    ),
                content = content
            )
        }
    }
}

@Composable
internal fun GlassPage(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 0f.dp,
) {
    val tuningState = LocalLiquidGlassTuningState.current
    val darkTheme = isSystemInDarkTheme()
    val adaptiveState = rememberGlassAdaptiveState()
    Box(
        modifier
            .glassAdaptiveBounds(adaptiveState)
            .drawBackdrop(
                backdrop = LocalLiquidCardBackdrop.current,
                shape = { RoundedCornerShape(cornerRadius) },
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
    )
}

