package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService

/**
 * 手动刷新（点击小组件刷新按钮）时触发：拉取最新余额并刷新对应小组件。
 * 手动刷新会播放 0 → 当前值 滚动动画；系统/后台定时刷新（自动刷新）不播放。
 */
class WidgetUpdateService : JobIntentService() {

    override fun onHandleWork(intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        // 复用共享刷新逻辑（内部已处理无 Key / 失败保留旧数据）
        // 是否播放动画由 extra 决定，避免跨 job 复用实例时的状态串扰
        WidgetRefresh.refresh(this, animate = intent.getBooleanExtra(EXTRA_ANIMATE, false))
    }

    companion object {
        private const val JOB_ID = 1001
        const val EXTRA_ANIMATE = "widget_animate"

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, WidgetUpdateService::class.java, JOB_ID, intent)
        }
    }
}
