package com.deepseek.balance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
    Spacer(modifier = Modifier.height(8.dp))

    val totalAmount = totalBalance.toDoubleOrNull() ?: 0.0
    val grantedAmount = grantedBalance.toDoubleOrNull() ?: 0.0

    // 不透明品牌渐变实心卡（关掉液态玻璃：不透整页玻璃板，彻底不透明，保证文字可读）
    val primary = MaterialTheme.colorScheme.primary
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            primary,
            primary.copy(
                red = (primary.red * 0.82f).coerceIn(0f, 1f),
                green = (primary.green * 0.82f).coerceIn(0f, 1f),
                blue = (primary.blue * 0.82f).coerceIn(0f, 1f),
            ),
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(gradientBrush),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "账户总余额",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                        letterSpacing = 1.sp,
                    )
                    StatusChip(
                        text = statusText,
                        color = statusColor,
                        containerColor = Color.White.copy(alpha = 0.2f),
                        labelColor = Color.White,
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // 金额滚动：刷新后从 0 过渡到当前值
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = symbol,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = Color.White,
                    )
                    AnimatedAmount(
                        target = totalAmount,
                        restartKey = refreshCount,
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp,
                            fontFeatureSettings = "tnum",
                        ),
                        color = Color.White,
                    )
                }

                if (grantedBalance.isNotBlank()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(64.dp),
                    ) {
                        BalanceLine(
                            label = "赠金",
                            symbol = symbol,
                            amountTarget = grantedAmount,
                            restartKey = refreshCount,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                        // 总消费：来自用量接口（累计消费），复用 BalanceLine 保证与赠金完全对齐；
                        // 未配置网页令牌时显示 --（无动画）
                        if (totalCostCny != null) {
                            BalanceLine(
                                label = "总消费",
                                symbol = symbol,
                                amountTarget = totalCostCny,
                                restartKey = refreshCount,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        } else {
                            Column {
                                Text(
                                    text = "总消费",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = "--",
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.Medium,
                                        fontFeatureSettings = "tnum",
                                    ),
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }
                    }
                }
            }
    }

    Spacer(modifier = Modifier.height(8.dp))
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
