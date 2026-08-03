package com.deepseek.balance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    refreshCount: Int = 0,
) {
    Spacer(modifier = Modifier.height(8.dp))

    val totalAmount = totalBalance.toDoubleOrNull() ?: 0.0
    val grantedAmount = grantedBalance.toDoubleOrNull() ?: 0.0
    val toppedUpAmount = toppedUpBalance.toDoubleOrNull() ?: 0.0

    // 渐变主卡：品牌蓝 → 深蓝，白色文字，顶部叠一层柔和高光
    val primary = MaterialTheme.colorScheme.primary
    val gradientBrush = Brush.verticalGradient(
        colors = listOf(
            primary.copy(alpha = 0.95f),
            primary.copy(red = (primary.red * 0.82f).coerceIn(0f, 1f), green = (primary.green * 0.82f).coerceIn(0f, 1f), blue = (primary.blue * 0.82f).coerceIn(0f, 1f)),
        ),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
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
                        BalanceLine(
                            label = "充值",
                            symbol = symbol,
                            amountTarget = toppedUpAmount,
                            restartKey = refreshCount,
                            color = Color.White.copy(alpha = 0.85f),
                        )
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
