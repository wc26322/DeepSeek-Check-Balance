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
    Spacer(modifier = Modifier.height(24.dp))
    LiquidCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        // errorContainer 半透明叠在玻璃表面：保留错误语义红色，又有玻璃质感
        surfaceColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
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
