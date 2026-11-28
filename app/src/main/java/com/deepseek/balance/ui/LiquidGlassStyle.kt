package com.deepseek.balance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

// 默认值 = 用户在真机上调定并确认的偏好（2026-11-28 固化）；「恢复默认」回到这一套
@Immutable
data class LiquidGlassTuning(
    val blurDp: Float = 4.405053f,
    val refractionHeightDp: Float = 11.653835f,
    val refractionAmountDp: Float = 18.431108f,
    val surfaceAlpha: Float = 0f,
    val chromaticAberration: Boolean = true,
    val cornerRadiusDp: Float = 48f,
    val depthEffect: Boolean = true,
    val vibrancy: Boolean = true,
    // 背景亮度自适应强度（0 = 关闭）：玻璃按自身位置的壁纸明暗自动调整提亮/对比/模糊
    val adaptiveLuminance: Float = 1f,
    // 背景亮度自适应总开关：关闭后完全不注入自适应效果（玻璃 + 文字都不再跟随背景）
    val adaptiveEnabled: Boolean = true,
    // 提亮起效亮度（0.55~0.95）：亮度高于此值才做轻微提亮兜底
    val adaptiveThreshold: Float = ADAPTIVE_DEFAULT_BRIGHT_THRESHOLD,
    // 文字/图标反转点（0.35~0.65）：亮度高于此值用深色字，否则用浅色字
    val textFlipThreshold: Float = ADAPTIVE_DEFAULT_TEXT_FLIP,
) {
    companion object {
        fun officialClear() = LiquidGlassTuning(
            blurDp = 0f,
            refractionHeightDp = 16f,
            refractionAmountDp = 32f,
            surfaceAlpha = 0f,
            chromaticAberration = false,
            depthEffect = true,
        )
    }
}

val LocalLiquidGlassTuning = compositionLocalOf { LiquidGlassTuning() }

// snapshot state 驱动版本：引用稳定（rememberUpdatedState 提供），组件只读 State 对象、
// 在 shape/effects/onDrawSurface lambda 内读 .value —— 调参时组件不重组、drawBackdrop 的
// shapeProvider 不被替换，从根上消除"旧圆角残影"（与官方 playground 同一驱动方式）。
val LocalLiquidGlassTuningState = compositionLocalOf<State<LiquidGlassTuning>> { mutableStateOf(LiquidGlassTuning()) }

// 背景亮度图：由根层 AppBackground 生成（环境光斑解析计算 / 自定义壁纸缩略采样），
// 玻璃组件按自身屏幕矩形查询后驱动内容色与表面蒙层。null = 尚未就绪，组件保持原有静态外观。
internal val LocalBackdropLuminanceMap = compositionLocalOf<State<BackdropLuminanceMap?>> { mutableStateOf(null) }

@Composable
internal fun liquidGlassSurfaceColor(): Color {
    val tuning = LocalLiquidGlassTuning.current
    val alpha = if (isSystemInDarkTheme()) tuning.surfaceAlpha * 2f / 3f else tuning.surfaceAlpha
    return Color.White.copy(alpha = alpha)
}

internal fun liquidGlassSurfaceColorOf(tuning: LiquidGlassTuning, darkTheme: Boolean): Color {
    val alpha = if (darkTheme) tuning.surfaceAlpha * 2f / 3f else tuning.surfaceAlpha
    return Color.White.copy(alpha = alpha)
}

internal fun liquidGlassHighlight(): Highlight = Highlight.Ambient.copy(alpha = 0.12f)
internal const val DARK_CONTENT_REFERENCE_ARGB = 0xFF1B1B21
internal const val LIGHT_CONTENT_REFERENCE_ARGB = 0xFFE4E1E8

private val DarkContentReference = Color(DARK_CONTENT_REFERENCE_ARGB)
private val LightContentReference = Color(LIGHT_CONTENT_REFERENCE_ARGB)

