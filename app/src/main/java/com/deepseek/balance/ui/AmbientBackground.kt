package com.deepseek.balance.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

// 环境彩色光斑背景：给液态玻璃提供可折射的色彩（demo Playground 同思路）。
// 必须放在 layerBackdrop 录制层内部，玻璃栏才能模糊/折射到它。
// 径向渐变自然过渡到透明，全版本可用（不依赖 API 31+ 的 blur）。
@Composable
internal fun AmbientBackground(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val surface = MaterialTheme.colorScheme.surface
    // 光斑颜色向背景色混 30%：给玻璃卡片提供肉眼可见的折射源。
    // 官方 demo 玻璃效果炸裂是因为背后是花哨壁纸；之前混 65% + alpha 0.1x 的"静音版"
    // 背景近乎纯色，玻璃没有东西可折 → 卡片看起来和普通白卡没差别。
    val primary = lerp(MaterialTheme.colorScheme.primary, background, 0.3f)
    val tertiary = lerp(MaterialTheme.colorScheme.tertiary, background, 0.3f)
    val secondary = lerp(MaterialTheme.colorScheme.secondary, background, 0.3f)
    // 深色模式下光斑压暗，保持暗色氛围
    val baseAlpha = if (isSystemInDarkTheme()) 0.65f else 1f

    Spacer(
        modifier
            .fillMaxSize()
            .drawBehind {
                // 实色底：保证玻璃栏（尤其胶囊）停在纯背景区时折射到的是不透明表面，
                // 而非透明（否则会“透空/显重影”）。与卡片同为 surface，
                // 胶囊不论停在背景还是卡片上观感一致。光斑仍画在其上，保留环境光质感。
                drawRect(surface)

                fun blob(center: Offset, radius: Float, color: Color, alpha: Float) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                color.copy(alpha = alpha),
                                color.copy(alpha = 0f),
                            ),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }

                val w = size.width
                val h = size.height
                // 三个光斑全部避开顶栏/底栏所在的高度带（上下各 ~12%）：
                // 色散会把光斑渐变拆成彩虹边，玻璃（尤其胶囊）停在纯背景上时，
                // 下方必须没有渐变才不会显彩虹；颜色集中在卡片经过的中段
                blob(Offset(w * 0.12f, h * 0.30f), w * 0.45f, primary, 0.45f * baseAlpha)
                blob(Offset(w * 0.95f, h * 0.52f), w * 0.45f, tertiary, 0.38f * baseAlpha)
                blob(Offset(w * 0.25f, h * 0.72f), w * 0.38f, secondary, 0.32f * baseAlpha)
            }
    )
}
