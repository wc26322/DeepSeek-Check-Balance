package com.deepseek.balance.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * 小组件数字滚动动画：从**刷新前的旧值**平滑滚到新值（easeOut 缓动）。
 *
 * 设计要点（都是实测踩出来的）：
 * - 起点 = 旧值：v1.4.8 从 0 滚，观感是「数字先跳到新值 → 又跳回 0 → 再滚上去」，又突兀又像卡顿。
 * - 帧间隔 50ms（约 20fps）：RemoteViews 每帧都要让桌面重新渲染一次，帧率太高会被
 *   vivo 桌面节流，反而出现跳帧卡顿。
 * - 每帧走完整 RemoteViews（Provider 的 frame 分支只覆盖三个数值）：保证点击绑定永远在，
 *   动画被中断也不会让小组件失去点击能力。
 */
object WidgetCountUpAnimator {

    /** 帧间隔：8ms ≈ 120fps（用户指定）。注意小组件走 RemoteViews，桌面端刷新率有上限，
     *  实际观感可能达不到满 120fps，桌面跟不上时会丢帧合并——这是系统机制决定的。 */
    private const val FRAME_MS = 8L

    /** 动画总时长：500ms，配合 8ms 帧间隔 ≈ 62 帧 */
    private const val DURATION_MS = 500.0

    private val handlerRef = AtomicReference<Handler?>(null)
    private val threadRef = AtomicReference<HandlerThread?>(null)
    private val animRef = AtomicReference<Anim?>(null)

    /** 三个数值（余额 / 总Tokens / 今日Tokens） */
    data class Amounts(val balance: Double, val totalTokens: Double, val todayTokens: Double)

    /** 一次动画：起点 / 终点 / 起始时刻 */
    private class Anim(val from: Amounts, val to: Amounts, val startNanos: Long)

    /** 动画是否正在跑（其他更新逻辑在动画期间应避免直接覆盖数值） */
    fun isRunning(): Boolean = animRef.get() != null

    /** 读取 prefs 里的三个数值（格式化文本里的逗号会被剥掉） */
    private fun readAmounts(context: Context): Amounts {
        val prefs = context.getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
        return Amounts(
            parseAmount(prefs.getString(BalanceWidgetProvider.KEY_BALANCE, "0")),
            parseAmount(prefs.getString(BalanceWidgetProvider.KEY_TOTAL_TOKENS, "0")),
            parseAmount(prefs.getString(BalanceWidgetProvider.KEY_TODAY_TOKENS, "0")),
        )
    }

    private fun parseAmount(raw: String?): Double =
        raw?.replace(",", "")?.toDoubleOrNull() ?: 0.0

    /**
     * 启动滚动动画：固定从 0 滚到当前值（即使数据没变也有明显的滚动反馈）。
     * 三个目标值全为 0 时直接跳过。
     */
    fun start(context: Context) {
        val to = readAmounts(context)
        if (to.balance <= 0.0 && to.totalTokens <= 0.0 && to.todayTokens <= 0.0) return
        animRef.set(Anim(Amounts(0.0, 0.0, 0.0), to, System.nanoTime()))
        widgetLog("动画启动 to=(${to.balance},${to.totalTokens},${to.todayTokens})")

        val thread = threadRef.get() ?: HandlerThread("widget-count-up").also {
            it.start()
            threadRef.set(it)
        }
        val handler = handlerRef.get() ?: Handler(thread.looper).also { handlerRef.set(it) }
        // 动画期间再次 start()：清掉旧帧，以新目标值重启
        handler.removeCallbacksAndMessages(null)
        handler.post { tick(context) }
    }

    private fun tick(context: Context) {
        val anim = animRef.get() ?: return
        val manager = AppWidgetManager.getInstance(context)
        val elapsedMs = (System.nanoTime() - anim.startNanos) / 1_000_000.0
        if (elapsedMs >= DURATION_MS) {
            // 动画结束：直接落到位，并恢复完整更新
            animRef.set(null)
            widgetLog("动画结束，恢复完整更新")
            BalanceWidgetProvider.updateWidget(context, manager, null)
            return
        }
        val p = (elapsedMs / DURATION_MS).coerceIn(0.0, 1.0)
        // easeOutCubic：先快后慢，数字滚动观感更自然
        val eased = 1.0 - (1.0 - p) * (1.0 - p) * (1.0 - p)
        val frame = BalanceWidgetProvider.Companion.AnimFrame(
            balance = formatBalance(lerp(anim.from.balance, anim.to.balance, eased)),
            totalTokens = formatTokens(lerp(anim.from.totalTokens, anim.to.totalTokens, eased)),
            todayTokens = formatTokens(lerp(anim.from.todayTokens, anim.to.todayTokens, eased)),
        )
        try {
            BalanceWidgetProvider.updateWidget(context, manager, null, frame)
        } catch (t: Throwable) {
            // 单帧推送失败：立刻收尾成最终状态，绝不留残缺视图在桌面上
            animRef.set(null)
            widgetLog("动画帧异常，强制恢复: ${t.javaClass.simpleName}")
            BalanceWidgetProvider.updateWidget(context, manager, null)
            return
        }
        handlerRef.get()?.postDelayed({ tick(context) }, FRAME_MS)
    }

    private fun lerp(from: Double, to: Double, eased: Double) = from + (to - from) * eased
    private fun formatBalance(v: Double) = String.format(Locale.US, "%.2f", v)
    private fun formatTokens(v: Double) = String.format(Locale.US, "%,d", v.toLong())
}
