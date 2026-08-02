package com.deepseek.balance.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.deepseek.balance.model.DailyUsage
import com.deepseek.balance.model.ModelDailyUsage
import com.deepseek.balance.model.ModelUsage
import com.deepseek.balance.model.UsageData
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private fun formatInt(n: Int): String = "%,d".format(java.util.Locale.US, n)
private fun formatLong(n: Long): String = "%,d".format(java.util.Locale.US, n)
private fun formatMoney(v: Double): String = "¥" + "%.2f".format(java.util.Locale.US, v)

private val mmddFmt: java.time.format.DateTimeFormatter = java.time.format.DateTimeFormatter.ofPattern("MM-dd")

// 每日用量堆叠柱状图的三段颜色（输入命中/未命中/输出）
private val HitColor = Color(0xFF4CAF50)   // 绿：输入·命中缓存
private val MissColor = Color(0xFFFFA726)  // 橙：输入·未命中缓存
private val ResponseColor = Color(0xFF42A5F5) // 蓝：输出

@Composable
internal fun UsageSection(
    usage: UsageData?,
    usageError: String?,
    hasWebToken: Boolean,
    isLoading: Boolean = false,
    onSettingsClick: () -> Unit = {},
    loadRangeDaily: suspend (start: java.time.LocalDate, end: java.time.LocalDate) -> List<ModelDailyUsage>? =
        { _, _ -> null },
) {
    Spacer(modifier = Modifier.height(24.dp))

    // 错误优先
    if (usageError != null) {
        ErrorCard(message = usageError)
        if (!hasWebToken) {
            Spacer(modifier = Modifier.height(12.dp))
            NoTokenHint(onSettingsClick = onSettingsClick)
        }
        return
    }

    // 未配置网页令牌
    if (usage == null && !hasWebToken) {
        NoTokenHint(onSettingsClick = onSettingsClick)
        return
    }

    // 首次加载中（已配令牌但还没有数据）
    if (isLoading && usage == null) {
        UsageLoadingCard()
        return
    }

    // 已加载
    if (usage != null) {
        // 每日用量放最上面
        if (usage.byModelDaily.isNotEmpty()) {
            DailyUsageCard(
                recentDaily = usage.byModelDaily,
                loadRangeDaily = loadRangeDaily,
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        UsageOverviewCard(usage = usage)
        Spacer(modifier = Modifier.height(16.dp))
        if (usage.byModel.isNotEmpty()) {
            UsageListCard(
                title = "按模型",
                rows = usage.byModel.map { m ->
                    UsageRowData(
                        primary = m.model,
                        calls = m.apiCalls,
                        tokens = m.totalTokens,
                        cost = m.costCny,
                    )
                },
            )
            Spacer(modifier = Modifier.height(16.dp))
        }
        if (usage.byKey.isNotEmpty()) {
            UsageListCard(
                title = "按 API Key",
                rows = usage.byKey.map { k ->
                    UsageRowData(
                        primary = k.name,
                        secondary = k.sensitiveId,
                        calls = k.apiCalls,
                        tokens = k.totalTokens,
                        cost = k.costCny,
                    )
                },
                icon = Icons.Default.Key,
            )
        }
    }
}

@Composable
private fun NoTokenHint(onSettingsClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "用量明细需要「网页令牌」",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "余额来自 API Key；但累计消费 / 请求数 / Tokens / 按模型 / 按 Key 等用量数据来自网页后台，需要用网页令牌鉴权。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onSettingsClick) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("前往设置填写网页令牌")
            }
        }
    }
}

