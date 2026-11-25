package com.deepseek.balance.ui

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * compose-ui-consistency-review 阶段 3：玻璃效果实验室 G-01 几何量化。
 * 只读取边界框并输出数值，不使用截图判断布局。
 */
class GlassPlaygroundGeometryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private data class Snapshot(
        val button: DpRect,
        val panel: DpRect,
        val preview: DpRect,
        val card: DpRect,
    ) {
        val overlapAreaDp2: Float
            get() {
                val width = max(0f, min(button.right.value, panel.right.value) - max(button.left.value, panel.left.value))
                val height = max(0f, min(button.bottom.value, panel.bottom.value) - max(button.top.value, panel.top.value))
                return width * height
            }

        val buttonFullyInsidePreview: Boolean
            get() = button.left >= preview.left - 0.5.dp && button.right <= preview.right + 0.5.dp &&
                button.top >= preview.top - 0.5.dp && button.bottom <= preview.bottom + 0.5.dp

        val cardPanelLeftDeltaDp: Float get() = abs(card.left.value - panel.left.value)
        val cardPanelRightDeltaDp: Float get() = abs(card.right.value - panel.right.value)

        override fun toString(): String =
            "button=$button panel=$panel preview=$preview card=$card overlapDp2=$overlapAreaDp2 fullyVisible=$buttonFullyInsidePreview"
    }

    private fun setLab(size: DpSize? = null, fontScale: Float? = null) {
        composeRule.setContent {
            val backdrop = rememberLayerBackdrop()
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalLiquidCardBackdrop provides backdrop,
                LocalDensity provides Density(currentDensity.density, fontScale ?: currentDensity.fontScale),
            ) {
                // 固定尺寸容器模拟可用空间；字体缩放通过 LocalDensity 注入，避免依赖设备截图或额外测试 API。
                Box(modifier = if (size != null) Modifier.requiredSize(size) else Modifier.fillMaxSize()) {
                    GlassPlaygroundPage(
                        tuning = LiquidGlassTuning(),
                        navTuning = NavGlassTuning(),
                        onTuningChange = {},
                        onTuningCommit = {},
                        onNavTuningChange = {},
                        onBack = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun snapshot(): Snapshot {
        composeRule.waitForIdle()
        return Snapshot(
            button = composeRule.onNodeWithTag("lab:preview:sampleButton").getUnclippedBoundsInRoot(),
            panel = composeRule.onNodeWithTag("lab:panel:root").getUnclippedBoundsInRoot(),
            preview = composeRule.onNodeWithTag("lab:preview:root").getUnclippedBoundsInRoot(),
            card = composeRule.onNodeWithTag("lab:preview:balanceCard").getUnclippedBoundsInRoot(),
        )
    }

    private fun assertNoOverlap(snapshot: Snapshot, config: String) {
        Log.i(TAG, "$config -> $snapshot")
        assertEquals("$config 示例按钮与参数面板不得相交: $snapshot", 0f, snapshot.overlapAreaDp2, 0.5f)
        assertEquals("$config 卡片与面板左边缘对齐: $snapshot", 0f, snapshot.cardPanelLeftDeltaDp, 0.5f)
        assertEquals("$config 卡片与面板右边缘对齐: $snapshot", 0f, snapshot.cardPanelRightDeltaDp, 0.5f)
    }

    private fun assertVisibleOrReachable(config: String) {
        val before = snapshot()
        if (before.buttonFullyInsidePreview) return
        composeRule.onNodeWithTag("lab:preview:root").performScrollToNode(hasTestTag("lab:preview:sampleButton"))
        composeRule.waitForIdle()
        val after = snapshot()
        Log.i(TAG, "$config after scroll -> $after")
        assertTrue("$config 滚动后示例按钮应完整可见: $after", after.buttonFullyInsidePreview)
    }

    @Test
    fun expanded_default_360x800_noOverlap_andButtonFullyVisible() {
        setLab(DpSize(360.dp, 800.dp))
        val snapshot = snapshot()
        assertNoOverlap(snapshot, "expanded/360x800/font1.0")
        assertTrue("默认配置下示例按钮必须完整位于预览视口内: $snapshot", snapshot.buttonFullyInsidePreview)
    }

    @Test
    fun expanded_small_360x640_noOverlap_andReachable() {
        setLab(DpSize(360.dp, 640.dp))
        assertNoOverlap(snapshot(), "expanded/360x640")
        assertVisibleOrReachable("expanded/360x640")
    }

    @Test
    fun expanded_large_411x891_noOverlap_andReachable() {
        setLab(DpSize(411.dp, 891.dp))
        assertNoOverlap(snapshot(), "expanded/411x891")
        assertVisibleOrReachable("expanded/411x891")
    }

    @Test
    fun collapsed_360x800_noOverlap_andButtonFullyVisible() {
        setLab(DpSize(360.dp, 800.dp))
        composeRule.onNodeWithTag("lab:panel:expandButton").performClick()
        composeRule.waitForIdle()
        val snapshot = snapshot()
        assertNoOverlap(snapshot, "collapsed/360x800")
        assertTrue("收起时示例按钮必须完整可见: $snapshot", snapshot.buttonFullyInsidePreview)
    }

    @Test
    fun expanded_fontScale13_noOverlap_andReachable() {
        setLab(DpSize(360.dp, 800.dp), fontScale = 1.3f)
        assertNoOverlap(snapshot(), "expanded/360x800/font1.3")
        assertVisibleOrReachable("expanded/360x800/font1.3")
    }

    private companion object {
        const val TAG = "UiGeometry"
    }
}