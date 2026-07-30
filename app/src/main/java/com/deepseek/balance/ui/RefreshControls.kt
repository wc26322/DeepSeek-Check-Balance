package com.deepseek.balance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RefreshControls(
    hasData: Boolean,
    hasKey: Boolean,
    isLoading: Boolean,
    onQueryClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasData) {
            FilledTonalButton(
                onClick = onQueryClick,
                enabled = !isLoading,
                modifier = Modifier.height(40.dp),
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("刷新", style = MaterialTheme.typography.labelLarge)
            }
        } else {
            Button(
                onClick = onQueryClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = hasKey && !isLoading,
                shape = RoundedCornerShape(16.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("查询中")
                } else {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("查询余额", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun RefreshControlsPreview() {
    MaterialTheme {
        Column {
            RefreshControls(
                hasData = true,
                hasKey = true,
                isLoading = false,
                onQueryClick = {},
            )
        }
    }
}
