package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.deepseek.balance.network.ApiClient
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
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
                )
                .apply()
            updateAllWidgets(context)
            true
        } catch (_: Exception) {
            false
        }
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
