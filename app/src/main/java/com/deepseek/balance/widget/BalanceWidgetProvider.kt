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
        widgetLog("onUpdate ids=${appWidgetIds.toList()}")
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
        widgetLog("onReceive action=${intent.action}")
        if (ACTION_REFRESH == intent.action) {
            // 手动刷新（点右下角刷新按钮）。
            // 这里不再交给 JobIntentService：它底层走 JobScheduler，job 可能被厂商省电策略延后，
            // 表现就是「点了没反应」。改为在广播自带的 goAsync 窗口内直接发起请求：
            // 先把小组件立刻切成「刷新中…」（点击反馈），再拉数据，完成后恢复并 finish。
            val appWidgetId = intent.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            )
            WidgetRefresh.refreshFromBroadcast(context, goAsync(), appWidgetId)
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

        /** 「正在刷新」的截止时刻（毫秒）。用时间戳而非布尔量：进程被杀也不会永久停在刷新中。 */
        const val KEY_REFRESHING_UNTIL = "widget_refreshing_until"

        /** 「刷新中」状态最长保留时间，超时后自动按正常状态渲染（兜底，防止卡在刷新中） */
        const val REFRESHING_TIMEOUT_MS = 20_000L

        private const val PREFS = "deepseek_balance"

        fun isRefreshing(prefs: SharedPreferences): Boolean =
            prefs.getLong(KEY_REFRESHING_UNTIL, 0L) > System.currentTimeMillis()

        /** 动画帧数据：余额 / 总Tokens / 今日Tokens 三个插值后的显示文本 */
        data class AnimFrame(val balance: String, val totalTokens: String, val todayTokens: String)

        /**
         * 更新小组件。
         * - frame 不为 null：动画帧，仅覆盖三个数值文本，**其余（点击、时间、配色）与完整更新完全一致**
         *   ——这一点很关键：早期版本动画帧只 setText，会把 PendingIntent 全部冲掉，
         *   导致之后点刷新按钮 / 点小组件本身都没反应。
         * - appWidgetId 为 null：刷新所有已添加的小组件实例（动画收尾 / 刷新中提示用）
         * - 否则：完整更新指定 id
         */
        @JvmOverloads
        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int?,
            frame: AnimFrame? = null,
        ) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val ids = if (appWidgetId != null) {
                intArrayOf(appWidgetId)
            } else {
                resolveIds(context, appWidgetManager)
            }
            ids.forEach { updateWidgetFull(context, appWidgetManager, it, prefs, frame) }
        }

        private fun resolveIds(context: Context, appWidgetManager: AppWidgetManager): IntArray =
            appWidgetManager.getAppWidgetIds(
                ComponentName(context, BalanceWidgetProvider::class.java),
            )

        /**
         * 液态玻璃小组件：背景 drawable 会自动按系统深/浅色切换（drawable-night），
         * 这里同步适配文字颜色——深色玻璃上改用亮色，保证可读（浅色玻璃用布局默认深字）。
         */
        private fun applyGlassTextColors(context: Context, views: RemoteViews) {
            val nightMode = context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK
            if (nightMode != android.content.res.Configuration.UI_MODE_NIGHT_YES) return
            views.setTextColor(R.id.widget_balance, 0xFFF5F7FA.toInt())
            views.setTextColor(R.id.widget_total_tokens, 0xFF5CC8F6.toInt())
            views.setTextColor(R.id.widget_today_tokens, 0xFF55E08C.toInt())
            views.setTextColor(R.id.widget_update_time, 0xFF94A3B8.toInt())
            views.setTextColor(R.id.widget_label_balance, 0xFF94A3B8.toInt())
            views.setTextColor(R.id.widget_label_total, 0xFF94A3B8.toInt())
            views.setTextColor(R.id.widget_label_today, 0xFF94A3B8.toInt())
        }

        private fun updateWidgetFull(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            prefs: SharedPreferences,
            frame: AnimFrame? = null,
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_balance)
            applyGlassTextColors(context, views)

            val symbol = prefs.getString(KEY_SYMBOL, "¥") ?: "¥"
            if (frame != null) {
                // 动画帧：只覆盖三个数值，点击/时间/配色等状态照常渲染
                views.setTextViewText(R.id.widget_balance, "$symbol${frame.balance}")
                views.setTextViewText(R.id.widget_total_tokens, frame.totalTokens)
                views.setTextViewText(R.id.widget_today_tokens, frame.todayTokens)
            } else {
                val balance = prefs.getString(KEY_BALANCE, null)
                views.setTextViewText(
                    R.id.widget_balance,
                    if (balance != null) "$symbol$balance" else "点击查询",
                )
                views.setTextViewText(
                    R.id.widget_total_tokens,
                    prefs.getString(KEY_TOTAL_TOKENS, null) ?: "--",
                )
                views.setTextViewText(
                    R.id.widget_today_tokens,
                    prefs.getString(KEY_TODAY_TOKENS, null) ?: "--",
                )
            }

            // 底部：更新时间 / 刷新中提示。
            // 注意：刷新按钮**永远可见、永远可点**（v1.4.8 曾试过刷新中临时藏按钮，
            // 状态一旦没恢复按钮就永远不见了，已废弃这种做法）。
            val refreshing = isRefreshing(prefs)
            val updateTime = prefs.getString(KEY_UPDATE_TIME, null)
            views.setTextViewText(
                R.id.widget_update_time,
                if (refreshing) "刷新中…" else (updateTime ?: ""),
            )

            // 点击小组件任意处（除刷新按钮）打开 App
            val openIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val openPending = PendingIntent.getActivity(
                context, appWidgetId * 2, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPending)

            // 点击刷新按钮：requestCode 与 root 用不同槽位，避免两者互相覆盖
            val refreshIntent = Intent(context, BalanceWidgetProvider::class.java).apply {
                action = ACTION_REFRESH
                // FLAG_RECEIVER_FOREGROUND：让系统按「前台广播」投递，允许在进程已被回收时
                // 把本应用拉起来。vivo / OriginOS 等厂商会拦截后台广播的冷启动，
                // 不加这个标志时进程一旦被杀，点刷新就会完全没反应。
                addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val refreshPending = PendingIntent.getBroadcast(
                context, appWidgetId * 2 + 1, refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
