package com.deepseek.balance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.deepseek.balance.model.BalanceResponse

// ============================================================================
// MainScreen —— 仅负责「状态编排」。
// 所有视觉组件都在独立 .kt 文件中，各自编译为独立 class，
// Live Edit 改某个组件时只重编那一个 class，可直接热替换。
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    apiKey: String,
    onSettingsClick: () -> Unit,
    onQueryClick: () -> Unit,
    isLoading: Boolean,
    result: BalanceResponse?,
    errorMessage: String?,
    lastQueryTime: String?,
    alertEnabled: Boolean = false,
    alertThreshold: Double = 50.0,
    settingsVisible: Boolean = false,
) {
    val scrollState = rememberScrollState()
    val hasKey = apiKey.isNotBlank()
    val hasData = result != null

    val snackbarHostState = remember { SnackbarHostState() }
    var previousLoading by remember { mutableStateOf(isLoading) }
    LaunchedEffect(isLoading, settingsVisible) {
        if (!settingsVisible && previousLoading && !isLoading && hasData && errorMessage == null) {
            snackbarHostState.showSnackbar(
                message = "已显示最新数据",
                duration = SnackbarDuration.Short,
            )
        }
        previousLoading = isLoading
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(scrollState),
        ) {
            Spacer(
                modifier = Modifier.height(
                    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp,
                )
            )

            TopBar(
                isLoading = isLoading,
                hasData = hasData,
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
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            RefreshControls(
                hasData = hasData,
                hasKey = hasKey,
                isLoading = isLoading,
                onQueryClick = onQueryClick,
            )

            if (lastQueryTime != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "上次更新 $lastQueryTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            if (errorMessage != null) {
                ErrorCard(message = errorMessage)
            }

            if (!hasData && errorMessage == null && !isLoading) {
                EmptyState(hasKey = hasKey)
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

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
        )
    }
}