internal const val LIGHT_ACCENT_PRIMARY_ARGB = 0xFF3A5BDF
internal const val LIGHT_ACCENT_ON_PRIMARY_ARGB = 0xFFFFFFFF
internal const val DARK_ACCENT_PRIMARY_ARGB = 0xFFB5C4FF
internal const val DARK_ACCENT_ON_PRIMARY_ARGB = 0xFF00206E
internal const val LIGHT_ACCENT_ERROR_ARGB = 0xFFBA1A1A
internal const val LIGHT_ACCENT_ON_ERROR_ARGB = 0xFFFFFFFF
internal const val DARK_ACCENT_ERROR_ARGB = 0xFFFFB4AB
internal const val DARK_ACCENT_ON_ERROR_ARGB = 0xFF690005

private val LightAccentPrimary = Color(LIGHT_ACCENT_PRIMARY_ARGB)
private val LightAccentOnPrimary = Color(LIGHT_ACCENT_ON_PRIMARY_ARGB)
private val DarkAccentPrimary = Color(DARK_ACCENT_PRIMARY_ARGB)
private val DarkAccentOnPrimary = Color(DARK_ACCENT_ON_PRIMARY_ARGB)
private val LightAccentError = Color(LIGHT_ACCENT_ERROR_ARGB)
private val LightAccentOnError = Color(LIGHT_ACCENT_ON_ERROR_ARGB)
private val DarkAccentError = Color(DARK_ACCENT_ERROR_ARGB)
private val DarkAccentOnError = Color(DARK_ACCENT_ON_ERROR_ARGB)

internal val LocalBackdropBackgroundLuminance = compositionLocalOf { 0.9f }

/**
 * 文字/图标色判定：按「玻璃之后的实际背景亮度」选色，而不是壁纸原始亮度。
 *
 * 两步：
 * 1. 先把壁纸亮度换算成实际观感亮度——加上玻璃提亮（colorControls.brightness），
 *    再叠上白色蒙层（surfaceAlpha）。否则玻璃提亮后该用深字的地方会被判成浅字。
 * 2. 以用户设定的反转点为首选，再按 WCAG 对比度兜底：首选色对比度不足 3:1 时切到另一种。
 */
internal fun glassContentColor(
    rawLuminance: Float,
    tuning: LiquidGlassTuning,
    darkTheme: Boolean,
): Color {
    if (!tuning.adaptiveEnabled) return themeContentColor(darkTheme)
    val brightness = adaptiveGlassResponse(
        luminance = rawLuminance,
        detail = 1f, // 提亮与细节量无关，取 1 不影响结果
        brightnessStrength = tuning.adaptiveLuminance,
        brightThreshold = tuning.adaptiveThreshold,
        baseBlurDp = tuning.blurDp,
    ).brightness
    val surfaceAlpha = if (darkTheme) tuning.surfaceAlpha * 2f / 3f else tuning.surfaceAlpha
    val backdrop = effectiveBackdropLuminance(rawLuminance, brightness, surfaceAlpha)
    return readableContentColor(backdrop, tuning.textFlipThreshold)
}

/** 玻璃之后观众实际看到的背景亮度：提亮 → 白色蒙层合成 */
internal fun effectiveBackdropLuminance(
    rawLuminance: Float,
    brightness: Float,
    surfaceAlpha: Float,
): Float {
    val brightened = (rawLuminance + brightness).coerceIn(0f, 1f)
    return (brightened + (1f - brightened) * surfaceAlpha).coerceIn(0f, 1f)
}

/** 按对比度选深/浅字：首选由反转点决定，对比度不足 3:1 时反向 */
internal fun readableContentColor(backdropLuminance: Float, flipThreshold: Float): Color {
    val preferDark = backdropLuminance > flipThreshold
    val primary = if (preferDark) DarkContentReference else LightContentReference
    val alternative = if (preferDark) LightContentReference else DarkContentReference
    val primaryContrast = contrastRatio(primary.luminance(), backdropLuminance)
    if (primaryContrast >= MIN_CONTENT_CONTRAST) return primary
    val alternativeContrast = contrastRatio(alternative.luminance(), backdropLuminance)
    return if (alternativeContrast > primaryContrast) alternative else primary
}

private fun contrastRatio(foreground: Float, background: Float): Float {
    val lighter = max(foreground, background)
    val darker = min(foreground, background)
    return (lighter + 0.05f) / (darker + 0.05f)
}

// 小字号文本的可读性下限（WCAG AA 大字号标准；小字标准 4.5 会过于激进）
private const val MIN_CONTENT_CONTRAST = 3f

