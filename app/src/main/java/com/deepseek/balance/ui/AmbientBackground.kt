package com.deepseek.balance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

internal const val AMBIENT_DARK_ALPHA_SCALE = 0.65f

// 环境彩色光斑背景：给液态玻璃提供可折射的色彩（demo Playground 同思路）。
// 必须放在 layerBackdrop 录制层内部，玻璃栏才能模糊/折射到它。
// 径向渐变自然过渡到透明，全版本可用（不依赖 API 31+ 的 blur）。
//
// 光斑参数抽成 AmbientBackgroundSpec：绘制与亮度图共用同一份数值，
// 保证"自适应计算看到的光斑"与"实际画出来的光斑"不会漂移。
@Composable
internal fun rememberAmbientBackgroundSpec(): AmbientBackgroundSpec {
    val colorScheme = MaterialTheme.colorScheme
    val darkTheme = isSystemInDarkTheme()
    return remember(
        colorScheme.primary,
        colorScheme.tertiary,
        colorScheme.secondary,
        colorScheme.background,
        colorScheme.surface,
        darkTheme,
    ) {
        val background = colorScheme.background
        val surface = colorScheme.surface
        val baseAlpha = if (darkTheme) AMBIENT_DARK_ALPHA_SCALE else 1f
        AmbientBackgroundSpec(
            baseColor = surface.toRgb(),
            blobs = listOf(
                AmbientBlobSpec(
                    centerXFraction = 0.12f,
                    centerYFraction = 0.12f,
                    radiusFraction = 0.50f,
                    color = lerp(colorScheme.primary, background, 0.3f).toRgb(),
                    alpha = 0.45f * baseAlpha,
                ),
                AmbientBlobSpec(
                    centerXFraction = 0.95f,
                    centerYFraction = 0.50f,
                    radiusFraction = 0.48f,
                    color = lerp(colorScheme.tertiary, background, 0.3f).toRgb(),
                    alpha = 0.38f * baseAlpha,
                ),
                AmbientBlobSpec(
                    centerXFraction = 0.25f,
                    centerYFraction = 0.88f,
                    radiusFraction = 0.50f,
                    color = lerp(colorScheme.secondary, background, 0.3f).toRgb(),
                    alpha = 0.32f * baseAlpha,
                ),
            ),
        )
    }
}

@Composable
internal fun AmbientBackground(modifier: Modifier = Modifier) {
    val spec = rememberAmbientBackgroundSpec()
    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                // 实色底：保证玻璃栏（尤其胶囊）停在纯背景区时折射到的是不透明表面，
                // 而非透明（否则会“透空/显重影”）。与卡片同为 surface，
                // 胶囊不论停在背景还是卡片上观感一致。光斑仍画在其上，保留环境光质感。
                drawRect(Color(spec.baseColor.red, spec.baseColor.green, spec.baseColor.blue))

                val width = size.width
                val height = size.height
                for (blob in spec.blobs) {
                    val center = Offset(blob.centerXFraction * width, blob.centerYFraction * height)
                    val radius = blob.radiusFraction * width
                    val color = Color(blob.color.red, blob.color.green, blob.color.blue)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = blob.alpha),
                                color.copy(alpha = 0f),
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
    )
}