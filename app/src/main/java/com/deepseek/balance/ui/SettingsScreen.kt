package com.deepseek.balance.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    webToken: String,
    onWebTokenChange: (String) -> Unit,
    onWebLoginClick: () -> Unit,
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
            item(contentType = "webtoken") {
                WebTokenCard(
                    webToken = webToken,
                    onWebTokenChange = onWebTokenChange,
                    onWebLoginClick = onWebLoginClick,
                )
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
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer(), // 缓存整卡(含阴影)为硬件层，滚动时只平移、不重绘阴影模糊
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
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
            shape = MaterialTheme.shapes.medium,
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
        if (apiKey.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { onApiKeyChange("") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清除 API Key")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "API Key 仅保存在本机，不会上传到任何第三方服务器。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// ===================== 网页令牌 =====================
@Composable
private fun WebTokenCard(
    webToken: String,
    onWebTokenChange: (String) -> Unit,
    onWebLoginClick: () -> Unit,
) {
    var showPassword by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    SettingsCard {
        Text(
            text = "网页令牌（用量查询）",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "用于查询累计消费 / 请求数 / Tokens / 按模型 / 按 Key 等用量数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = webToken,
            onValueChange = onWebTokenChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("网页登录态令牌") },
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
            shape = MaterialTheme.shapes.medium,
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (webToken.isNotBlank())
                "网页令牌已保存，返回主界面即可查询用量"
            else
                "获取方式：登录 platform.deepseek.com → F12 → Application → Local Storage → 复制 userToken 的值",
            style = MaterialTheme.typography.bodySmall,
            color = if (webToken.isNotBlank())
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(12.dp))
        // 网页一键登录：在 WebView 内登录后自动抓取 userToken，免去手动复制
        Button(
            onClick = onWebLoginClick,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text("网页一键登录（免手动复制）")
        }
        if (webToken.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            TextButton(
                onClick = { onWebTokenChange("") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("清除网页令牌")
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "网页令牌为短期登录凭据，会过期；仅保存在本机，不上传任何第三方。",
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
                shape = MaterialTheme.shapes.small,
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
                    shape = MaterialTheme.shapes.small,
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
    // 版本号直接读安装包（build.gradle 里的 versionName），与 APK 同步
    val context = LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }
    val githubUrl = "https://github.com/wc26322/DeepSeek-Check-Balance"
    SettingsCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 版本行
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "版本",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(130.dp),
                )
                Text(
                    text = versionName.ifBlank { "未知" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.End,
                )
            }
            // 横线分隔
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 10.dp),
            )
            // GitHub 开源地址：值为仓库短名，普通数值风格，仅文字可点
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "GitHub开源地址",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "打开",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable {
                            try {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl)),
                                )
                            } catch (_: Exception) {
                            }
                        }
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}
