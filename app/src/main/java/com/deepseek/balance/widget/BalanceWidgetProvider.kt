package com.deepseek.balance.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
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
            // 系统定时（最短 30 分钟）触发时，真正去拉取最新余额（自动刷新，不播动画）
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
            // 手动触发刷新：启动 WidgetUpdateService（播放滚动动画）
            WidgetUpdateService.enqueueWork(
                context,
                Intent(context, WidgetUpdateService::class.java).apply {
                    action = ACTION_REFRESH
                    putExtra(WidgetUpdateService.EXTRA_ANIMATE, true)
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

        /** 动画帧数据：余额 / 总Tokens / 今日Tokens 三个插值后的显示文本 */
        data class AnimFrame(val balance: String, val totalTokens: String, val todayTokens: String)

        /**
         * 更新小组件。
         * - frame 不为 null：动画帧，仅更新三个数值文本（不重设 PendingIntent，避免闪烁）
         * - appWidgetId 为 null：刷新所有已添加的小组件实例（动画收尾用）
         * - 否则：完整更新指定 id
         */
        @JvmOverloads
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int?,
            frame: AnimFrame? = null,
        ) {
            val prefs = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)

            if (frame != null) {
                // 动画帧：只改文本，复用之前设置过的 PendingIntent
                val ids = resolveIds(context, appWidgetManager)
                val views = RemoteViews(context.packageName, R.layout.widget_balance)
                views.setTextViewText(R.id.widget_balance, "${prefs.getString(KEY_SYMBOL, "¥")}${frame.balance}")
                views.setTextViewText(R.id.widget_total_tokens, frame.totalTokens)
                views.setTextViewText(R.id.widget_today_tokens, frame.todayTokens)
                ids.forEach { appWidgetManager.updateAppWidget(it, views) }
                return
            }

            // 完整更新（单个 id 或全部）
            val ids = if (appWidgetId != null) intArrayOf(appWidgetId) else resolveIds(context, appWidgetManager)
            ids.forEach { updateWidgetFull(context, appWidgetManager, it, prefs) }
        }

        private fun resolveIds(context: Context, appWidgetManager: AppWidgetManager): IntArray =
            appWidgetManager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java),
            )

        private fun updateWidgetFull(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            prefs: SharedPreferences,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_balance)

            // 金额（居中，看余额即可判断是否可用，不再单独显示状态）
            val balance = prefs.getString(KEY_BALANCE, null)
            if (balance != null) {
                views.setTextViewText(R.id.widget_balance, "${prefs.getString(KEY_SYMBOL, "¥")}$balance")
            } else {
                views.setTextViewText(R.id.widget_balance, "点击查询")
            }

            val updateTime = prefs.getString(KEY_UPDATE_TIME, null)
            views.setTextViewText(R.id.widget_update_time, updateTime ?: "")

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
