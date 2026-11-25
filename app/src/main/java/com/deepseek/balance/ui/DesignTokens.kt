package com.deepseek.balance.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * UI 一致性基线对应的代码令牌（docs/ui-review/baseline.md v1）。
 * 命名只表达设计角色，不表达某个页面的偶然数值；新增取值需先更新基线。
 */
object UiSpacing {
    val Xs: Dp = 4.dp
    val Sm: Dp = 8.dp
    val Md: Dp = 12.dp
    val Lg: Dp = 16.dp
    val Xl: Dp = 20.dp
    val Xxl: Dp = 24.dp
    val Xxxl: Dp = 32.dp
}

object UiRadius {
    val Small: Dp = 12.dp
    val Option: Dp = 16.dp
    val Card: Dp = 24.dp
    val Dialog: Dp = 28.dp
    val Glass: Dp = 32.dp
}

object UiComponentSize {
    val TopBarHeight: Dp = 56.dp
    val BottomBarHeight: Dp = 64.dp
    val ActionButtonHeight: Dp = 52.dp
}