package com.deepseek.balance.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.IntSize
import com.kyant.backdrop.highlight.Highlight
import kotlin.math.round

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

internal fun adaptiveContentColor(backgroundLuminance: Float): Color =
    if (backgroundLuminance > 0.5f) DarkContentReference else LightContentReference

internal fun adaptiveSecondaryContentColor(backgroundLuminance: Float): Color =
    adaptiveContentColor(backgroundLuminance).copy(alpha = 0.72f)

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
        )
    }
}

@Composable
internal fun rememberAdaptiveContentColor(luminanceTracker: GlassLuminanceTracker): Color {
    val sample = rememberGlassLuminanceSample(luminanceTracker)
    val fallbackLuminance = if (isSystemInDarkTheme()) 0.1f else 0.9f
    val target = adaptiveContentColor(sample?.luminance ?: fallbackLuminance)

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
    val fallbackLuminance = if (isSystemInDarkTheme()) 0.1f else 0.9f
    val luminance = backgroundLuminance ?: fallbackLuminance
    val primaryContentColor = animateColorAsState(
        targetValue = adaptiveContentColor(luminance),
        animationSpec = tween(500),
        label = "globalGlassContentColor",
    ).value
    val secondaryContentColor = animateColorAsState(
        targetValue = adaptiveSecondaryContentColor(luminance),
        animationSpec = tween(500),
        label = "globalGlassSecondaryColor",
    ).value
    val scheme = MaterialTheme.colorScheme
    val backgroundIsLight = luminance > 0.5f
    val themeIsDark = scheme.onSurface.luminance() > 0.5f
    val accentConflicts = backgroundIsLight == themeIsDark

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