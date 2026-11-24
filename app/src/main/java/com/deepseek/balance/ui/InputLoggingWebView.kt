package com.deepseek.balance.ui

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.webkit.WebView
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

class InputLoggingWebView(context: Context) : WebView(context) {
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val inputConnection = super.onCreateInputConnection(outAttrs) ?: return null
        val isDebuggable = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (!isDebuggable) return inputConnection
        Log.i(
            INPUT_LOG_TAG,
            "onCreateInputConnection inputType=${outAttrs.inputType} imeOptions=${outAttrs.imeOptions}",
        )
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(InputConnection::class.java),
        ) { _, method, arguments ->
            logInputMethod(method.name, arguments)
            try {
                if (arguments == null) {
                    method.invoke(inputConnection)
                } else {
                    method.invoke(inputConnection, *arguments)
                }
            } catch (error: InvocationTargetException) {
                throw error.targetException
            }
        } as InputConnection
    }

    private fun logInputMethod(methodName: String, arguments: Array<out Any?>?) {
        when (methodName) {
            "sendKeyEvent" -> {
                val event = arguments?.getOrNull(0) as? KeyEvent
                Log.i(
                    INPUT_LOG_TAG,
                    "sendKeyEvent action=${event?.action} keyCode=${event?.keyCode} repeat=${event?.repeatCount} flags=${event?.flags}",
                )
            }
            "deleteSurroundingText", "deleteSurroundingTextInCodePoints" -> {
                Log.i(
                    INPUT_LOG_TAG,
                    "$methodName before=${arguments?.getOrNull(0)} after=${arguments?.getOrNull(1)}",
                )
            }
            "commitText", "setComposingText" -> {
                val text = arguments?.getOrNull(0) as? CharSequence
                Log.i(
                    INPUT_LOG_TAG,
                    "$methodName textLength=${text?.length} cursor=${arguments?.getOrNull(1)}",
                )
            }
            "setSelection", "setComposingRegion" -> {
                Log.i(
                    INPUT_LOG_TAG,
                    "$methodName start=${arguments?.getOrNull(0)} end=${arguments?.getOrNull(1)}",
                )
            }
            "beginBatchEdit", "endBatchEdit", "finishComposingText", "closeConnection" -> {
                Log.i(INPUT_LOG_TAG, methodName)
            }
        }
    }

    private companion object {
        const val INPUT_LOG_TAG = "WebInputProbe"
    }
}