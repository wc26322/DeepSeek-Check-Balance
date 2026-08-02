package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import com.deepseek.balance.model.UsageData
import com.deepseek.balance.network.ApiClient
import com.deepseek.balance.network.UsageClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 小组件数据刷新的共享逻辑：
 * 1) 调用 DeepSeek 余额接口
 * 2) 写入 SharedPreferences（小组件读取源）
 * 3) 刷新所有已添加的小组件实例
 */
object WidgetRefresh {

    /**
     * 拉取最新余额并写回存储。返回是否成功。
     * 失败时保留旧数据，不抛异常。
     */
    fun refresh(context: Context): Boolean {
        val prefs = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isBlank()) return false

        return try {
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
            // 用量（总/今日 Tokens）：需要网页令牌，失败保留旧值，不影响余额刷新
            refreshTokens(context)
            updateAllWidgets(context)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** 拉取用量并保存总/今日 Tokens（需网页令牌；失败保留旧值） */
    fun refreshTokens(context: Context) {
        val prefs = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
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
