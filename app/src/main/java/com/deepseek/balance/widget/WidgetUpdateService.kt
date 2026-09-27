package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService

/**
 * 系统定时刷新（APPWIDGET_UPDATE，最短 30 分钟一次）时触发：拉取最新余额并刷新对应小组件。
 * 不播放动画，静默更新。
 *
 * 注意：手动点刷新按钮**不走这里**——那条路径在 BalanceWidgetProvider.onReceive 里用
 * goAsync 直接执行，避免 JobScheduler 排期延迟导致「点了没反应」。
 */
class WidgetUpdateService : JobIntentService() {

    override fun onHandleWork(intent: Intent) {
        widgetLog("onHandleWork enter animate=${intent.getBooleanExtra(EXTRA_ANIMATE, false)}")
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        // 复用共享刷新逻辑（内部已处理无 Key / 失败保留旧数据）
        val ok = WidgetRefresh.refresh(this, animate = intent.getBooleanExtra(EXTRA_ANIMATE, false))
        widgetLog("onHandleWork result=$ok")
    }

    companion object {
        private const val JOB_ID = 1001
        const val EXTRA_ANIMATE = "widget_animate"

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, WidgetUpdateService::class.java, JOB_ID, intent)
        }
    }
}
