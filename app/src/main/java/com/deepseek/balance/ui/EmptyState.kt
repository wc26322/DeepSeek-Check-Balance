package com.deepseek.balance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/** 空状态卡片：无凭证时给出「开始使用」主引导（网页登录优先）；有凭证无数据时提示下拉刷新 */
@Composable
internal fun EmptyState(
    hasCredential: Boolean,
    onWebLoginClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    // 外部间距由 MainScreen 统一控制；与其它卡片同款液态玻璃容器
    LiquidCard(
        modifier = Modifier.fillMaxWidth(),

    ) {
        Column(
            modifier = Modifier.padding(UiSpacing.Xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!hasCredential) {
                Text(
                    text = "开始使用",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "网页一键登录可同时查看余额与用量明细；也可只填入 API Key 查看余额。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LiquidButton(
                        onClick = onWebLoginClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "网页一键登录",
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LiquidButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = "填写 API Key",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                Text(
                    text = "暂无数据",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "下拉即可刷新余额与用量",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EmptyStatePreview() {
    MaterialTheme {
        EmptyState(hasCredential = false, onWebLoginClick = {}, onSettingsClick = {})
    }
}