@Composable
private fun UsageLoadingCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "正在加载用量数据…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UsageOverviewCard(usage: UsageData) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "用量概览",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.sp,
            )
            Spacer(modifier = Modifier.height(16.dp))
            // 竖排三行：每行占满卡片宽度，标签居左、数值右对齐，精确长数字也放得下
            OverviewRow(
                label = "累计消费",
                value = formatMoney(usage.totalCostCny),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OverviewRow(
                label = "请求次数",
                value = formatInt(usage.apiCalls),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OverviewRow(
                label = "Tokens",
                value = formatLong(usage.totalTokens),
            )
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}

private data class UsageRowData(
    val primary: String,
    val secondary: String? = null,
    val calls: Int,
    val tokens: Long,
    val cost: Double,
)

@Composable
private fun UsageListCard(
    title: String,
    rows: List<UsageRowData>,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                icon?.let {
                    Icon(
                        it,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                Column {
                    Text(
                        text = row.primary,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (!row.secondary.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = row.secondary,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    // 请求 / 消费 两个短数值一排，Tokens 精确数值单独一行，避免长数字换行
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatChip("请求", formatInt(row.calls))
                        if (row.cost > 0) StatChip("消费", formatMoney(row.cost))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TokensRow(value = formatLong(row.tokens))
                }
            }
        }
    }
}

@Composable
private fun TokensRow(value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Tokens",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StatChip(label: String, value: String) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ===================== 每日用量（按模型） =====================
private sealed interface DailyDimension {
    data object Recent7 : DailyDimension
    data object Recent30 : DailyDimension
    data object ThisMonth : DailyDimension
    data object LastMonth : DailyDimension
    data object Custom : DailyDimension
}

private val DailyDimension.label: String
    get() = when (this) {
        DailyDimension.Recent7 -> "近7天"
        DailyDimension.Recent30 -> "近30天"
        DailyDimension.ThisMonth -> "本月"
        DailyDimension.LastMonth -> "上月"
        DailyDimension.Custom -> "自定义"
    }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun DailyUsageCard(
    recentDaily: List<ModelDailyUsage>,
    loadRangeDaily: suspend (start: LocalDate, end: LocalDate) -> List<ModelDailyUsage>?,
) {
    var dimension by remember { mutableStateOf<DailyDimension>(DailyDimension.Recent7) }
    var fetchedDaily by remember { mutableStateOf<List<ModelDailyUsage>?>(null) }
    var loadFailed by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(recentDaily.firstOrNull()?.model ?: "") }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var customRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var showCustomPicker by remember { mutableStateOf(false) }

    // 点击柱子切换明细时，把图表下方的明细面板滚进可视区（初始加载不滚动）
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // 当前展示的每日数据：近7/30 天用已有数据本地切片；本月/上月/自定义用拉取的月度数据
    val displayModels: List<ModelDailyUsage> = when (dimension) {
        DailyDimension.Recent7 -> recentDaily.map { it.copy(daily = it.daily.takeLast(7)) }
        DailyDimension.Recent30 -> recentDaily.map { it.copy(daily = it.daily.takeLast(30)) }
        else -> fetchedDaily ?: emptyList()
    }
    val current = displayModels.find { it.model == selectedModel } ?: displayModels.firstOrNull()
    val displayDaily = current?.daily ?: emptyList()

    // 本月/上月/自定义：按日期范围拉取月度接口数据
    LaunchedEffect(dimension, customRange) {
        val range = when (dimension) {
            DailyDimension.ThisMonth -> {
                val n = LocalDate.now()
                n.withDayOfMonth(1) to n.withDayOfMonth(n.lengthOfMonth())
            }
            DailyDimension.LastMonth -> {
                val p = LocalDate.now().minusMonths(1)
                p.withDayOfMonth(1) to p.withDayOfMonth(p.lengthOfMonth())
            }
            DailyDimension.Custom -> customRange
            else -> null
        }
        if (range != null) {
            loadFailed = false
            try {
                fetchedDaily = loadRangeDaily(range.first, range.second)
                loadFailed = fetchedDaily == null
            } catch (e: Exception) {
                fetchedDaily = null
                loadFailed = true
            }
        }
    }
    // 切换维度/模型/数据后：模型选择保留有效值，明细默认选中「最后一天」（通常为今天）
    LaunchedEffect(displayModels.firstOrNull()?.model, dimension, fetchedDaily, customRange) {
        if (displayModels.isNotEmpty()) {
            selectedModel = displayModels.firstOrNull()?.model ?: selectedModel
        }
        displayDaily.lastOrNull()?.let { selectedDate = it.date }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "每日用量 · UTC",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 时间维度下拉框
            var menuExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = menuExpanded,
                onExpandedChange = { menuExpanded = it },
            ) {
                OutlinedTextField(
                    value = dimension.label,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded) },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
                ExposedDropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    listOf(
                        DailyDimension.Recent7,
                        DailyDimension.Recent30,
                        DailyDimension.ThisMonth,
                        DailyDimension.LastMonth,
                        DailyDimension.Custom,
                    ).forEach { d ->
                        DropdownMenuItem(
                            text = { Text(d.label) },
                            onClick = {
                                menuExpanded = false
                                if (d == DailyDimension.Custom) {
                                    showCustomPicker = true
                                } else {
                                    dimension = d
                                }
                            },
                        )
                    }
                }
            }
            // 自定义日期范围选择（弹出日历）
            if (showCustomPicker) {
                Spacer(modifier = Modifier.height(8.dp))
                CustomRangePicker(
                    onConfirm = { s, e ->
                        customRange = s to e
                        dimension = DailyDimension.Custom
                        showCustomPicker = false
                    },
                    onDismiss = { showCustomPicker = false },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // 模型选择（下拉框）
            if (displayModels.isNotEmpty()) {
                var modelMenuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = modelMenuExpanded,
                    onExpandedChange = { modelMenuExpanded = it },
                ) {
                    OutlinedTextField(
                        value = current?.model ?: "",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelMenuExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                    )
                    ExposedDropdownMenu(
                        expanded = modelMenuExpanded,
                        onDismissRequest = { modelMenuExpanded = false },
                    ) {
                        displayModels.forEach { m ->
                            DropdownMenuItem(
                                text = { Text(m.model) },
                                onClick = {
                                    selectedModel = m.model
                                    modelMenuExpanded = false
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            // 颜色图例
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LegendItem(HitColor, "输入·命中")
                LegendItem(MissColor, "输入·未命中")
                LegendItem(ResponseColor, "输出")
            }
            Spacer(modifier = Modifier.height(4.dp))
            // 竖向堆叠柱状图：横轴=日期（左旧右新），纵轴=每日总 Tokens；
            // 明细面板始终显示在图表下方：默认今天（最后一天），点击有量的柱子切换
            if (current != null) {
                DailyColumnChart(
                    days = displayDaily,
                    selectedDate = selectedDate,
                    onSelect = { day ->
                        selectedDate = day?.date
                        // 仅在用户点击柱子时把明细面板滚进可视区
                        if (day != null) scope.launch { bringIntoViewRequester.bringIntoView() }
                    },
                )
                val shownDay = displayDaily.find { it.date == selectedDate } ?: displayDaily.lastOrNull()
                shownDay?.let { day ->
                    Spacer(modifier = Modifier.height(12.dp))
                    DayDetail(
                        day = day,
                        modifier = Modifier.bringIntoViewRequester(bringIntoViewRequester),
                    )
                }
            } else if (dimension == DailyDimension.Custom || dimension == DailyDimension.ThisMonth || dimension == DailyDimension.LastMonth) {
                Text(
                    text = if (loadFailed)
                        "加载失败，请检查网页令牌是否有效"
                    else
                        "正在加载该时间段的用量…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (loadFailed)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

/** 自定义日期范围选择对话框：开始/结束两个日期字段，各自点击弹出单日日历，间隔 ≤31 天 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangePicker(
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var startMs by remember { mutableStateOf<Long?>(null) }
    var endMs by remember { mutableStateOf<Long?>(null) }
    var picking by remember { mutableStateOf<Int?>(null) } // 1=选开始, 2=选结束

    val start = startMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val end = endMs?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    val days = if (start != null && end != null) (ChronoUnit.DAYS.between(start, end).toInt() + 1) else null
    val valid = days != null && days in 1..31

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(360.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "选择日期范围",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(12.dp))
                DateField(
                    label = "开始日期",
                    value = start?.format(mmddFmt) ?: "",
                    onClick = { picking = 1 },
                )
                Spacer(modifier = Modifier.height(8.dp))
                DateField(
                    label = "结束日期",
                    value = end?.format(mmddFmt) ?: "",
                    onClick = { picking = 2 },
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = when {
                        days == null -> "请选择开始和结束日期"
                        valid -> "间隔 $days 天（最多 31 天）"
                        else -> "间隔需在 1~31 天之间"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid || days == null)
                        MaterialTheme.colorScheme.onSurfaceVariant
                    else
                        MaterialTheme.colorScheme.error,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        enabled = valid && start != null && end != null,
                        onClick = { if (start != null && end != null) onConfirm(start, end) },
                    ) { Text("确定") }
                }
            }
        }
    }

    // 点击字段时弹出的单日日历
    when (picking) {
        1 -> SingleDateDialog(
            initial = startMs,
            selectableStart = null, // 选开始日：任意日期可选
            onPick = { startMs = it; endMs = null; picking = null }, // 改开始日会重置结束日
            onDismiss = { picking = null },
        )
        2 -> SingleDateDialog(
            initial = endMs,
            selectableStart = start, // 选结束日：仅 [开始日, 开始日+30] 可选，超31天置灰
            onPick = { endMs = it; picking = null },
            onDismiss = { picking = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: String, onClick: () -> Unit) {
    // 整个字段就是一个可点击 Surface：外观模拟输入框（边框+标签+数值+下拉箭头），
    // 波纹严格沿 shape 的 12dp 圆角绘制、贴合方框，也不存在输入框吞点击的问题。
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = value.ifEmpty { "请选择" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (value.isEmpty())
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单日日历选择对话框；selectableStart 非空时仅 [开始日, 开始日+30天] 可选（选结束日用） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SingleDateDialog(
    initial: Long?,
    selectableStart: LocalDate?,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        // 无初始选中时显式指定为当前月，避免默认月份偏差
        initialDisplayedMonthMillis = initial ?: System.currentTimeMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = Instant.ofEpochMilli(utcTimeMillis)
                    .atZone(ZoneId.systemDefault()).toLocalDate()
                // 未来日期没有用量数据，一律不可选
                if (day.isAfter(LocalDate.now())) return false
                if (selectableStart == null) return true
                val diff = ChronoUnit.DAYS.between(selectableStart, day)
                return diff in 0..30
            }
            override fun isSelectableYear(year: Int): Boolean = year <= LocalDate.now().year
        },
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.width(360.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column {
                DatePicker(
                    state = state,
                    showModeToggle = false,
                    title = null,
                    headline = null,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    TextButton(
                        onClick = { state.selectedDateMillis?.let(onPick) ?: onDismiss() },
                    ) { Text("确定") }
                }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 竖向堆叠柱状图：横轴=日期，纵轴=每日总 Tokens，柱内三段颜色（绿=输入命中，橙=输入未命中，蓝=输出） */
@Composable
private fun DailyColumnChart(
    days: List<DailyUsage>,
    selectedDate: String?,
    onSelect: (DailyUsage?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val markerColor = MaterialTheme.colorScheme.primary

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .pointerInput(days, selectedDate) {
                detectTapGestures { tap ->
                    if (days.isEmpty()) return@detectTapGestures
                    val padL = 44.dp.toPx()
                    val chartW = size.width - padL - 12.dp.toPx()
                    val n = days.size
                    fun px(i: Int): Float =
                        padL + if (n > 1) chartW * (i + 0.5f) / n.toFloat() else padL + chartW / 2f
                    val idx = days.indices.minByOrNull { kotlin.math.abs(px(it) - tap.x) }
                    // 点中有用量的柱子→切换该天明细；点空白/零用量处不动作（明细保持当前选中）
                    if (idx != null && days[idx].totalTokens > 0) {
                        onSelect(days[idx])
                    }
                }
            },
    ) {
        if (days.isEmpty()) return@Canvas
        val maxV = days.maxOfOrNull { it.totalTokens }?.coerceAtLeast(1L) ?: 1L
        val n = days.size
        val padL = 44.dp.toPx()
        val padT = 12.dp.toPx()
        val padB = 26.dp.toPx()
        val padR = 12.dp.toPx()
        val chartW = size.width - padL - padR
        val chartH = size.height - padT - padB
        fun px(i: Int): Float =
            padL + if (n > 1) chartW * (i + 0.5f) / n.toFloat() else padL + chartW / 2f
        fun py(v: Long): Float = padT + chartH * (1f - v.toFloat() / maxV.toFloat())

        // 画一段柱子，可按需圆角顶部/底部（内部交界处保持直边）
        fun drawRoundedRect(
            color: Color,
            left: Float,
            top: Float,
            width: Float,
            height: Float,
            corner: Float,
            roundTop: Boolean,
            roundBottom: Boolean,
        ) {
            if (width <= 0f || height <= 0f) return
            val path = Path()
            path.addRoundRect(
                RoundRect(
                    rect = Rect(Offset(left, top), Size(width, height)),
                    topLeft = CornerRadius(if (roundTop) corner else 0f),
                    topRight = CornerRadius(if (roundTop) corner else 0f),
                    bottomLeft = CornerRadius(if (roundBottom) corner else 0f),
                    bottomRight = CornerRadius(if (roundBottom) corner else 0f),
                )
            )
            drawPath(path, color)
        }

        // 网格线 + Y 轴刻度（0 / 中间 / max）
        val gridPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = gridColor.copy(alpha = 0.4f).toArgb()
            strokeWidth = 1.dp.toPx()
        }
        val labelPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = labelColor.toArgb()
            textSize = 10.sp.toPx()
        }
        drawContext.canvas.nativeCanvas.drawLine(padL, py(0), size.width - padR, py(0), gridPaint)
        drawContext.canvas.nativeCanvas.drawLine(padL, py(maxV / 2), size.width - padR, py(maxV / 2), gridPaint)
        drawContext.canvas.nativeCanvas.drawText("0", 2.dp.toPx(), py(0) + 4.dp.toPx(), labelPaint)
        drawContext.canvas.nativeCanvas.drawText(formatAxis(maxV), 2.dp.toPx(), py(maxV) + 4.dp.toPx(), labelPaint)

        // X 轴日期刻度（隔几个显示一个，避免拥挤）
        val step = if (n > 8) (n / 6).coerceAtLeast(1) else 1
        days.forEachIndexed { i, d ->
            if (i % step == 0) {
                drawContext.canvas.nativeCanvas.drawText(
                    d.date,
                    px(i) - 12.dp.toPx(),
                    size.height - 6.dp.toPx(),
                    labelPaint,
                )
            }
        }

        // 竖向堆叠柱：从下往上 输出(蓝) → 未命中(橙) → 命中(绿)，只圆最顶部（绿）的两个上角
        val barW = if (n > 1) chartW / n * 0.72f else chartW * 0.6f
        val corner = minOf(6.dp.toPx(), barW / 2f)
        val bottom = py(0)
        days.forEachIndexed { i, d ->
            val left = px(i) - barW / 2f
            val total = d.totalTokens
            if (total > 0) {
                val hH = chartH * d.cacheHitTokens.toFloat() / maxV.toFloat()
                val mH = chartH * d.cacheMissTokens.toFloat() / maxV.toFloat()
                val rH = chartH * d.responseTokens.toFloat() / maxV.toFloat()
                val hasHit = hH > 0f
                val hasMiss = mH > 0f
                val hasResp = rH > 0f
                var y = bottom
                if (hasResp) {
                    drawRoundedRect(ResponseColor, left, y - rH, barW, rH, corner,
                        roundTop = false, roundBottom = false)
                    y -= rH
                }
                if (hasMiss) {
                    drawRoundedRect(MissColor, left, y - mH, barW, mH, corner,
                        roundTop = false, roundBottom = false)
                    y -= mH
                }
                if (hasHit) {
                    drawRoundedRect(HitColor, left, y - hH, barW, hH, corner,
                        roundTop = true, roundBottom = false)
                    y -= hH
                }
            }
        }

        // 选中柱上方标记小圆点
        val selIdx = days.indexOfFirst { it.date == selectedDate }
        if (selIdx >= 0 && days[selIdx].totalTokens > 0) {
            val markerPaint = android.graphics.Paint().apply {
                isAntiAlias = true
                color = markerColor.toArgb()
            }
            drawContext.canvas.nativeCanvas.drawCircle(
                px(selIdx),
                py(days[selIdx].totalTokens) - 6.dp.toPx(),
                4.dp.toPx(),
                markerPaint,
            )
        }
    }
}

/** 竖向柱状图 Y 轴刻度用的紧凑数字 */
private fun formatAxis(v: Long): String = when {
    v >= 100_000_000L -> "%.1f亿".format(java.util.Locale.US, v / 100_000_000.0)
    v >= 10_000L -> "%.1f万".format(java.util.Locale.US, v / 10_000.0)
    else -> formatLong(v)
}

/** 选中天的明细面板：显示在柱状图下方 */
@Composable
private fun DayDetail(day: DailyUsage, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = day.date,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "请求 ${formatInt(day.apiCalls)} · 合计 ${formatLong(day.totalTokens)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                DetailItem("输入·命中", day.cacheHitTokens, HitColor)
                DetailItem("输入·未命中", day.cacheMissTokens, MissColor)
                DetailItem("输出", day.responseTokens, ResponseColor)
            }
        }
    }
}

@Composable
private fun DetailItem(label: String, value: Long, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatLong(value),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Medium,
                fontFeatureSettings = "tnum",
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun UsageSectionPreview() {
    MaterialTheme {
        UsageSection(
            usage = UsageData(
                totalCostCny = 19.20,
                apiCalls = 2895,
                totalTokens = 478218898L,
                windowDays = 30,
                byModel = listOf(
                    ModelUsage("deepseek-v4-flash", 2465, 398362706, 15.24),
                    ModelUsage("deepseek-v4-pro", 430, 79856192, 3.96),
                ),
                byKey = listOf(),
                byModelDaily = listOf(
                    ModelDailyUsage(
                        model = "deepseek-v4-flash",
                        daily = listOf(
                            DailyUsage("07-25", 1204, 1502330, 1018204, 1404322),
                            DailyUsage("07-24", 987, 800123, 700456, 599544),
                        ),
                    ),
                    ModelDailyUsage(
                        model = "deepseek-v4-pro",
                        daily = listOf(
                            DailyUsage("07-25", 300, 500000, 300000, 400000),
                        ),
                    ),
                ),
            ),
            usageError = null,
            hasWebToken = true,
        )
    }
}