internal fun adaptiveContentColor(
    backgroundLuminance: Float,
    flipThreshold: Float = ADAPTIVE_DEFAULT_TEXT_FLIP,
): Color = if (backgroundLuminance > flipThreshold) DarkContentReference else LightContentReference

/** 关闭亮度自适应时使用的固定内容色：跟随应用主题，不跟随背景 */
internal fun themeContentColor(darkTheme: Boolean): Color =
    if (darkTheme) LightContentReference else DarkContentReference

internal fun adaptiveSecondaryContentColor(
    backgroundLuminance: Float,
    flipThreshold: Float = ADAPTIVE_DEFAULT_TEXT_FLIP,
): Color = adaptiveContentColor(backgroundLuminance, flipThreshold).copy(alpha = 0.72f)

// ============================================================================
// 背景亮度自适应（参考官方 AdaptiveLuminanceGlass）：由壁纸亮度图驱动玻璃本体的
// colorControls 与模糊，而不是像官方 demo 那样每帧回读玻璃自身渲染结果（GPU→CPU，
// 本项目同屏 4 张卡 + RenderThread 已是瓶颈，不可照搬）。
// 与官方配方同构：先做 sign(n)·n² 的对比强化，亮侧「洗白」（提亮 + 降对比 + 加模糊），
// 暗侧只轻微压暗（不加亮，保留深色壁纸下的通透感）。
//
// 模糊用「比例缩放」而不是加减：滑块值仍是唯一基准（拉 0 = 完全不糊），自适应只在该
// 基准上放大/缩小。曲线形状与官方 demo 一致（官方是亮 8→16dp ×2、暗 8→2dp ×0.25）。
// ============================================================================

internal data class AdaptiveGlassResponse(
    val brightness: Float,
    val contrast: Float,
    /** 最终模糊半径（dp）：自适应开启时由细节量在绝对区间内取值，关闭时等于滑块值 */
    val blurDp: Float,
)

private val NeutralGlassResponse = AdaptiveGlassResponse(0f, 1f, 0f)

/**
 * 由亮度与细节量计算玻璃响应。
 *
 * 策略（"可读性优先、质感克制"）：
 * - 模糊：**背景模糊滑块值 × 细节量**。滑块即"最杂乱背景的模糊上限"，纯色/柔和背景自动趋近 0。
 *   模糊不改变平均亮度，是"效果 ÷ 跳变风险"最高的参数。
 * - 提亮：只在极亮背景（> 提亮起效亮度，默认 0.70）出动，最多 +0.08，仅作浅背景兜底。
 * - 对比度恒 1（不改）：它是"洗白感"和"多卡不一致"的主要来源。
 * - 暗侧完全不响应，保留深色壁纸下的通透。
 *
 * 本函数必须连续（调用方对亮度/细节做动画过渡），不能阶梯量化。
 */
internal fun adaptiveGlassResponse(
    luminance: Float?,
    detail: Float,
    brightnessStrength: Float,
    brightThreshold: Float,
    baseBlurDp: Float,
): AdaptiveGlassResponse {
    if (luminance == null) return NeutralGlassResponse.copy(blurDp = baseBlurDp)
    val k = brightnessStrength.coerceIn(0f, 1f)
    val brightness = if (luminance > brightThreshold) {
        val n = ((luminance - brightThreshold) / (1f - brightThreshold)).coerceIn(0f, 1f)
        // 开平方让中亮区也有可见响应（n² 会让 0.7~0.8 几乎没变化）
        ADAPTIVE_MAX_BRIGHTNESS * sqrt(n) * k
    } else {
        0f
    }
    // 模糊走绝对区间（与官方 demo 同构）：纯色背景 ADAPTIVE_BLUR_MIN_DP，最杂乱 ADAPTIVE_BLUR_MAX_DP
    val blurDp = ADAPTIVE_BLUR_MIN_DP +
        (ADAPTIVE_BLUR_MAX_DP - ADAPTIVE_BLUR_MIN_DP) * detail.coerceIn(0f, 1f)
    return AdaptiveGlassResponse(
        brightness = brightness,
        contrast = 1f,
        blurDp = blurDp,
    )
}

