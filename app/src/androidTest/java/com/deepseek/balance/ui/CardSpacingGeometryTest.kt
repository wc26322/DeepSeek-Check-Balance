package com.deepseek.balance.ui

import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.Box
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
import com.deepseek.balance.model.BalanceInfo
import com.deepseek.balance.model.BalanceResponse
import com.deepseek.balance.model.KeyUsage
import com.deepseek.balance.model.ModelUsage
import com.deepseek.balance.model.UsageData
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** 卡片可见间距与首尾留白门禁：使用 fake 数据，不使用真实凭证或截图。 */
class CardSpacingGeometryTest {
    @get:Rule
    val composeRule = createComposeRule()

    private fun environment(
        darkTheme: Boolean = false,
        fontScale: Float = 1.0f,
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
                Box(Modifier.requiredSize(DpSize(360.dp, 800.dp))) { content() }
            }
        }
        composeRule.waitForIdle()
    }

    private fun bounds(tag: String): DpRect = composeRule.onNodeWithTag(tag).getUnclippedBoundsInRoot()

    private fun assertGap(ruleName: String, previousTag: String, nextTag: String, expectedDp: Float) {
        val previous = bounds(previousTag)
        val next = bounds(nextTag)
        val gap = (next.top - previous.bottom).value
        Log.i(TAG, "$ruleName $previousTag -> $nextTag gap=$gap")
        assertEquals("$ruleName: $previousTag -> $nextTag", expectedDp, gap, 0.5f)
    }

    private fun assertVerticalInsets(cardTag: String, firstTag: String, lastTag: String, expectedDp: Float) {
        val card = bounds(cardTag)
        val first = bounds(firstTag)
        val last = bounds(lastTag)
        val top = (first.top - card.top).value
        val bottom = (card.bottom - last.bottom).value
        Log.i(TAG, "$cardTag top=$top bottom=$bottom")
        assertEquals("$cardTag 首内容留白", expectedDp, top, 0.5f)
        assertEquals("$cardTag 末内容留白", expectedDp, bottom, 0.5f)
    }

    private fun fakeBalance() = BalanceResponse(
        isAvailable = true,
        balanceInfos = listOf(BalanceInfo("CNY", "123.45", "23.45", "100.00")),
    )

    private fun fakeUsage() = UsageData(
        totalCostCny = 19.20,
        apiCalls = 2895,
        totalTokens = 478218898L,
        windowDays = 30,
        byModel = listOf(ModelUsage("deepseek-chat", 10, 1000, 0.2)),
        byKey = listOf(KeyUsage("test", "track", "sensitive", true, 10, 1000, 0.2)),
    )

    private fun homeContent(
        result: BalanceResponse?,
        usage: UsageData?,
        errorMessage: String? = null,
    ): @Composable () -> Unit = {
        MainScreen(
            apiKey = "sk-test", onSettingsClick = {}, onQueryClick = {}, isLoading = false,
            result = result, errorMessage = errorMessage, lastQueryTime = "17:40",
            usage = usage, usageError = null, hasWebToken = true,
        )
    }

    private fun assertHomeSuccessSpacing() {
        assertGap("余额卡到辅助时间", "home:balanceCard", "home:lastUpdate", 8f)
        assertGap("辅助时间到用量区", "home:lastUpdate", "home:usageSection", 16f)
        assertVerticalInsets("home:balanceCard", "home:balanceCard:first", "home:balanceCard:stats", 20f)
        assertVerticalInsets("home:usageOverview", "home:usageOverview:first", "home:usageOverview:last", 20f)
    }

    @Test
    fun home_success_cardGapsAndVerticalInsets() {
        environment { homeContent(fakeBalance(), fakeUsage())() }
        assertHomeSuccessSpacing()
    }

    @Test
    fun home_success_darkTheme_sameSpacing() {
        environment(darkTheme = true) { homeContent(fakeBalance(), fakeUsage())() }
        assertHomeSuccessSpacing()
    }

    @Test
    fun home_success_fontScale13_sameSpacing() {
        environment(fontScale = 1.3f) { homeContent(fakeBalance(), fakeUsage())() }
        assertHomeSuccessSpacing()
    }

    @Test
    fun home_empty_allVisibleCardsUse16DpGaps() {
        environment { homeContent(result = null, usage = null)() }
        assertGap("空态时间到提示卡", "home:lastUpdate", "home:emptyState", 16f)
        assertGap("空态提示卡到用量提示", "home:emptyState", "home:usageSection", 16f)
    }

    @Test
    fun home_error_errorCardSeparatedBy16Dp() {
        environment { homeContent(fakeBalance(), fakeUsage(), errorMessage = "网络错误")() }
        assertGap("用量区到错误卡", "home:usageSection", "home:errorCard", 16f)
    }

    @Test
    fun settings_allVisibleCardsHave16DpGaps_beforeAndAfterScroll() {
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
        val tags = listOf("api", "webtoken", "alert", "realtime", "background", "navglass", "about").map { "settings:card:$it" }
        fun assertVisibleGaps(stage: String) {
            val visible = tags.mapNotNull { tag ->
                if (composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()) tag to bounds(tag) else null
            }.sortedBy { it.second.top.value }
            visible.zipWithNext().forEach { (previous, next) ->
                val gap = (next.second.top - previous.second.bottom).value
                Log.i(TAG, "settings $stage ${previous.first} -> ${next.first} gap=$gap")
                assertEquals("设置页 $stage ${previous.first} -> ${next.first}", 16f, gap, 0.5f)
            }
        }
        assertVisibleGaps("初始可见卡片")
    }

    @Test
    fun lab_preview_cardsHave16DpGapsAnd20DpInsets() {
        environment {
            GlassPlaygroundPage(LiquidGlassTuning(), NavGlassTuning(), {}, {}, {}, {})
        }
        assertGap("实验室顶栏到示例卡", "lab:preview:topBar", "lab:preview:balanceCard", 16f)
        assertGap("实验室示例卡到按钮", "lab:preview:balanceCard", "lab:preview:sampleButton", 16f)
        assertVerticalInsets("lab:preview:balanceCard", "lab:preview:balanceCard:first", "lab:preview:balanceCard:last", 20f)
    }

    private companion object { const val TAG = "UiGeometry" }
}