package com.deepseek.balance.widget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.deepseek.balance.MainActivity
import com.deepseek.balance.R

/**
 * 前台服务：按设定的间隔在后台自动拉取 DeepSeek 余额并刷新桌面小组件。
 *
 * 注意：前台服务必须展示一个常驻通知（系统要求），因此状态栏会一直显示一个
 * 低重要级的通知。这是实现「比系统 30 分钟更频繁」自动刷新的标准做法。
 */
class WidgetAutoRefreshService : Service() {

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())

        workerThread = HandlerThread("widget-auto-refresh").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)

        // 启动后立刻刷一次
        workerHandler?.post { WidgetRefresh.refresh(this) }
        scheduleNext()
    }

    /** 按当前间隔排期下一次刷新（递归调度，可随设置变化自动生效） */
    private fun scheduleNext() {
        val intervalSec = readIntervalSec()
        workerHandler?.postDelayed({
            WidgetRefresh.refresh(this)
            scheduleNext()
        }, intervalSec * 1000L)
    }

    private fun readIntervalSec(): Long {
        val sec = getSharedPreferences("deepseek_balance", MODE_PRIVATE)
            .getInt(KEY_INTERVAL_SEC, DEFAULT_INTERVAL_SEC)
            .coerceAtLeast(MIN_INTERVAL_SEC)
            .coerceAtMost(MAX_INTERVAL_SEC)
        return sec.toLong()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 被系统杀掉后自动重启（前提是仍被允许）
        return START_STICKY
    }

    override fun onDestroy() {
        workerHandler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = CHANNEL_ID
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "余额实时刷新",
                NotificationManager.IMPORTANCE_LOW, // 低重要级：不发声、不震动
            ).apply {
                description = "后台自动刷新 DeepSeek 余额桌面小组件"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPending = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("DeepSeek 余额实时刷新")
            .setContentText("正在后台自动更新小组件")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openPending)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "widget_auto_refresh_channel"
        const val NOTIF_ID = 2001
        const val KEY_INTERVAL_SEC = "widget_interval_sec"
        const val DEFAULT_INTERVAL_SEC = 300
        const val MIN_INTERVAL_SEC = 15
        const val MAX_INTERVAL_SEC = 900
    }
}
