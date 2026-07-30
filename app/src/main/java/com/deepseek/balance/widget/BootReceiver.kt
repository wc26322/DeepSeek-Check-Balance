package com.deepseek.balance.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机后若用户开启了「后台实时刷新」，则重新启动前台刷新服务。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val enabled = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
            .getBoolean(KEY_REALTIME, true)
        if (!enabled) return

        val serviceIntent = Intent(context, WidgetAutoRefreshService::class.java)
        context.startForegroundService(serviceIntent)
    }

    companion object {
        const val KEY_REALTIME = "widget_realtime"
    }
}
