package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 小组件金额「0 → 当前值」滚动动画：
 * RemoteViews 不支持原生动画，这里在后台线程用 Handler 逐帧把数字从 0 插值到
 * 目标值，每帧重新 setText 并更新小组件（easeOut 缓动，约 600ms）。
 * 单例：动画期间再次 start() 会以新目标值重启。
 */
object WidgetCountUpAnimator {

    private val handlerRef = AtomicReference<Handler?>(null)
    private val threadRef = AtomicReference<HandlerThread?>(null)
    private val targetsRef = AtomicReference<Amounts?>(null)

    /** 一次动画的目标值与起始时刻 */
    private class Amounts(
        val balance: Double,
        val totalTokens: Double,
        val todayTokens: Double,
        val startNanos: Long,
    )

    /** 解析 prefs 中的三个数值（失败按 0 处理），并用 symbol 组装显示文本 */
    private fun buildTargets(context: Context): Amounts {
        val prefs = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
        return Amounts(
            parseAmount(prefs.getString(BalanceWidgetProvider.KEY_BALANCE, "0")),
            parseAmount(prefs.getString(BalanceWidgetProvider.KEY_TOTAL_TOKENS, "0")),
            parseAmount(prefs.getString(BalanceWidgetProvider.KEY_TODAY_TOKENS, "0")),
            System.nanoTime(),
        )
    }

    private fun parseAmount(raw: String?): Double =
        raw?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    /** 启动滚动动画（若三个目标值全为 0 则跳过） */
    fun start(context: Context) {
        val targets = buildTargets(context)
        if (targets.balance <= 0.0 && targets.totalTokens <= 0.0 && targets.todayTokens <= 0.0) {
            return
        }
        targetsRef.set(targets)

        val thread = threadRef.get() ?: HandlerThread("widget-count-up").also {
            it.start()
            threadRef.set(it)
        }
        val handler = handlerRef.get() ?: Handler(thread.looper).also {
            handlerRef.set(it)
        }
        handler.removeCallbacksAndMessages(null)
        handler.post { tick(context) }
    }

    private fun tick(context: Context) {
        val targets = targetsRef.get() ?: return
        val elapsedMs = (System.nanoTime() - targets.startNanos) / 1_000_000.0
        val durationMs = 600.0
        if (elapsedMs >= durationMs) {
            // 动画结束：直接落到位，并恢复完整更新（含 PendingIntent）
            targetsRef.set(null)
            BalanceWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), null)
            return
        }
        val p = (elapsedMs / durationMs).coerceIn(0.0, 1.0)
        // easeOutCubic：先快后慢，数字滚动观感更自然
        val eased = 1.0 - (1.0 - p) * (1.0 - p) * (1.0 - p)
        val frame = BalanceWidgetProvider.Companion.AnimFrame(
            balance = formatBalance(targets.balance * eased),
            totalTokens = formatTokens(targets.totalTokens * eased),
            todayTokens = formatTokens(targets.todayTokens * eased),
        )
        BalanceWidgetProvider.updateWidget(context, AppWidgetManager.getInstance(context), null, frame)
        handlerRef.get()?.postDelayed({ tick(context) }, 16)
    }

    private fun formatBalance(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun formatTokens(v: Double) = String.format(Locale.US, "%,d", v.toLong())
}
