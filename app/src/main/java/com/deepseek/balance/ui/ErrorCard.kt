package com.deepseek.balance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
internal fun ErrorCard(message: String) {

    LiquidCard(
        modifier = Modifier.fillMaxWidth(),


    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ErrorCardPreview() {
    MaterialTheme {
        ErrorCard(message = "网络错误：无法连接到服务器")
    }
}