internal const val ADAPTIVE_MIN_BRIGHT_THRESHOLD = 0.55f
internal const val ADAPTIVE_MAX_BRIGHT_THRESHOLD = 0.95f
internal const val ADAPTIVE_DEFAULT_BRIGHT_THRESHOLD = 0.70f
internal const val ADAPTIVE_MIN_TEXT_FLIP = 0.35f
internal const val ADAPTIVE_MAX_TEXT_FLIP = 0.65f
internal const val ADAPTIVE_DEFAULT_TEXT_FLIP = 0.50f

private const val ADAPTIVE_MAX_BRIGHTNESS = 0.25f
// 自适应模糊的绝对区间（dp）：与官方 demo 同构（官方 2~16dp），本项目按性能收紧上限
internal const val ADAPTIVE_BLUR_MIN_DP = 2f
internal const val ADAPTIVE_BLUR_MAX_DP = 10f

// ============================================================================
// 玻璃自适应状态：卡片 / 按钮 / 弹窗背板 / 顶栏共用同一套取样与亮度动画，
// 保证同一片背景上所有玻璃面（提亮、对比、模糊倍率）反应一致。
// ============================================================================

internal class GlassAdaptiveState internal constructor(
    internal val samplingOrigin: GlassSamplingOrigin?,
    initialLuminance: Float,
) {
    internal val tracker = GlassLuminanceTracker()
    internal val luminance = Animatable(initialLuminance)
    // 细节量初值取 1（= 用满滑块模糊）：取样矩形在页面切换/弹窗进出时可能短暂失效，
    // 若从 0 起步会表现为"模糊突然消失，等下一次布局才回来"。
    internal val detail = Animatable(1f)
    internal var initialized = false
    internal var detailInitialized = false
}

@Composable
internal fun rememberGlassAdaptiveState(): GlassAdaptiveState {
    val origin = LocalGlassSamplingOrigin.current
    // 首帧兜底用「页面平均亮度」，避免从极端的 0.9/0.1 起步再回弹
    val pageLuminance = LocalBackdropBackgroundLuminance.current
    val state = remember { GlassAdaptiveState(origin, pageLuminance) }
    val sample = rememberGlassLuminanceSample(state.tracker)
    LaunchedEffect(sample?.luminance) {
        val target = sample?.luminance ?: return@LaunchedEffect
        if (!state.initialized) {
            // 第一次拿到真实取样直接落位（新出现的组件不该先按兜底值画一帧再过渡）
            state.luminance.snapTo(target)
            state.initialized = true
        } else {
            state.luminance.animateTo(target, tween(durationMillis = 400))
        }
    }
    LaunchedEffect(sample?.detail) {
        val target = sample?.detail ?: return@LaunchedEffect
        state.detailInitialized = true
        state.detail.animateTo(target, tween(durationMillis = 400))
    }
    return state
}

/** 挂到玻璃组件本体上：按页面坐标记录取样矩形（排除整页滑动位移）。 */
internal fun Modifier.glassAdaptiveBounds(state: GlassAdaptiveState): Modifier =
    onGloballyPositioned { coordinates ->
        val origin = state.samplingOrigin?.coordinates
        state.tracker.bounds = origin?.localBoundingBoxOf(coordinates)
            ?: coordinates.boundsInRoot()
    }

/** 当前亮度/细节量对应的玻璃响应；只在绘制阶段调用（读动画值不触发重组）。 */
internal fun GlassAdaptiveState.response(tuning: LiquidGlassTuning): AdaptiveGlassResponse =
    if (!tuning.adaptiveEnabled) {
        // 关闭自适应：模糊完全由「背景模糊」滑块决定
        NeutralGlassResponse.copy(blurDp = tuning.blurDp)
    } else {
        adaptiveGlassResponse(
            luminance = luminance.value,
            // 尚未取到有效细节量时取中值，避免"模糊整个消失"
            detail = if (detailInitialized) detail.value else 0.5f,
            brightnessStrength = tuning.adaptiveLuminance,
            brightThreshold = tuning.adaptiveThreshold,
            baseBlurDp = tuning.blurDp,
        )
    }

internal class GlassLuminanceTracker {
    var bounds by mutableStateOf<Rect?>(null)
}

