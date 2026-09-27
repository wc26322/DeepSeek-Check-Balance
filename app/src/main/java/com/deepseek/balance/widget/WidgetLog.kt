package com.deepseek.balance.widget

import android.util.Log

/**
 * 小组件调试日志统一出口。
 *
 * 重要：本机（OriginOS / vivo）的 logcat 会过滤掉 **D 级** 日志（实测 `log -p d` 一行都抓不到，
 * `log -p i` 正常）。所以小组件相关的排查日志一律用 **I 级**，否则在真机上排查时会误判成
 * 「广播没送达 / 代码没执行」。
 */
internal const val WIDGET_LOG_TAG = "DSWidget"

internal fun widgetLog(message: String) {
    Log.i(WIDGET_LOG_TAG, message)
}
