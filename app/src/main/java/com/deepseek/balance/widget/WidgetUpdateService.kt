package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.core.app.JobIntentService

/**
 * 手动刷新（点击小组件刷新按钮）时触发：拉取最新余额并刷新对应小组件。
 */
class WidgetUpdateService : JobIntentService() {

    override fun onHandleWork(intent: Intent) {
        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        // 复用共享刷新逻辑（内部已处理无 Key / 失败保留旧数据）
        WidgetRefresh.refresh(this)
    }

    companion object {
        private const val JOB_ID = 1001

        fun enqueueWork(context: Context, intent: Intent) {
            enqueueWork(context, WidgetUpdateService::class.java, JOB_ID, intent)
        }
    }
}
