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
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
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
                PullToRefreshDefaults.LoadingIndicator(
                    state = pullState,
                    isRefreshing = isLoading,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(scrollState),
            ) {
                Spacer(
                    modifier = Modifier.height(
                        WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                    )
                )

                TopBar(
                    onSettingsClick = onSettingsClick,
                )

                AnimatedVisibility(
                    visible = result != null,
                    enter = fadeIn(animationSpec = tween(300)),
                    exit = fadeOut(animationSpec = tween(200)),
                ) {
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

                Spacer(modifier = Modifier.height(24.dp))

                // 上次更新时间：文本区域始终保留（null 时显示占位文案），
                // 避免刷新完成后文本出现把下方卡片挤下移
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = lastQueryTime?.let { "上次更新 $it" } ?: "等待更新…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )

                // API Key 提示卡（未填写时显示，常驻不随刷新消失）
                if (!hasData) {
                    EmptyState(
                        hasKey = hasKey,
                        onSettingsClick = onSettingsClick,
                    )
                }

                // 用量明细（网页令牌鉴权）
                UsageSection(
                    usage = usage,
                    usageError = usageError,
                    hasWebToken = hasWebToken,
                    webTokenInvalid = webTokenInvalid,
                    onReLoginClick = onWebLoginClick,
                    isLoading = isLoading,
                    onSettingsClick = onSettingsClick,
                    loadRangeDaily = loadRangeDaily,
                    refreshCount = refreshCount,
                )

                if (errorMessage != null) {
                    ErrorCard(message = errorMessage)
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        }
    }
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
