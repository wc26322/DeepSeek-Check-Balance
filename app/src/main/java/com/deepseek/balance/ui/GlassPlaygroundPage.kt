package com.deepseek.balance.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassPlaygroundPage(
    tuning: LiquidGlassTuning,
    navTuning: NavGlassTuning,
    onTuningChange: (LiquidGlassTuning) -> Unit,
    onTuningCommit: (LiquidGlassTuning) -> Unit,
    onNavTuningChange: (NavGlassTuning) -> Unit,
    onBack: () -> Unit,
) {

    var dragging by remember { mutableStateOf(false) }
    val navChange: (NavGlassTuning) -> Unit = {
        onNavTuningChange(it.copy(interactiveDegrade = false))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("玻璃效果实验室", fontWeight = FontWeight.Medium) },
                navigationIcon = {
                    IconButton(onClick = {
                        onTuningCommit(tuning)
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        containerColor = Color.Transparent,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier

                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .testTag("lab:preview:root")
                    .padding(
                        start = UiSpacing.Xxl,
                        top = UiSpacing.Sm,
                        end = UiSpacing.Xxl,
                        bottom = UiSpacing.Lg,
                    ),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.Lg),
            ) {
                TopBar(
                    backdrop = LocalLiquidCardBackdrop.current,
                    modifier = Modifier.fillMaxWidth().testTag("lab:preview:topBar"),
                )
                LiquidCard(modifier = Modifier.fillMaxWidth().testTag("lab:preview:balanceCard")) {
                    Column(modifier = Modifier.padding(UiSpacing.Xl)) {
                        Text(
                            text = "示例余额卡片",
                            modifier = Modifier.testTag("lab:preview:balanceCard:first"),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "¥ 123.45",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "圆角、透明度、模糊与折射会实时变化",
                            modifier = Modifier.testTag("lab:preview:balanceCard:last"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                SettingsActionButton(
                    text = "示例玻璃按钮",
                    onClick = {},
                    modifier = Modifier.fillMaxWidth().testTag("lab:preview:sampleButton"),
                )
            }
            GlassLabControlPanel(
                tuning = tuning,
                navTuning = navTuning,
                dragging = dragging,
                onDraggingChange = { dragging = it },
                onTuningChange = onTuningChange,
                onTuningCommit = onTuningCommit,
                onNavTuningChange = navChange,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = UiSpacing.Xxl, end = UiSpacing.Xxl, bottom = 92.dp)
                    .testTag("lab:panel:root"),
            )
        }
    }
}

@Composable
private fun GlassLabControlPanel(
    tuning: LiquidGlassTuning,
    navTuning: NavGlassTuning,
    dragging: Boolean,
    onDraggingChange: (Boolean) -> Unit,
    onTuningChange: (LiquidGlassTuning) -> Unit,
    onTuningCommit: (LiquidGlassTuning) -> Unit,
    onNavTuningChange: (NavGlassTuning) -> Unit,

    modifier: Modifier = Modifier,
) {
    val panelColor = if (isSystemInDarkTheme()) {
        Color(0xFF15151B).copy(alpha = 0.88f)
    } else {
        Color.White.copy(alpha = 0.86f)
    }

    Column(
        modifier = modifier.fillMaxWidth().drawBackdrop(
            backdrop = LocalLiquidCardBackdrop.current,
            shape = { RoundedCornerShape(UiRadius.Glass) },
            effects = {
                vibrancy()
                blur(4.dp.toPx())
                lens(16.dp.toPx(), 32.dp.toPx())
            },
            highlight = { Highlight.Plain },
            onDrawSurface = { drawRect(panelColor) },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = UiSpacing.Lg, end = UiSpacing.Sm, top = UiSpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "玻璃参数",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.testTag("lab:panel:title"),
            )
            Spacer(modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    onTuningCommit(LiquidGlassTuning())
                    onNavTuningChange(NavGlassTuning())
                },
                modifier = Modifier.testTag("lab:panel:reset"),
            ) {
                Text("重置")
            }
        }
        GlassLabPresetChip(
            text = "官方清透",
            onClick = { onTuningCommit(LiquidGlassTuning.officialClear()) },
            modifier = Modifier
                .padding(start = UiSpacing.Lg)
                .testTag("lab:panel:preset:officialClear"),
        )
        CompositionLocalProvider(LocalSliderDraggingChanged provides { onDraggingChange(it) }) {
            LazyColumn(
                modifier = Modifier.weight(1f).testTag("lab:panel:list"),
                userScrollEnabled = !dragging,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(UiSpacing.Lg),
            ) {
                item {
                    GlassLabGroup(
                        title = "全局玻璃材质",
                        modifier = Modifier.testTag("lab:panel:group:material"),
                    ) {
                        GlassSlider(
                            label = "卡片圆角",
                            modifier = Modifier
                                .padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md)
                                .testTag("lab:panel:slider:cornerRadius"),
                            hint = "只调整卡片圆角，顶栏、按钮和导航保持胶囊形状",
                            value = tuning.cornerRadiusDp,
                            valueRange = 0f..48f,
                            unit = "dp",
                            onChange = { onTuningChange(tuning.copy(cornerRadiusDp = it)) },
                            onValueChangeFinished = { onTuningCommit(tuning.copy(cornerRadiusDp = it)) },
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "背景模糊",
                            hint = "0dp 最清透；过高会降低背景细节",
                            value = tuning.blurDp,
                            valueRange = 0f..8f,
                            unit = "dp",
                            onChange = { onTuningChange(tuning.copy(blurDp = it)) },
                            onValueChangeFinished = { onTuningCommit(tuning.copy(blurDp = it)) },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "折射高度",
                            hint = "玻璃边缘的折射带宽",
                            value = tuning.refractionHeightDp,
                            valueRange = 0f..24f,
                            unit = "dp",
                            onChange = { onTuningChange(tuning.copy(refractionHeightDp = it)) },
                            onValueChangeFinished = { onTuningCommit(tuning.copy(refractionHeightDp = it)) },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "折射强度",
                            hint = "玻璃边缘内容的弯曲程度",
                            value = tuning.refractionAmountDp,
                            valueRange = 0f..36f,
                            unit = "dp",
                            onChange = { onTuningChange(tuning.copy(refractionAmountDp = it)) },
                            onValueChangeFinished = { onTuningCommit(tuning.copy(refractionAmountDp = it)) },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "表面蒙层",
                            hint = "0% 最透明；复杂背景上可适当提高以保护文字",
                            value = tuning.surfaceAlpha,
                            valueRange = 0f..0.24f,
                            unit = "%",
                            onChange = { onTuningChange(tuning.copy(surfaceAlpha = it)) },
                            onValueChangeFinished = { onTuningCommit(tuning.copy(surfaceAlpha = it)) },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassLabToggle(
                            label = "彩色色散",
                            checked = tuning.chromaticAberration,
                            onCheckedChange = { onTuningCommit(tuning.copy(chromaticAberration = it)) },
                        )
                        GlassLabDivider()
                        GlassLabToggle(
                            label = "深度效果",
                            checked = tuning.depthEffect,
                            onCheckedChange = { onTuningCommit(tuning.copy(depthEffect = it)) },
                        )
                        GlassLabDivider()
                        GlassLabToggle(
                            label = "Vibrancy",
                            checked = tuning.vibrancy,
                            onCheckedChange = { onTuningCommit(tuning.copy(vibrancy = it)) },
                        )
                        if (Build.VERSION.SDK_INT < 33) {
                            Text(
                                text = "当前系统不支持完整镜头折射与深度效果，模糊和蒙层仍可调节。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            )
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(UiSpacing.Xxl))
                    GlassLabGroup(
                        title = "导航交互",
                        modifier = Modifier.testTag("lab:panel:group:nav"),
                    ) {
                        GlassLabToggle(
                            label = "段落震动反馈",
                            checked = navTuning.detentHaptics,
                            onCheckedChange = {
                                onNavTuningChange(navTuning.copy(detentHaptics = it, interactiveDegrade = false))
                            },
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "导航栏高度",
                            hint = "只调整导航背景板高度，胶囊和图标保持 56dp",
                            value = navTuning.barHeightDp,
                            valueRange = 60f..104f,
                            unit = "dp",
                            onChange = { onNavTuningChange(navTuning.copy(barHeightDp = it)) },
                            onValueChangeFinished = {
                                onNavTuningChange(navTuning.copy(barHeightDp = it, interactiveDegrade = false))
                            },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "按压上下外移",
                            hint = "按住导航胶囊时上下边框的外移量",
                            value = navTuning.pressStretchVDp,
                            valueRange = -24f..24f,
                            unit = "dp",
                            onChange = { onNavTuningChange(navTuning.copy(pressStretchVDp = it)) },
                            onValueChangeFinished = {
                                onNavTuningChange(navTuning.copy(pressStretchVDp = it, interactiveDegrade = false))
                            },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "按压左右外移",
                            hint = "按住导航胶囊时左右边框的外移量",
                            value = navTuning.pressStretchHDp,
                            valueRange = -24f..24f,
                            unit = "dp",
                            onChange = { onNavTuningChange(navTuning.copy(pressStretchHDp = it)) },
                            onValueChangeFinished = {
                                onNavTuningChange(navTuning.copy(pressStretchHDp = it, interactiveDegrade = false))
                            },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "切换粘滞强度",
                            hint = "导航拖动时的磁吸档位感",
                            value = navTuning.detentStrength,
                            valueRange = 0f..0.9f,
                            unit = "%",
                            onChange = { onNavTuningChange(navTuning.copy(detentStrength = it)) },
                            onValueChangeFinished = {
                                onNavTuningChange(navTuning.copy(detentStrength = it, interactiveDegrade = false))
                            },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                        GlassLabDivider()
                        GlassSlider(
                            label = "段落量化",
                            hint = "0 为连续跟手，1 为按档位跳变",
                            value = navTuning.detentQuantize,
                            valueRange = 0f..1f,
                            unit = "%",
                            onChange = { onNavTuningChange(navTuning.copy(detentQuantize = it)) },
                            onValueChangeFinished = {
                                onNavTuningChange(navTuning.copy(detentQuantize = it, interactiveDegrade = false))
                            },
                            modifier = Modifier.padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
                            framed = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassLabGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(UiSpacing.Sm))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(UiSpacing.Xl))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.10f)),
            content = content,
        )
    }
}

@Composable
private fun GlassLabDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = UiSpacing.Lg),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
    )
}

@Composable
private fun GlassLabPresetChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = UiSpacing.Md, vertical = UiSpacing.Xs),
    )
}

@Composable
private fun GlassLabToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.Lg, vertical = UiSpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 15.sp)
        Spacer(modifier = Modifier.width(16.dp))
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
        )
    }
}