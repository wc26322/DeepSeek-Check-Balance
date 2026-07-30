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

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val prefs = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
            val views = RemoteViews(context.packageName, R.layout.widget_balance)

            // 金额（大号居中）
            val balance = prefs.getString(KEY_BALANCE, null)
            if (balance != null) {
                views.setTextViewText(R.id.widget_balance, "${prefs.getString(KEY_SYMBOL, "¥")}$balance")

                val isAvailable = prefs.getBoolean(KEY_AVAILABLE, true)
                views.setTextViewText(R.id.widget_status, if (isAvailable) "● 可用" else "● 余额不足")
                views.setTextColor(R.id.widget_status,
                    if (isAvailable) 0xFF22C55E.toInt()
                    else 0xFFEF4444.toInt())
            } else {
                views.setTextViewText(R.id.widget_balance, "点击查询")
                views.setTextViewText(R.id.widget_status, "设置 API Key")
                views.setTextColor(R.id.widget_status, 0xFF94A3B8.toInt())
            }

            val updateTime = prefs.getString(KEY_UPDATE_TIME, null)
            if (updateTime != null) {
                views.setTextViewText(R.id.widget_update_time, updateTime)
            } else {
                views.setTextViewText(R.id.widget_update_time, "")
            }

            // 点击余额打开 App
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openPending = PendingIntent.getActivity(
                context, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_balance, openPending)

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
