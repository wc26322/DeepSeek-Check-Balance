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
) {
    Spacer(modifier = Modifier.height(8.dp))

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
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
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    letterSpacing = 1.sp,
                )
                StatusChip(text = statusText, color = statusColor)
            }

            Spacer(modifier = Modifier.height(3.dp))

            Text(
                text = "$symbol$totalBalance",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onPrimary,
            )

            if (grantedBalance.isNotBlank()) {
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(64.dp),
                ) {
                    BalanceLine(
                        label = "赠金",
                        value = "${symbol}$grantedBalance",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
                    BalanceLine(
                        label = "充值",
                        value = "${symbol}$toppedUpBalance",
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    )
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
