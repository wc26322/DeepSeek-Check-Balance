package com.deepseek.balance.ui

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.deepseek.balance.model.KeyUsage
import com.deepseek.balance.model.ModelUsage
import com.deepseek.balance.model.UsageData
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

/** 核心页面一致性与深色矩阵；全部结论来自渲染后的 DpRect，不使用截图。 */
class AppUiConsistencyGeometryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun environment(
        size: DpSize = DpSize(360.dp, 800.dp),
        fontScale: Float = 1.0f,
        darkTheme: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        composeRule.setContent {
            val backdrop = rememberLayerBackdrop()
            val density = LocalDensity.current
            val configuration = Configuration(LocalConfiguration.current).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                    if (darkTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            }
            CompositionLocalProvider(
                LocalLiquidCardBackdrop provides backdrop,
                LocalDensity provides Density(density.density, fontScale),
                LocalConfiguration provides configuration,
            ) {
                Box(Modifier.requiredSize(size)) { content() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect =
        composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun assertSymmetricInsets(contentTag: String, childTag: String, expectedDp: Float) {
        val content = bounds(contentTag)
        val child = bounds(childTag)
        val left = child.left - content.left
        val right = content.right - child.right
        Log.i(TAG, "$contentTag/$childTag left=$left right=$right")
        assertEquals("$childTag 左页面边距", expectedDp, left.value, 0.5f)
        assertEquals("$childTag 右页面边距", expectedDp, right.value, 0.5f)
    }

    @Test
    fun home_empty_usageSectionHasSymmetric24DpInsets() {
        environment {
            MainScreen(
                apiKey = "", onSettingsClick = {}, onQueryClick = {}, isLoading = false,
                result = null, errorMessage = null, lastQueryTime = null,
                usage = null, usageError = null, hasWebToken = false,
            )
        }
        assertSymmetricInsets("home:content", "home:usageSection", 24f)
    }

    @Test
    fun home_fakeUsage_successStateHasSymmetricInsets() {
        environment {
            MainScreen(
                apiKey = "sk-test", onSettingsClick = {}, onQueryClick = {}, isLoading = false,
                result = null, errorMessage = null, lastQueryTime = "17:40",
                usage = UsageData(
                    totalCostCny = 19.20,
                    apiCalls = 2895,
                    totalTokens = 478218898L,
                    windowDays = 30,
                    byModel = listOf(ModelUsage("deepseek-chat", 10, 1000, 0.2)),
                    byKey = listOf(KeyUsage("test", "track", "sensitive", true, 10, 1000, 0.2)),
                ),
                usageError = null, hasWebToken = true,
            )
        }
        assertSymmetricInsets("home:content", "home:usageSection", 24f)
    }

    @Test
    fun settings_visibleCardsShareIdenticalHorizontalBounds() {
        environment {
            SettingsScreen(
                apiKey = "", onApiKeyChange = {}, webToken = "", onWebTokenChange = {},
                onWebLoginClick = {}, onBackClick = {}, alertEnabled = false,
                onAlertEnabledChange = {}, alertThreshold = "50", onAlertThresholdChange = {},
                widgetRealtime = false, onWidgetRealtimeChange = {}, widgetIntervalSec = 60,
                onWidgetIntervalSecChange = {}, backgroundImagePath = "", onBackgroundImageChange = {},
                liquidGlassTuning = LiquidGlassTuning(), onLiquidGlassTuningChange = {},
                onLiquidGlassTuningCommit = {}, navGlassTuning = NavGlassTuning(), onNavGlassTuningChange = {},
            )
        }
        val tags = listOf("api", "webtoken", "alert", "realtime", "background", "navglass", "about")
        val measured = mutableListOf<Pair<String, DpRect>>()
        tags.forEach { name ->
            val tag = "settings:card:$name"
            if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) {
                measured += name to bounds(tag)
            }
        }
        assertTrue("设置页至少应测量到 3 张卡片，实际 ${measured.size}", measured.size >= 3)
        val reference = measured.first().second
        measured.forEach { (name, rect) ->
            Log.i(TAG, "settings card $name -> $rect")
            assertEquals("$name 左边缘与首张卡片一致", reference.left.value, rect.left.value, 0.5f)
            assertEquals("$name 右边缘与首张卡片一致", reference.right.value, rect.right.value, 0.5f)
        }
        composeRule.onNodeWithTag("settings:list").performScrollToNode(hasTestTag("settings:card:about"))
        composeRule.waitForIdle()
        val about = bounds("settings:card:about")
        assertEquals("滚动到底后 about 左边缘一致", reference.left.value, about.left.value, 0.5f)
        assertEquals("滚动到底后 about 右边缘一致", reference.right.value, about.right.value, 0.5f)
    }

    private fun assertBottomTabsEqualWidth(darkTheme: Boolean) {
        environment(size = DpSize(360.dp, 120.dp), darkTheme = darkTheme) {
            BottomTabs(
                backdrop = LocalLiquidCardBackdrop.current,
                isSettings = false,
                onTabSelected = {},
                modifier = Modifier.fillMaxSize().testTag("nav:bottom:root"),
            )
        }
        val homeNodes = composeRule.onAllNodesWithTag("nav:bottom:item:home").fetchSemanticsNodes()
        val settingsNodes = composeRule.onAllNodesWithTag("nav:bottom:item:settings").fetchSemanticsNodes()
        assertEquals("home 标签应唯一", 1, homeNodes.size)
        assertEquals("settings 标签应唯一", 1, settingsNodes.size)
        val home = bounds("nav:bottom:item:home")
        val settings = bounds("nav:bottom:item:settings")
        Log.i(TAG, "bottomTabs dark=$darkTheme home=$home settings=$settings")
        assertEquals("底部导航两项等宽 dark=$darkTheme", (home.right - home.left).value, (settings.right - settings.left).value, 0.5f)
    }

    @Test
    fun bottomTabs_light_haveEqualWidthTabs() = assertBottomTabsEqualWidth(false)

    @Test
    fun bottomTabs_dark_haveEqualWidthTabs() = assertBottomTabsEqualWidth(true)

    @Test
    fun lab_panelContent_leftAligned() {
        environment {
            GlassPlaygroundPage(LiquidGlassTuning(), NavGlassTuning(), {}, {}, {}, {})
        }
        val title = bounds("lab:panel:title")
        val group = bounds("lab:panel:group:material")
        Log.i(TAG, "lab title=$title group=$group")
        assertEquals("面板标题与分组容器左边缘一致", title.left.value, group.left.value, 0.5f)
    }

    @Test
    fun lab_darkTheme_noOverlap_andCardPanelAligned() {
        environment(darkTheme = true) {
            GlassPlaygroundPage(LiquidGlassTuning(), NavGlassTuning(), {}, {}, {}, {})
        }
        val button = bounds("lab:preview:sampleButton")
        val panel = bounds("lab:panel:root")
        val card = bounds("lab:preview:balanceCard")
        val overlapHeight = minOf(button.bottom, panel.top).let { button.bottom - panel.top }
        Log.i(TAG, "lab dark button=$button panel=$panel card=$card")
        assertTrue("深色模式下面板不得覆盖按钮", overlapHeight.value <= 0.5f)
        assertEquals("深色模式左右边缘对齐", card.left.value, panel.left.value, 0.5f)
        assertEquals("深色模式右右边缘对齐", card.right.value, panel.right.value, 0.5f)
    }

    private companion object { const val TAG = "UiGeometry" }
}