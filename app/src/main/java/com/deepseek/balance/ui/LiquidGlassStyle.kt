package com.deepseek.balance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import com.kyant.backdrop.highlight.Highlight

@Immutable
data class LiquidGlassTuning(
    val blurDp: Float = 2f,
    val refractionHeightDp: Float = 12f,
    val refractionAmountDp: Float = 24f,
    val surfaceAlpha: Float = 0.06f,
    val chromaticAberration: Boolean = true,
    val cornerRadiusDp: Float = 32f,
    val depthEffect: Boolean = false,
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

@Composable
internal fun liquidGlassSurfaceColor(): Color {
    val tuning = LocalLiquidGlassTuning.current
    val alpha = if (isSystemInDarkTheme()) tuning.surfaceAlpha * 2f / 3f else tuning.surfaceAlpha
    return Color.White.copy(alpha = alpha)
}

internal fun liquidGlassHighlight(): Highlight = Highlight.Ambient.copy(alpha = 0.12f)