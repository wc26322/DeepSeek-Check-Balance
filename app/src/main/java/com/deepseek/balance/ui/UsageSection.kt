package com.deepseek.balance.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
    webTokenInvalid: Boolean = false,
    onReLoginClick: () -> Unit = {},
    isLoading: Boolean = false,
    onSettingsClick: () -> Unit = {},
    loadRangeDaily: suspend (start: java.time.LocalDate, end: java.time.LocalDate) -> List<ModelDailyUsage>? =
        { _, _ -> null },
    refreshCount: Int = 0,
) {
    Spacer(modifier = Modifier.height(24.dp))

    // 错误优先
    if (usageError != null) {
        ErrorCard(message = usageError)
        if (webTokenInvalid) {
            Spacer(modifier = Modifier.height(12.dp))
            TokenExpiredHint(onReLoginClick = onReLoginClick)
        } else if (!hasWebToken) {
            Spacer(modifier = Modifier.height(12.dp))
            NoTokenHint(onSettingsClick = onSettingsClick)
        }
        return
    }

    // 令牌已失效（自动刷新静默失败）：数据仍保留显示，上方引导重新登录
    if (webTokenInvalid) {
        TokenExpiredHint(onReLoginClick = onReLoginClick)
        Spacer(modifier = Modifier.height(16.dp))
    }

    // 未配置网页令牌
    if (usage == null && !hasWebToken) {
        NoTokenHint(onSettingsClick = onSettingsClick)
        return
    }

    // 已配置令牌：数据直接渲染，首帧即显示（与余额卡同步出现，不做淡入）。
    // 冷启动时 usage 为「结构完整、数值全 0」的占位数据，渲染出真实卡片框架，
    // 数据到达后原地填充（柱状图生长/数字滚动等内部动画体现变化）
    if (usage != null) {
        val u = usage
        // 纵向排列所有卡片
        Column(modifier = Modifier.fillMaxWidth()) {
            // 每日用量放最上面
            if (u.byModelDaily.isNotEmpty()) {
                DailyUsageCard(
                    recentDaily = u.byModelDaily,
                    loadRangeDaily = loadRangeDaily,
                    refreshCount = refreshCount,
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            UsageOverviewCard(usage = u)
            Spacer(modifier = Modifier.height(16.dp))
            if (u.byModel.isNotEmpty()) {
                UsageListCard(
                    title = "按模型",
                    rows = u.byModel.map { m ->
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
            if (u.byKey.isNotEmpty()) {
                UsageListCard(
                    title = "按 API Key",
                    rows = u.byKey.map { k ->
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
}

@Composable
private fun NoTokenHint(onSettingsClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
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
private fun TokenExpiredHint(onReLoginClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "网页令牌已失效",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "用量数据来自网页后台，令牌过期后无法继续更新。请重新登录以恢复用量查询。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onReLoginClick) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("重新登录")
            }
        }
    }
}

// ===================== 用量概览 =====================

@Composable
private fun UsageOverviewCard(usage: UsageData) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "用量概览",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(12.dp))
            // 竖排三行：每行 装饰色条 + 标签 + 数值（色条不按比例，仅作色彩点缀区分）
            OverviewBar(
                label = "总消费金额",
                value = formatMoney(usage.totalCostCny),
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OverviewBar(
                label = "总请求次数",
                value = formatInt(usage.apiCalls),
                color = Color(0xFF42A5F5),
            )
            Spacer(modifier = Modifier.height(12.dp))
            OverviewBar(
                label = "总 Tokens",
                value = formatLong(usage.totalTokens),
                color = Color(0xFF4CAF50),
            )
        }
    }
}

/** 概览数据行：装饰色条 + 标签 + 数值。色条固定长度不做比例，仅作色彩点缀区分三个维度 */
@Composable
private fun OverviewBar(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 固定长度装饰色条（圆角小方块）
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(modifier = Modifier.width(10.dp))
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
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
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
        shape = MaterialTheme.shapes.small,
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
    refreshCount: Int = 0,
) {
    var dimension by remember { mutableStateOf<DailyDimension>(DailyDimension.Recent7) }
    // 月度数据缓存：预拉取本月/上月，切换维度时即时显示（无加载等待、无旧数据动画）
    val monthlyCache = remember { mutableStateMapOf<DailyDimension, List<ModelDailyUsage>>() }
    // 最近一次拉取的月度数据（自定义等未预拉维度切换时作静态占位）
    var fetchedDaily by remember { mutableStateOf<List<ModelDailyUsage>?>(null) }
    var fetchedFor by remember { mutableStateOf<DailyDimension?>(null) }  // fetchedDaily 对应的维度（自定义维度动画触发判断）
    var loadFailed by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(recentDaily.firstOrNull()?.model ?: "") }
    var selectedDate by remember { mutableStateOf<String?>(null) }
    var customRange by remember { mutableStateOf<Pair<LocalDate, LocalDate>?>(null) }
    var showCustomPicker by remember { mutableStateOf(false) }

    // 点击柱子切换明细时，把图表下方的明细面板滚进可视区（初始加载不滚动）
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()

    // 当前展示的每日数据：近7/30 天用已有数据本地切片；
    // 本月/上月优先用预拉缓存（切换即时显示），自定义等无缓存维度用最近拉取数据静态占位
    val displayModels: List<ModelDailyUsage> = when (dimension) {
        DailyDimension.Recent7 -> recentDaily.map { it.copy(daily = it.daily.takeLast(7)) }
        DailyDimension.Recent30 -> recentDaily.map { it.copy(daily = it.daily.takeLast(30)) }
        else -> monthlyCache[dimension] ?: fetchedDaily ?: emptyList()
    }

    // 预拉取本月/上月：进入卡片即后台拉取，切换维度时直接命中缓存，无加载等待
    LaunchedEffect(recentDaily) {
        listOf(DailyDimension.ThisMonth, DailyDimension.LastMonth).forEach { d ->
            if (monthlyCache[d] == null) {
                val range = when (d) {
                    DailyDimension.ThisMonth -> {
                        val n = LocalDate.now()
                        n.withDayOfMonth(1) to n.withDayOfMonth(n.lengthOfMonth())
                    }
                    else -> {
                        val p = LocalDate.now().minusMonths(1)
                        p.withDayOfMonth(1) to p.withDayOfMonth(p.lengthOfMonth())
                    }
                }
                try {
                    loadRangeDaily(range.first, range.second)?.let { monthlyCache[d] = it }
                } catch (_: Exception) { }
            }
        }
    }
    val current = displayModels.find { it.model == selectedModel } ?: displayModels.firstOrNull()
    val displayDaily = current?.daily ?: emptyList()

    // 本月/上月/自定义：缓存未命中时按日期范围拉取月度接口数据（本月/上月通常已被预拉取命中）
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
        if (range != null && monthlyCache[dimension] == null) {
            loadFailed = false
            try {
                val data = loadRangeDaily(range.first, range.second)
                if (data != null) monthlyCache[dimension] = data
                fetchedDaily = data
                loadFailed = data == null
                fetchedFor = dimension
            } catch (e: Exception) {
                fetchedDaily = null
                loadFailed = true
                fetchedFor = null
            }
        } else {
            loadFailed = false
            fetchedFor = null
        }
    }
    // 切换维度/模型/数据后：模型选择保留有效值，明细默认选中「最后一天」（通常为今天）
    LaunchedEffect(displayModels.firstOrNull()?.model, dimension, monthlyCache[dimension], fetchedDaily, customRange) {
        if (displayModels.isNotEmpty()) {
            selectedModel = displayModels.firstOrNull()?.model ?: selectedModel
        }
        displayDaily.lastOrNull()?.let { selectedDate = it.date }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "每日用量",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "所有日期均按 UTC+0 时间显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            // 时间维度 + 模型选择并排，奶糖胶囊锚点（浅蓝渐变底 + 渐变圆箭头）
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // 时间维度下拉框
                var menuExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    modifier = if (displayModels.isEmpty())
                        Modifier.weight(1f)
                    else
                        Modifier.weight(0.38f),
                ) {
                    CompactDropdownAnchor(
                        label = "时间范围",
                        value = dimension.label,
                        expanded = menuExpanded,
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(16.dp),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                    ) {
                        listOf(
                            DailyDimension.Recent7,
                            DailyDimension.Recent30,
                            DailyDimension.ThisMonth,
                            DailyDimension.LastMonth,
                            DailyDimension.Custom,
                        ).forEach { d ->
                            CandyDropdownMenuItem(
                                text = d.label,
                                selected = dimension == d,
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
                // 模型选择下拉框
                if (displayModels.isNotEmpty()) {
                    var modelMenuExpanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = modelMenuExpanded,
                        onExpandedChange = { modelMenuExpanded = it },
                        modifier = Modifier.weight(0.62f),
                    ) {
                        CompactDropdownAnchor(
                            label = "模型",
                            value = current?.model ?: "",
                            expanded = modelMenuExpanded,
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = modelMenuExpanded,
                            onDismissRequest = { modelMenuExpanded = false },
                            // 宽度按内容自适应（最长模型名），上限 300dp，避免撑满卡片
                            modifier = Modifier.widthIn(min = 160.dp, max = 300.dp),
                            shape = RoundedCornerShape(16.dp),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)),
                        ) {
                            displayModels.forEach { m ->
                                CandyDropdownMenuItem(
                                    text = m.model,
                                    selected = selectedModel == m.model,
                                    onClick = {
                                        selectedModel = m.model
                                        modelMenuExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // 自定义日期范围选择（弹出日历）；CustomRangePicker 是悬浮 Dialog 不占布局，无需额外 Spacer
            if (showCustomPicker) {
                CustomRangePicker(
                    onConfirm = { s, e ->
                        customRange = s to e
                        dimension = DailyDimension.Custom
                        showCustomPicker = false
                    },
                    onDismiss = { showCustomPicker = false },
                )
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
                // 柱状图生长动画：仅在「数据真正切换到当前维度」时从底部重新长高。
                // 近7/30 数据即时生效；月度等维度等新数据拉取到达（fetchedFor 更新）才触发，
                // 切换瞬间旧数据保持静态显示，避免旧数据动画与「正在加载」文本的突兀
                val dataReadyKey = when (dimension) {
                    DailyDimension.Recent7, DailyDimension.Recent30 -> "local:$dimension"
                    DailyDimension.Custom -> "custom:${fetchedFor}:${fetchedDaily?.hashCode()}"
                    else -> "$dimension:${monthlyCache[dimension]?.hashCode()}"
                }
                val chartProgress = remember(current?.model, dataReadyKey, refreshCount) { Animatable(0f) }
                LaunchedEffect(current?.model, dataReadyKey, refreshCount) {
                    chartProgress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                }
                DailyColumnChart(
                    days = displayDaily,
                    selectedDate = selectedDate,
                    onSelect = { day ->
                        selectedDate = day?.date
                        // 仅在用户点击柱子时把明细面板滚进可视区
                        if (day != null) scope.launch { bringIntoViewRequester.bringIntoView() }
                    },
                    progress = chartProgress.value,
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

/** 紧凑版下拉框锚点：奶糖胶囊（浅蓝渐变底 + 渐变圆箭头），12dp 圆角与卡片呼应 */
@Composable
private fun CompactDropdownAnchor(
    label: String,
    value: String,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    val arrowAngle by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(150),
    )
    val primary = MaterialTheme.colorScheme.primary
    // 深色调 = 主题色向黑色收敛 18%（RGB 混合）
    val deep = Color(
        red = primary.red * 0.82f,
        green = primary.green * 0.82f,
        blue = primary.blue * 0.82f,
    )
    val pillBrush = Brush.linearGradient(
        colors = listOf(primary.copy(alpha = 0.16f), primary.copy(alpha = 0.07f)),
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(pillBrush)
            .border(1.5.dp, primary.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            // 渐变圆箭头（旋转 180° 表示展开）
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .background(Brush.linearGradient(listOf(primary, deep)), CircleShape)
                    .graphicsLayer { rotationZ = arrowAngle },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = Color.White,
                )
            }
        }
    }
}

/** 奶糖风格菜单项：选中项浅蓝圆角底 + 主题色文字 */
@Composable
private fun CandyDropdownMenuItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val primary = MaterialTheme.colorScheme.primary
    DropdownMenuItem(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        colors = MenuDefaults.itemColors(
            textColor = if (selected) primary else MaterialTheme.colorScheme.onSurface,
        ),
        text = {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        modifier = Modifier
            .padding(horizontal = 6.dp)
            .then(
                if (selected) {
                    Modifier.background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                primary.copy(alpha = 0.16f),
                                primary.copy(alpha = 0.07f),
                            ),
                        ),
                        shape = RoundedCornerShape(10.dp),
                    )
                } else {
                    Modifier
                }
            ),
    )
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
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
    // 整个字段就是一个可点击胶囊：与下拉框同款奶糖风格（浅色渐变底 + 渐变圆箭头）
    val primary = MaterialTheme.colorScheme.primary
    val deep = Color(
        red = primary.red * 0.82f,
        green = primary.green * 0.82f,
        blue = primary.blue * 0.82f,
    )
    val pillBrush = Brush.linearGradient(
        colors = listOf(primary.copy(alpha = 0.16f), primary.copy(alpha = 0.07f)),
    )
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0f),
        border = BorderStroke(1.5.dp, primary.copy(alpha = 0.35f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(pillBrush),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    )
                    Text(
                        text = value.ifEmpty { "请选择" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (value.isEmpty())
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.onSurface,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(Brush.linearGradient(listOf(primary, deep)), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = Color.White,
                    )
                }
            }
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
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
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
                .clip(MaterialTheme.shapes.small)
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
    progress: Float = 1f,
) {
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    // 点击反馈：被点击柱子短暂放大（按压感），press 动画 1→0 恢复
    val scope = rememberCoroutineScope()
    val press = remember { Animatable(0f) }
    var pressedIndex by remember { mutableStateOf(-1) }
    // 选中反馈：非选中柱变暗（选中柱全亮，其余 35% 透明度），点击切换时旧选中柱平滑变暗过渡
    val fadeProgress = remember { Animatable(0f) }  // 0=亮，1=暗（旧选中柱过渡进度）
    var lastSelectedDate by remember { mutableStateOf<String?>(null) }
    var lastDaysKey by remember { mutableStateOf<String?>(null) }
    // 数据内容标识：切换时间范围/刷新时变化，用于区分「点击切换」与「数据整体变化」
    val daysKey = days.joinToString("|") { it.date }
    LaunchedEffect(selectedDate, daysKey) {
        val newIdx = days.indexOfFirst { it.date == selectedDate }
        if (lastDaysKey != daysKey) {
            // 数据变化（切换时间范围/刷新）：直接定位，不做过渡动画，避免旧索引错位导致闪烁
            fadeProgress.snapTo(1f)
        } else if (lastSelectedDate != null && lastSelectedDate != selectedDate && newIdx >= 0) {
            // 同批数据内点击切换：旧选中柱从当前透明度平滑变暗
            fadeProgress.animateTo(1f, tween(300))
        }
        lastSelectedDate = selectedDate
        lastDaysKey = daysKey
    }

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
                        // 按压反馈动画：柱子短暂放大后恢复
                        pressedIndex = idx
                        scope.launch {
                            press.snapTo(1f)
                            press.animateTo(0f, tween(250))
                        }
                        onSelect(days[idx])
                    }
                }
            },
    ) {
        if (days.isEmpty()) return@Canvas
        val p = progress.coerceIn(0f, 1f)  // 生长动画进度：0=从底部开始，1=完整高度
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
            alpha: Float = 1f,
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
            drawPath(path, color, alpha = alpha)
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
        val selIdx = days.indexOfFirst { it.date == selectedDate }
        val lastSelIdx = days.indexOfFirst { it.date == lastSelectedDate }
        days.forEachIndexed { i, d ->
            // 按压反馈：被点击的柱子宽度短暂放大（press 动画 1→0 恢复）
            val effBarW = barW * (if (i == pressedIndex) 1f + press.value * 0.08f else 1f)
            val left = px(i) - effBarW / 2f
            val total = d.totalTokens
            // 选中反馈：选中柱全亮，非选中柱变暗；点击切换时旧选中柱平滑变暗
            val barAlpha = when {
                i == selIdx -> 1f
                i == lastSelIdx -> 1f - 0.65f * fadeProgress.value
                else -> 0.35f
            }
            if (total > 0) {
                // 生长动画：各段高度随进度从底部向上增长
                val hH = chartH * d.cacheHitTokens.toFloat() / maxV.toFloat() * p
                val mH = chartH * d.cacheMissTokens.toFloat() / maxV.toFloat() * p
                val rH = chartH * d.responseTokens.toFloat() / maxV.toFloat() * p
                val hasHit = hH > 0f
                val hasMiss = mH > 0f
                val hasResp = rH > 0f
                var y = bottom
                if (hasResp) {
                    drawRoundedRect(ResponseColor, left, y - rH, effBarW, rH, corner,
                        roundTop = false, roundBottom = false, alpha = barAlpha)
                    y -= rH
                }
                if (hasMiss) {
                    drawRoundedRect(MissColor, left, y - mH, effBarW, mH, corner,
                        roundTop = false, roundBottom = false, alpha = barAlpha)
                    y -= mH
                }
                if (hasHit) {
                    drawRoundedRect(HitColor, left, y - hH, effBarW, hH, corner,
                        roundTop = true, roundBottom = false, alpha = barAlpha)
                    y -= hH
                }
            }
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
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp),
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
                    .clip(MaterialTheme.shapes.small)
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
