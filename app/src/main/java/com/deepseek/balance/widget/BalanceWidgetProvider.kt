package com.deepseek.balance.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import com.deepseek.balance.MainActivity
import com.deepseek.balance.R

class BalanceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
            // 系统定时（最短 30 分钟）触发时，真正去拉取最新余额
            WidgetUpdateService.enqueueWork(
                context,
                Intent(context, WidgetUpdateService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                },
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (ACTION_REFRESH == intent.action) {
            // 触发刷新：启动 WidgetUpdateService
            WidgetUpdateService.enqueueWork(
                context,
                Intent(context, WidgetUpdateService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))
                },
            )
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.deepseek.balance.WIDGET_REFRESH"
        const val KEY_BALANCE = "balance"
        const val KEY_SYMBOL = "symbol"
        const val KEY_GRANTED = "granted"
        const val KEY_TOPPED_UP = "topped_up"
        const val KEY_AVAILABLE = "available"
        const val KEY_UPDATE_TIME = "update_time"
        const val KEY_TOTAL_TOKENS = "total_tokens"
        const val KEY_TODAY_TOKENS = "today_tokens"

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val prefs = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_balance)

            // 金额（居中，看余额即可判断是否可用，不再单独显示状态）
            val balance = prefs.getString(KEY_BALANCE, null)
            if (balance != null) {
                views.setTextViewText(R.id.widget_balance, "${prefs.getString(KEY_SYMBOL, "¥")}$balance")
            } else {
                views.setTextViewText(R.id.widget_balance, "点击查询")
            }

            val updateTime = prefs.getString(KEY_UPDATE_TIME, null)
            if (updateTime != null) {
                views.setTextViewText(R.id.widget_update_time, updateTime)
            } else {
                views.setTextViewText(R.id.widget_update_time, "")
            }

            // 总 / 今日 Tokens（用量数据，需网页令牌；紧凑单位，标签已在布局中）
            val totalTokens = prefs.getString(KEY_TOTAL_TOKENS, null)
            val todayTokens = prefs.getString(KEY_TODAY_TOKENS, null)
            views.setTextViewText(R.id.widget_total_tokens, totalTokens ?: "--")
            views.setTextViewText(R.id.widget_today_tokens, todayTokens ?: "--")

            // 点击小组件任意处（除刷新按钮）打开 App
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            // 点击刷新按钮
            val refreshIntent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, appWidgetId, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