@Composable
internal fun rememberGlassLuminanceSample(luminanceTracker: GlassLuminanceTracker): LuminanceSample? {
    val map = LocalBackdropLuminanceMap.current.value
    val windowSize = LocalWindowInfo.current.containerSize
    return remember(luminanceTracker.bounds, map, windowSize) {
        val bounds = luminanceTracker.bounds ?: return@remember null
        if (map == null || windowSize.width <= 0 || windowSize.height <= 0) return@remember null
        val width = windowSize.width.toFloat()
        val height = windowSize.height.toFloat()
        val raw = map.sample(
            leftFraction = bounds.left / width,
            topFraction = bounds.top / height,
            rightFraction = bounds.right / width,
            bottomFraction = bounds.bottom / height,
        )
        LuminanceSample(
            luminance = round(raw.luminance * 10f) / 10f,
            chroma = round(raw.chroma * 10f) / 10f,
            detail = round(raw.detail * 10f) / 10f,
        )
    }
}

@Composable
internal fun rememberAdaptiveContentColor(luminanceTracker: GlassLuminanceTracker): Color {
    val sample = rememberGlassLuminanceSample(luminanceTracker)
    val tuning = LocalLiquidGlassTuning.current
    val target = glassContentColor(
        rawLuminance = sample?.luminance ?: LocalBackdropBackgroundLuminance.current,
        tuning = tuning,
        darkTheme = isSystemInDarkTheme(),
    )

    return animateColorAsState(
        targetValue = target,
        animationSpec = tween(500),
        label = "liquidGlassContentColor",
    ).value
}

internal val LocalLiquidSecondaryContentColor = compositionLocalOf<Color?> { null }

@Composable
internal fun liquidSecondaryContentColor(): Color =
    LocalLiquidSecondaryContentColor.current ?: MaterialTheme.colorScheme.onSurfaceVariant

@Composable
internal fun GlobalAdaptiveGlassTheme(
    backgroundLuminance: Float?,
    content: @Composable () -> Unit,
) {
    val fallbackLuminance = LocalBackdropBackgroundLuminance.current
    val tuning = LocalLiquidGlassTuning.current
    val luminance = backgroundLuminance ?: fallbackLuminance
    val primaryTarget = glassContentColor(luminance, tuning, isSystemInDarkTheme())
    val secondaryTarget = primaryTarget.copy(alpha = 0.8f)
    val primaryContentColor = animateColorAsState(
        targetValue = primaryTarget,
        animationSpec = tween(500),
        label = "globalGlassContentColor",
    ).value
    val secondaryContentColor = animateColorAsState(
        targetValue = secondaryTarget,
        animationSpec = tween(500),
        label = "globalGlassSecondaryColor",
    ).value
    val scheme = MaterialTheme.colorScheme
    val backgroundIsLight = luminance > tuning.textFlipThreshold
    val themeIsDark = scheme.onSurface.luminance() > 0.5f
    val accentConflicts = tuning.adaptiveEnabled && backgroundIsLight == themeIsDark

    val adaptivePrimary = when {
        !accentConflicts -> scheme.primary
        backgroundIsLight -> LightAccentPrimary
        else -> DarkAccentPrimary
    }
    val adaptiveOnPrimary = when {
        !accentConflicts -> scheme.onPrimary
        backgroundIsLight -> LightAccentOnPrimary
        else -> DarkAccentOnPrimary
    }
    val adaptiveError = when {
        !accentConflicts -> scheme.error
        backgroundIsLight -> LightAccentError
        else -> DarkAccentError
    }
    val adaptiveOnError = when {
        !accentConflicts -> scheme.onError
        backgroundIsLight -> LightAccentOnError
        else -> DarkAccentOnError
    }
    MaterialTheme(
        colorScheme = scheme.copy(
            onSurface = primaryContentColor,
            onBackground = primaryContentColor,
            onSurfaceVariant = secondaryContentColor,
            primary = adaptivePrimary,
            onPrimary = adaptiveOnPrimary,
            error = adaptiveError,
            onError = adaptiveOnError,
        ),
        shapes = MaterialTheme.shapes,
        typography = MaterialTheme.typography,
        motionScheme = MaterialTheme.motionScheme,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides primaryContentColor,
            LocalLiquidSecondaryContentColor provides secondaryContentColor,
            LocalBackdropBackgroundLuminance provides luminance,
        ) {
            content()
        }
    }
}