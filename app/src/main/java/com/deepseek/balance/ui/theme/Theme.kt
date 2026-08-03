package com.deepseek.balance.ui.theme

import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== 自定义后备配色方案（基于 DeepSeek 蓝 #3A5BDF） =====
// 含新版 Material3 surfaceContainer 分层 token（surfaceDim/Low/Lowest/High/Highest/Bright）
private val LightColors = lightColorScheme(
    primary = Color(0xFF3A5BDF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE1FF),
    onPrimaryContainer = Color(0xFF001556),
    secondary = Color(0xFF585E71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE2F9),
    onSecondaryContainer = Color(0xFF151B2C),
    tertiary = Color(0xFF735572),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD7F8),
    onTertiaryContainer = Color(0xFF2A132B),
    background = Color(0xFFFBF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFBF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE2E1EC),
    onSurfaceVariant = Color(0xFF45464F),
    surfaceDim = Color(0xFFDBD8E3),
    surfaceBright = Color(0xFFFBF8FF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F2FC),
    surfaceContainer = Color(0xFFEFECF6),
    surfaceContainerHigh = Color(0xFFE9E6F1),
    surfaceContainerHighest = Color(0xFFE3E1EB),
    outline = Color(0xFF767680),
    outlineVariant = Color(0xFFC6C5D0),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB5C4FF),
    onPrimary = Color(0xFF00206E),
    primaryContainer = Color(0xFF003BB5),
    onPrimaryContainer = Color(0xFFDCE1FF),
    secondary = Color(0xFFC0C6DD),
    onSecondary = Color(0xFF2A3043),
    secondaryContainer = Color(0xFF404659),
    onSecondaryContainer = Color(0xFFDCE2F9),
    tertiary = Color(0xFFD7B9D5),
    onTertiary = Color(0xFF3C2840),
    tertiaryContainer = Color(0xFF553E59),
    onTertiaryContainer = Color(0xFFFFD7F8),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE4E1E8),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE4E1E8),
    surfaceVariant = Color(0xFF2B2B33),
    onSurfaceVariant = Color(0xFFC6C5D0),
    surfaceDim = Color(0xFF131318),
    surfaceBright = Color(0xFF393840),
    surfaceContainerLowest = Color(0xFF0E0E13),
    surfaceContainerLow = Color(0xFF1B1B21),
    surfaceContainer = Color(0xFF1F1F26),
    surfaceContainerHigh = Color(0xFF2A2931),
    surfaceContainerHighest = Color(0xFF35343C),
    outline = Color(0xFF908F99),
    outlineVariant = Color(0xFF45464F),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// Material3 形状体系：小/中/大/特大圆角，组件统一引用 MaterialTheme.shapes
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),   // 状态条/小标签
    small = RoundedCornerShape(8.dp),        // 输入框/下拉框
    medium = RoundedCornerShape(12.dp),      // 中按钮/选中态
    large = RoundedCornerShape(16.dp),       // 卡片/对话框
    extraLarge = RoundedCornerShape(24.dp),  // 主卡片/大圆角卡片
)

private val AppTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

@Composable
fun DeepSeekBalanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        // Android 12+ 动态取色（跟随壁纸）
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        // 深色/浅色自定义方案
        darkTheme -> DarkColors
        else -> LightColors
    }

    // Material 3 Expressive：MaterialExpressiveTheme 自动启用 Expressive 组件形态（形状/动效）。
    // 动效用「快速版」MotionScheme：形状保持 Expressive 有机风格，
    // 但过渡动画用短时 tween 替代 Expressive 的长衰减弹簧（弹簧会让菜单收起动画拖很久，
    // 期间 Popup 仍拦截点击，导致下拉后要等动画结束才能再操作）
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        motionScheme = FastMotionScheme,
        content = content,
    )
}

/** 快速版 MotionScheme：保留 Expressive 形状语言，动效全部用短时 tween */
private object FastMotionScheme : MotionScheme {
    private fun <T> fast(): FiniteAnimationSpec<T> = tween(durationMillis = 120)
    private fun <T> normal(): FiniteAnimationSpec<T> = tween(durationMillis = 180)
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = normal()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = fast()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 260)
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = normal()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = fast()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(durationMillis = 260)
}
