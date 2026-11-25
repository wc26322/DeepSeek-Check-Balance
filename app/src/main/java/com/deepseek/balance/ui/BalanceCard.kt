package com.deepseek.balance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BalanceCard(
    symbol: String,
    totalBalance: String,
    statusText: String,
    statusColor: Color,
    grantedBalance: String,
    toppedUpBalance: String,
    totalCostCny: Double? = null,
    refreshCount: Int = 0,
) {
    val totalAmount = totalBalance.toDoubleOrNull() ?: 0.0
    val grantedAmount = grantedBalance.toDoubleOrNull() ?: 0.0
    val contentColor = MaterialTheme.colorScheme.onSurface

    // 外部间距由 MainScreen 统一控制，卡片自身不再携带外部 Spacer
    LiquidCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .testTag("home:balanceCard:content")
                .padding(UiSpacing.Xl),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().testTag("home:balanceCard:first"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "账户总余额",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.7f),
                    letterSpacing = 1.sp,
                )
                StatusChip(
                    text = statusText,
                    color = statusColor,
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.12f),
                    labelColor = contentColor,
                )
            }

            Spacer(modifier = Modifier.height(UiSpacing.Sm))

            // 金额滚动：刷新后从 0 过渡到当前值
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = contentColor,
                )
                AnimatedAmount(
                    target = totalAmount,
                    restartKey = refreshCount,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp,
                        fontFeatureSettings = "tnum",
                    ),
                    color = contentColor,
                )
            }

            if (grantedBalance.isNotBlank()) {
                Spacer(modifier = Modifier.height(UiSpacing.Xl))
                Row(
                    modifier = Modifier.fillMaxWidth().testTag("home:balanceCard:stats"),
                    horizontalArrangement = Arrangement.spacedBy(64.dp),
                ) {
                    BalanceLine(
                        label = "赠金",
                        symbol = symbol,
                        amountTarget = grantedAmount,
                        restartKey = refreshCount,
                        color = contentColor.copy(alpha = 0.85f),
                    )
                    // 总消费：来自用量接口（累计消费），复用 BalanceLine 保证与赠金完全对齐
                    if (totalCostCny != null) {
                        BalanceLine(
                            label = "总消费",
                            symbol = symbol,
                            amountTarget = totalCostCny,
                            restartKey = refreshCount,
                            color = contentColor.copy(alpha = 0.85f),
                        )
                    } else {
                        Column {
                            Text(
                                text = "总消费",
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.7f),
                            )
                            Text(
                                text = "--",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontFeatureSettings = "tnum",
                                ),
                                color = contentColor.copy(alpha = 0.85f),
                            )
                        }
                    }
                }
            }

        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BalanceCardPreview() {
    MaterialTheme {
        BalanceCard(
            symbol = "¥",
            totalBalance = "110.00",
            statusText = "可用",
            statusColor = Color(0xFF66FF88),
            grantedBalance = "10.00",
            toppedUpBalance = "100.00",
        )
    }
}
