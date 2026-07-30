package com.deepseek.balance.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    onBackClick: () -> Unit,
    alertEnabled: Boolean,
    onAlertEnabledChange: (Boolean) -> Unit,
    alertThreshold: String,
    onAlertThresholdChange: (String) -> Unit,
    widgetRealtime: Boolean,
    onWidgetRealtimeChange: (Boolean) -> Unit,
    widgetIntervalSec: Int,
    onWidgetIntervalSecChange: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 24.dp, top = 8.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(contentType = "api") {
                ApiKeyCard(apiKey, onApiKeyChange)
            }
            item(contentType = "alert") {
                AlertCard(
                    enabled = alertEnabled,
                    onEnabledChange = onAlertEnabledChange,
                    threshold = alertThreshold,
                    onThresholdChange = onAlertThresholdChange,
                )
            }
            item(contentType = "realtime") {
                RealtimeCard(
                    enabled = widgetRealtime,
                    onEnabledChange = onWidgetRealtimeChange,
                    intervalSec = widgetIntervalSec,
                    onIntervalSecChange = onWidgetIntervalSecChange,
                )
            }
            item(contentType = "about") {
                AboutCard()
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(), // 缓存整卡(含阴影)为硬件层，滚动时只平移、不重绘阴影模糊
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp), content = content)
    }
}

// ===================== API Key =====================
@Composable
private fun ApiKeyCard(apiKey: String, onApiKeyChange: (String) -> Unit) {
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    SettingsCard {
        Text(
            text = "API Key",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("sk-...") },
            visualTransformation = if (showPassword)
                VisualTransformation.None
            else
                PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() },
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
            ),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (showPassword) "隐藏" else "显示",
                    )
                }
            },
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (apiKey.isNotBlank())
                "API Key 已保存，返回主界面即可查询"
            else
                "在 platform.deepseek.com/api_keys 获取",
            style = MaterialTheme.typography.bodySmall,
            color = if (apiKey.isNotBlank())
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "API Key 仅保存在本机，不会上传到任何第三方服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ===================== 余额预警 =====================
@Composable
private fun AlertCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    threshold: String,
    onThresholdChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "余额预警",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled) "余额低于阈值时在主界面显示提醒" else "关闭",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "预警金额 (¥)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = threshold,
                enabled = enabled,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() || it == '.' }
                    if (filtered.isEmpty() || filtered == ".") {
                        onThresholdChange("")
                    } else {
                        val num = filtered.toDoubleOrNull()
                        if (num != null && num >= 1 && num <= 5000000) {
                            onThresholdChange(filtered)
                        } else if (num != null && num < 1) {
                            onThresholdChange("1")
                        } else if (num != null && num > 5000000) {
                            onThresholdChange("5000000")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("例如 50") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                singleLine = true,
                prefix = { Text("¥ ", fontWeight = FontWeight.Medium) },
                suffix = {
                    if (threshold.isNotBlank()) {
                        Text("元", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                shape = RoundedCornerShape(12.dp),
                supportingText = { Text("范围：¥1 ~ ¥5,000,000") },
            )
        }
    }
}

// ===================== 小组件实时刷新 =====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RealtimeCard(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    intervalSec: Int,
    onIntervalSecChange: (Int) -> Unit,
) {
    val intervalOptions = remember {
        listOf(
            15 to "15秒",
            30 to "30秒",
            60 to "1分",
            120 to "2分",
            300 to "5分",
            600 to "10分",
            900 to "15分",
        )
    }
    val currentLabel = remember(intervalSec, intervalOptions) {
        intervalOptions.firstOrNull { it.first == intervalSec }?.second ?: "15秒"
    }
    var intervalExpanded by remember { mutableStateOf(false) }

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "后台实时刷新",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled) "按间隔自动更新小组件" else "关闭（仅手动/打开 App 时更新）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.4f),
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "刷新间隔",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = intervalExpanded,
                onExpandedChange = { if (enabled) intervalExpanded = it },
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = currentLabel,
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = intervalExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                )
                ExposedDropdownMenu(
                    expanded = intervalExpanded,
                    onDismissRequest = { intervalExpanded = false },
                ) {
                    intervalOptions.forEach { (sec, label) ->
                        DropdownMenuItem(
                            enabled = enabled,
                            text = { Text(label) },
                            onClick = {
                                onIntervalSecChange(sec)
                                intervalExpanded = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "开启后状态栏会显示一个常驻通知（低重要级，不发声），用于保持后台刷新。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

// ===================== 关于 =====================
@Composable
private fun AboutCard() {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "版本",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "1.0",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
