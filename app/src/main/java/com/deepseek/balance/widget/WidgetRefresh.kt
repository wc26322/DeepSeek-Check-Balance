package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.deepseek.balance.model.UsageData
import com.deepseek.balance.network.ApiClient
import com.deepseek.balance.network.UsageClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 小组件数据刷新的共享逻辑：
 * 1) 调用 DeepSeek 余额接口
 * 2) 写入 SharedPreferences（小组件读取源）
 * 3) 刷新所有已添加的小组件实例
 */
object WidgetRefresh {

    private const val PREFS = "deepseek_balance"

    /** 单次手动刷新的硬超时：到点强制收尾，避免「刷新中…」和并发守卫一起卡死 */
    private const val REFRESH_TIMEOUT_MS = 15_000L

    /** 手动刷新的进程内并发守卫：已经在刷就直接忽略，避免连点排队、避免重复播动画 */
    private val manualRefreshing = AtomicBoolean(false)

    /**
     * 手动刷新入口：由小组件右下角刷新按钮的广播触发。
     *
     * 关键：借用广播的 goAsync 窗口在当前进程内直接跑，不再等 JobScheduler 排期。
     * 流程：先立刻把小组件渲染成「刷新中…」（点击反馈）→ 拉数据 → 恢复 → finish()。
     *
     * @param pendingResult 来自 AppWidgetProvider.goAsync()；结束时必须 finish()，
     *                      否则系统会一直认为这条广播没处理完。
     */
    fun refreshFromBroadcast(
        context: Context,
        pendingResult: BroadcastReceiver.PendingResult,
        appWidgetId: Int,
    ) {
        if (!manualRefreshing.compareAndSet(false, true)) {
            widgetLog("已有手动刷新在跑，忽略本次点击 id=$appWidgetId")
            pendingResult.finish()
            return
        }
        widgetLog("手动刷新开始 id=$appWidgetId")

        // 第一步：立刻刷新界面，让「点了有反应」可见（此时还没发起网络请求）
        markRefreshing(context, true)
        BalanceWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), null)

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scope.launch {
            val ok: Boolean = try {
                // 15 秒兜底：网络 hang 死也不能让「刷新中…」卡住、不能堵死后续点击
                kotlinx.coroutines.withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
                    refresh(context, animate = true)
                } ?: false
            } catch (t: Throwable) {
                widgetLog("手动刷新异常: ${t.javaClass.simpleName}: ${t.message}")
                false
            }
            try {
                markRefreshing(context, false)
                // 收尾：无论成功失败都恢复成正常状态（失败时保留旧数据 + 旧时间）
                BalanceWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), null)
            } finally {
                manualRefreshing.set(false)
                pendingResult.finish()
            }
            widgetLog("手动刷新结束 ok=$ok id=$appWidgetId")
        }
    }

    /** 写入 / 清除「正在刷新」状态（带过期时间，进程被杀也不会永久卡在刷新中） */
    private fun markRefreshing(context: Context, refreshing: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(
                BalanceWidgetProvider.KEY_REFRESHING_UNTIL,
                if (refreshing) {
                    System.currentTimeMillis() + BalanceWidgetProvider.REFRESHING_TIMEOUT_MS
                } else {
                    0L
                },
            )
            .apply()
    }

    /**
     * 拉取最新余额并写回存储。返回是否成功。
     * 失败时保留旧数据，不抛异常。
     * @param animate 是否播放 0 → 当前值 滚动动画（仅手动刷新时传 true，自动刷新不打扰）
     */
    fun refresh(context: Context, animate: Boolean = false): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isBlank()) {
            widgetLog("refresh: apiKey 为空，直接返回 false")
            return false
        }

        return try {
            // 用量接口（网页用量）数据量大、明显慢于余额接口。以前是串行「余额 → 用量 → 才更新小组件」，
            // 点一次要等两轮请求，这就是「点了特别久才刷新」的主因。
            // 现在改成：用量与余额**并行**发，用量完成后自己再补刷一次小组件。
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                refreshTokens(context)
                // 动画还在跑时别插手，免得把滚动中的数字直接顶成最终值
                if (!WidgetCountUpAnimator.isRunning()) updateAllWidgets(context)
            }

            val response = kotlinx.coroutines.runBlocking { ApiClient.getBalance(apiKey) }
            val cny = response.balanceInfos.find { it.currency == "CNY" }
            prefs.edit()
                .putString(BalanceWidgetProvider.KEY_BALANCE, cny?.totalBalance ?: "0.00")
                .putString(BalanceWidgetProvider.KEY_SYMBOL, cny?.symbol ?: "¥")
                .putString(BalanceWidgetProvider.KEY_GRANTED, cny?.grantedBalance ?: "0.00")
                .putString(BalanceWidgetProvider.KEY_TOPPED_UP, cny?.toppedUpBalance ?: "0.00")
                .putBoolean(BalanceWidgetProvider.KEY_AVAILABLE, response.isAvailable)
                .putString(
                    BalanceWidgetProvider.KEY_UPDATE_TIME,
                    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
                )
                .apply()
            // 余额一到就立刻展示 + 播动画，不再等用量
            updateAllWidgets(context)
            // 仅手动刷新播放 0 → 当前值 滚动动画（起点固定为 0：即使数据没变也有明显的滚动反馈）
            if (animate) WidgetCountUpAnimator.start(context)
            true
        } catch (e: Exception) {
            widgetLog("refresh 异常: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** 拉取用量并保存总/今日 Tokens（需网页令牌；失败保留旧值） */
    fun refreshTokens(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val webToken = prefs.getString("web_token", "") ?: ""
        if (webToken.isBlank()) return
        try {
            val usage = kotlinx.coroutines.runBlocking { UsageClient.getUsage(webToken) }
            saveTokens(prefs, usage)
        } catch (_: Exception) {
        }
    }

    /** 计算并保存「总 Tokens」与「今日 Tokens」到小组件存储（具体数值）。
     *  平台按 UTC+0 切天，「今日」取每日数据里最新的一天（当前 UTC 日），
     *  而非本地日期，避免本地时间超前 UTC 时今日显示 0。 */
    fun saveTokens(prefs: SharedPreferences, usage: UsageData) {
        val lastDate = usage.byModelDaily
            .firstNotNullOfOrNull { it.daily.lastOrNull()?.date }
            ?: ""
        val todayTokens = usage.byModelDaily.sumOf { m ->
            m.daily.lastOrNull()?.takeIf { it.date == lastDate }?.totalTokens ?: 0L
        }
        prefs.edit()
            .putString(BalanceWidgetProvider.KEY_TOTAL_TOKENS, "%,d".format(Locale.US, usage.totalTokens))
            .putString(BalanceWidgetProvider.KEY_TODAY_TOKENS, "%,d".format(Locale.US, todayTokens))
            .apply()
    }

    /** 刷新所有已添加的小组件实例 */
    fun updateAllWidgets(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, BalanceWidgetProvider::class.java),
        )
        ids.forEach { BalanceWidgetProvider.updateWidget(context, manager, it) }
    }
}
