package com.deepseek.balance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun BalanceLine(
    label: String,
    symbol: String,
    amountTarget: Double,
    restartKey: Int,
    color: Color,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.65f),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row {
            Text(
                text = symbol,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = color,
            )
            AnimatedAmount(
                target = amountTarget,
                restartKey = restartKey,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = color,
            )
        }
    }
}
