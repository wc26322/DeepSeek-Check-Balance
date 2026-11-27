package com.deepseek.balance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalOverscrollConfiguration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepseek.balance.model.BalanceResponse
import com.deepseek.balance.model.UsageData
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ============================================================================
// MainScreen —— 仅负责「状态编排」。
// 所有视觉组件都在独立 .kt 文件中，各自编译为独立 class，
// Live Edit 改某个组件时只重编那一个 class，可直接热替换。
// ============================================================================

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun MainScreen(
    apiKey: String,
    onSettingsClick: () -> Unit,
    onQueryClick: () -> Unit,
    isLoading: Boolean,
    result: BalanceResponse?,
    errorMessage: String?,
    lastQueryTime: String?,
    usage: UsageData?,
    usageError: String?,
    hasWebToken: Boolean,
    webTokenInvalid: Boolean = false,
    onWebLoginClick: () -> Unit = {},
    loadRangeDaily: suspend (start: java.time.LocalDate, end: java.time.LocalDate) -> List<com.deepseek.balance.model.ModelDailyUsage>? =
        { _, _ -> null },
    alertEnabled: Boolean = false,
    alertThreshold: Double = 50.0,
    settingsVisible: Boolean = false,
    refreshCount: Int = 0,
    dataUpdatedTick: Int = 0,
) {
    val scrollState = rememberScrollState()
    val hasKey = apiKey.isNotBlank()
    val hasData = result != null || usage != null

    // 下拉刷新状态（供 MD3 风格指示器读取下拉进度）
    val pullState = rememberPullToRefreshState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 数据一更新成功（dataUpdatedTick 变化）就弹提示，不等刷新指示器收回
    LaunchedEffect(dataUpdatedTick) {
        if (dataUpdatedTick > 0 && !settingsVisible) {
            // showSnackbar 会挂起等待显示结束（Short=4s 超时），
            // 放独立协程，主协程延迟 1.5s 后主动 dismiss 以缩短显示时长
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = "已显示最新数据",
                    duration = SnackbarDuration.Short,
                )
            }
            delay(1500)
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    // 余额数据（从 result 取出，下传给纯展示部件）
    val cnyInfo = result?.balanceInfos?.find { it.currency == "CNY" }
    val symbol = cnyInfo?.symbol ?: "¥"
    val totalBalance = cnyInfo?.totalBalance ?: "0.00"
    val grantedBalance = cnyInfo?.grantedBalance ?: ""
    val toppedUpBalance = cnyInfo?.toppedUpBalance ?: ""
    val totalAmount = totalBalance.toDoubleOrNull() ?: 0.0

    val (statusText, statusColor) = when {
        result?.isAvailable != true -> "余额不足" to Color(0xFFFF6666)
        alertEnabled && totalAmount < alertThreshold -> "低于预警线" to Color(0xFFFFAA00)
        else -> "可用" to Color(0xFF66FF88)
    }

    Scaffold(
        snackbarHost = {
            // 最新 MD3（Expressive）风格 Snackbar：大圆角 + 动态取色（跟随壁纸的浅色容器）
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    // 底部悬浮标签栏占据下方约 80dp，提示条上移避免被遮
                    modifier = Modifier.padding(bottom = 88.dp),
                )
            }
        },
        // 背景透明：透出录制层内的环境彩色光斑（玻璃效果的色彩来源）
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
        // 官方 AndroidLiquidGlass 架构：没有"整页玻璃底板"，背景（光斑壁纸）直接透出，
        // 每张卡片自己 drawBackdrop 折射壁纸层（见 LiquidCard.kt）
        // TopBar 同样改为折射壁纸层（LocalLiquidCardBackdrop），不再 layerBackdrop 录制滚动内容：
        // 录制滚动内容 = 滚动期间每帧重录整页纹理，是 RenderThread 的额外开销（2026-11 性能实测后调整）
        // 禁用系统 overscroll：顶部下拉由 PullToRefreshBox 独占（无回弹）；下拉刷新手势与系统回弹在系统层面冲突，故整体关闭
        CompositionLocalProvider(LocalOverscrollConfiguration provides null) {
        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onQueryClick,
            state = pullState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            indicator = {
                // Expressive 官方 LoadingIndicator：下拉时出现箭头 + 容器，松手后转圈，自带位移/缩放动画
                // 默认顶对齐会落在悬浮玻璃顶栏（8dp 间距 + 56dp 栏高）覆盖区内，
                // 下拉时指示器图像被「余额」栏遮挡；整体下移 72dp（栏底 64dp + 8dp 间隙）让其完整露出，
                // 同时把悬停待加载的位置一并下移到栏下方
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = isLoading,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 72.dp),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("home:content")
                    .padding(horizontal = UiSpacing.Xxl)
                    .verticalScroll(scrollState),
            ) {
                // 顶部让出悬浮玻璃栏（8 顶距 + 56 栏高 + 8 间距），初始时余额卡片不被遮挡
                Spacer(modifier = Modifier.height(72.dp))

                // 页面统一拥有卡片间距；辅助更新时间使用独立 8dp 间距，不与卡片间距叠加
                Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Lg)) {
                    if (result != null) {
                        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.Sm)) {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(animationSpec = tween(300)),
                                exit = fadeOut(animationSpec = tween(200)),
                            ) {
                                Box(modifier = Modifier.testTag("home:balanceCard")) {
                                    BalanceCard(
                                        symbol = symbol,
                                        totalBalance = totalBalance,
                                        statusText = statusText,
                                        statusColor = statusColor,
                                        grantedBalance = grantedBalance,
                                        toppedUpBalance = toppedUpBalance,
                                        totalCostCny = usage?.totalCostCny,
                                        refreshCount = refreshCount,
                                    )
                                }
                            }
                            LastUpdateText(lastQueryTime)
                        }
                    } else {
                        LastUpdateText(lastQueryTime)
                    }

                    // API Key 提示卡（未填写时显示，常驻不随刷新消失）
                    if (!hasData) {
                        Box(modifier = Modifier.testTag("home:emptyState")) {
                            EmptyState(
                                hasCredential = hasKey || hasWebToken,
                                onWebLoginClick = onWebLoginClick,
                                onSettingsClick = onSettingsClick,
                            )
                        }
                    }

                    // 用量明细（网页令牌鉴权）
                    Box(modifier = Modifier.testTag("home:usageSection")) {
                        UsageSection(
                            usage = usage,
                            usageError = usageError,
                            hasWebToken = hasWebToken,
                            webTokenInvalid = webTokenInvalid,
                            showNoTokenHint = hasKey && !hasWebToken,
                            onReLoginClick = onWebLoginClick,
                            isLoading = isLoading,
                            onSettingsClick = onSettingsClick,
                            loadRangeDaily = loadRangeDaily,
                            refreshCount = refreshCount,
                        )
                    }

                    if (errorMessage != null) {
                        Box(modifier = Modifier.testTag("home:errorCard")) {
                            ErrorCard(message = errorMessage)
                        }
                    }
                }

                // 尾部留白：让最后一张卡能完整滚出底部悬浮标签栏（栏 64 + 底距 16 + 间隙 16）
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
        }

        // 悬浮玻璃栏：声明在 PullToRefreshBox 之后 → z 序在上，滚动内容从其下方穿过
        TopBar(
            backdrop = LocalLiquidCardBackdrop.current,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding() // edge-to-edge：避开透明状态栏
                .padding(top = 8.dp)
                .padding(horizontal = 16.dp)
                .fillMaxWidth(),
        )
        }
    }
}

@Composable
private fun LastUpdateText(lastQueryTime: String?) {
    Text(
        text = lastQueryTime?.let { "上次更新 $it" } ?: "等待更新…",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().testTag("home:lastUpdate"),
        textAlign = TextAlign.Center,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun MainScreenPreview() {
    MaterialTheme {
        MainScreen(
            apiKey = "sk-test",
            onSettingsClick = {},
            onQueryClick = {},
            isLoading = false,
            result = null,
            errorMessage = null,
            lastQueryTime = null,
            usage = null,
            usageError = null,
            hasWebToken = false,
        )
    }
}
