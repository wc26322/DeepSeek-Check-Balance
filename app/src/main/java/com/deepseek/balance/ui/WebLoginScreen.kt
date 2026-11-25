package com.deepseek.balance.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.ApplicationInfo

import android.util.Log

import android.view.View
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import kotlinx.coroutines.delay

// 登录页（未登录时 SPA 会重定向到这里；登录成功后在 localStorage 写入 userToken）
private const val LOGIN_URL = "https://platform.deepseek.com/sign_in"
private const val TOKEN_KEY = "userToken"

// 修复 vivo/OriginOS 等 ROM 的 WebView 输入法光标 bug：
// 这些机型上，IME 在 <input>/<textarea> 提交字符后会把插入点（光标）留在最前面，
// 导致后续字符往开头插、看起来像「倒序输入」。这里在每次输入 / 组合结束后，
// 把光标强制拉回文本末尾，保证数字往后排。
// 注意：只在「字符数增加」（输入）时拉回；删除（字符数减少）时不动，
// 否则会打断输入法的「长按快速删除」。
private const val CARET_FIX_JS = """
(function(){
  function fixCaret(el){
    if (!el || (el.tagName !== 'INPUT' && el.tagName !== 'TEXTAREA')) return;
    if (document.activeElement !== el) return;
    var len = (el.value || '').length;
    var prev = el.__prevLen;
    if (typeof prev === 'number' && len < prev) { el.__prevLen = len; return; }
    el.__prevLen = len;
    try { el.setSelectionRange(len, len); } catch (e) {}
  }
  document.addEventListener('focusin', function(e){
    var el = e.target;
    if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA')) el.__prevLen = (el.value || '').length;
  }, true);
  document.addEventListener('input', function(e){ fixCaret(e.target); }, true);
  document.addEventListener('compositionend', function(e){ fixCaret(e.target); }, true);
})()
"""

// 从 localStorage 读取并解析 userToken：
// DeepSeek 未登录时 userToken = {"value":null,"__version":"0"}（占位对象），
// 登录成功后 value 变为真实令牌字符串。必须取 .value 且非空，才算登录成功。
// 同时兼容极少数“裸令牌字符串”存储形态。
private const val TOKEN_PROBE_JS = """
(function(){
  try {
    var raw = localStorage.getItem('$TOKEN_KEY');
    if (!raw) return '';
    var token = '';
    try {
      var obj = JSON.parse(raw);
      if (obj && typeof obj.value === 'string' && obj.value.length > 0) {
        token = obj.value;
      }
    } catch (e) {
      if (typeof raw === 'string' && raw.length > 0 && raw.charAt(0) !== '{') {
        token = raw;
      }
    }
    return token;
  } catch (e) { return ''; }
})()
"""


/**
 * App 内 WebView 的 html/body 高度会塌陷成 0，DeepSeek 登录页的 main 只按内容高度
 * （约 488px）布局，登录表单在 488 内垂直居中，整体比浏览器高约 90px。
 * 这里按 WebView 实际视口把 main 撑满，使登录表单几何与浏览器一致。
 */
private const val VIEWPORT_FILL_JS = """
(function(){
  var m = document.querySelector('main');
  if (!m) return;
  var w = window.innerWidth + 'px';
  var top = Math.max(0, m.getBoundingClientRect().top);
  var h = Math.max(0, window.innerHeight - top) + 'px';
  // 只把 main 撑到视口剩余高度，不锁死 html/body，避免 SPA 根容器被裁成白屏
  document.body.style.margin = '0';
m.style.width = w;
  m.style.height = h;
  m.style.minHeight = h;
  m.style.flex = 'none';
  // 页面自身在 main 上方预留了 116px 空白；上移 80px，只保留状态栏高度，
  // 并把页头/表单/底部提示按可用高度重新分布，避免内容整体偏下。
  var fittedH = (window.innerHeight - 36) + 'px';
  m.style.position = 'relative';
  m.style.marginTop = '-80px';
  m.style.height = fittedH;
  m.style.minHeight = fittedH;
  m.style.justifyContent = 'space-between';
  var wrap = m.querySelector('.ds-auth-form-wrapper');
  if (wrap) {
    wrap.style.position = 'absolute';
    wrap.style.top = '98px';
    wrap.style.left = '12px';
    wrap.style.width = 'calc(100% - 24px)';
    wrap.style.maxWidth = 'none';
  }
})()
"""

/**
 * 页面开始加载时就监听 main 的出现并立即撑满，避免 onPageFinished 后二次撑满
 * 导致登录表单先按内容高度渲染、再向下跳动。
 */
private const val VIEWPORT_FILL_OBSERVER_JS = """
(function(){
  if (window.__dsViewportObserver) return;
  window.__dsViewportObserver = true;
  function fillMain() {
    var m = document.querySelector('main');
    if (!m) return;
    var w = window.innerWidth + 'px';
    var top = Math.max(0, m.getBoundingClientRect().top);
    var h = Math.max(0, window.innerHeight - top) + 'px';
if (document.body.style.margin !== '0px') document.body.style.margin = '0';
    if (m.style.width !== w) m.style.width = w;
    if (m.style.marginTop !== '-80px') m.style.marginTop = '-80px';
    var fittedH = (window.innerHeight - 36) + 'px';
    if (m.style.position !== 'relative') m.style.position = 'relative';
    if (m.style.marginTop !== '-80px') m.style.marginTop = '-80px';
    if (m.style.height !== fittedH) m.style.height = fittedH;
    if (m.style.minHeight !== fittedH) m.style.minHeight = fittedH;
    if (m.style.justifyContent !== 'space-between') m.style.justifyContent = 'space-between';
    var wrap = m.querySelector('.ds-auth-form-wrapper');
    if (wrap) {
      if (wrap.style.position !== 'absolute') wrap.style.position = 'absolute';
      if (wrap.style.top !== '98px') wrap.style.top = '98px';
      if (wrap.style.left !== '12px') wrap.style.left = '12px';
      if (wrap.style.width !== 'calc(100% - 24px)') wrap.style.width = 'calc(100% - 24px)';
      if (wrap.style.maxWidth !== 'none') wrap.style.maxWidth = 'none';
    }
    if (m.style.flex !== 'none') m.style.flex = 'none';
  }
  fillMain();
  document.addEventListener('DOMContentLoaded', fillMain);
  new MutationObserver(fillMain).observe(document, { childList: true, subtree: true });
})()
"""

private const val VIEWPORT_METRICS_JS = """
(function(){
  function box(el){
    if (!el) return null;
    var r = el.getBoundingClientRect();
    return {tag: el.tagName, cls: String(el.className || '').slice(0, 80), top: Math.round(r.top), left: Math.round(r.left), w: Math.round(r.width), h: Math.round(r.height)};
  }
  var m = document.querySelector('main');
  var first = m ? m.firstElementChild : null;
  var form = document.querySelector('form');
  var wrap = document.querySelector('.ds-auth-form-wrapper');
  var wrapStyle = wrap ? window.getComputedStyle(wrap) : null;
  var mainStyle = m ? window.getComputedStyle(m) : null;
  var bodyStyle = document.body ? window.getComputedStyle(document.body) : null;
  return JSON.stringify({
    url: location.href,
    iw: window.innerWidth, ih: window.innerHeight,
    sh: document.documentElement.scrollHeight, bh: document.body.scrollHeight,
    bodyComputed: bodyStyle ? {
      paddingTop: bodyStyle.paddingTop, marginTop: bodyStyle.marginTop,
      display: bodyStyle.display, alignItems: bodyStyle.alignItems, justifyContent: bodyStyle.justifyContent
    } : null,
    bodyChildren: Array.prototype.slice.call(document.body.children, 0, 8).map(function(el){
      var b = box(el); if (b) b.cls = String(el.className || '').slice(0, 80); return b;
    }),
    main: box(m), mainStyleText: m ? m.getAttribute('style') : null,
    mainComputed: mainStyle ? {
      paddingTop: mainStyle.paddingTop, paddingBottom: mainStyle.paddingBottom,
      marginTop: mainStyle.marginTop, marginBottom: mainStyle.marginBottom,
      display: mainStyle.display, justifyContent: mainStyle.justifyContent
    } : null,
    mainPrev: m && m.previousElementSibling ? box(m.previousElementSibling) : null,
    mainPrevClass: m && m.previousElementSibling ? String(m.previousElementSibling.className || '').slice(0, 80) : null,
    first: box(first), firstStyle: first ? first.getAttribute('style') : null,
    form: box(form),
    wrap: box(wrap),
    wrapStyle: wrapStyle ? {
      display: wrapStyle.display, width: wrapStyle.width, maxWidth: wrapStyle.maxWidth,
      height: wrapStyle.height, padding: wrapStyle.padding, margin: wrapStyle.margin,
      flexDirection: wrapStyle.flexDirection, justifyContent: wrapStyle.justifyContent,
      alignItems: wrapStyle.alignItems, gap: wrapStyle.gap
    } : null,
    wrapChildren: wrap ? Array.prototype.slice.call(wrap.children, 0, 8).map(box) : [],
    children: m ? Array.prototype.slice.call(m.children, 0, 5).map(box) : []
  });
})()
"""

/**
 * 浏览器式 WebView 登录页。
 *
 * 不清空 WebView 会话：清空会丢掉横幅关闭状态、AB 分组等页面状态，导致同一页面
 * 在 App 内多出公告横幅、表单位置偏移。已登录时 sign_in 会直接跳转，轮询 localStorage
 * 仍能抓到 userToken 并自动回调保存。
 */
@SuppressLint("SetJavaScriptEnabled")

@Composable
fun WebLoginScreen(
    onTokenObtained: (String) -> Unit,
    onClose: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }

    var captured by remember { mutableStateOf(false) }
    var destroyed by remember { mutableStateOf(false) }
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    // 本机型 WebView 用 adjustResize 会把页面压缩、焦点输入框被顶出可视区；
    // 改为 adjustPan，让系统把焦点输入框平移到键盘正上方（退出登录页时恢复原模式）。
    val hostContext = LocalContext.current
    DisposableEffect(Unit) {
        val window = (hostContext as? Activity)?.window
        val prevMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        onDispose {
            if (window != null && prevMode != null) window.setSoftInputMode(prevMode)
        }
    }

    fun pollToken() {
        if (captured || destroyed) return
        val wv = webViewHolder.value ?: return
        try {
            wv.evaluateJavascript(TOKEN_PROBE_JS) { raw ->
                val token = raw.parseJsString()
                if (!captured && token.isNotBlank()) {
                    captured = true
                    onTokenObtained(token)
                }
            }
        } catch (_: Exception) {
        }
    }

    BackHandler(enabled = true) { destroyed = true; onClose() }

    // 轮询 localStorage 中的 userToken
    LaunchedEffect(Unit) {
        while (!captured && !destroyed) {
            delay(1000)
            pollToken()
        }
    }

    // 全屏 WebView：无顶部栏、无返回按钮、无空白占位，页面区域占满系统栏之间的空间
    val pageBackground = if (isSystemInDarkTheme()) Color(0xFF0F1115) else Color.White
    val webBackground = pageBackground
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AndroidView(
                modifier = Modifier
                    .fillMaxSize(),
                factory = { ctx ->
                    InputLoggingWebView(ctx).apply {
                        id = View.generateViewId()
                        webViewHolder.value = this
                        val isDebuggable = ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // UA 对齐 Chrome / Via：WebView 默认 UA 带完整设备型号、Build 号和完整内核版本，
// 页面容易据此识别为 WebView 并下发不同版本的登录页。改成 Chrome reduced UA，
// 与 Via 实际发出的 UA 一致（Android 10; K + Chrome 主版本.0.0.0）。
                        val defaultUserAgent = settings.userAgentString
                        val chromeMajorVersion = Regex("Chrome/(\\d+)")
                            .find(defaultUserAgent)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?: "150"
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/$chromeMajorVersion.0.0.0 Mobile Safari/537.36"
                        // 视口对齐 Chrome：登录页自身声明了
                        // width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no，
                        // 这里严格按页面 viewport 的 initial-scale 渲染，不再用 loadWithOverviewMode
                        // 二次缩放，否则 WebView 会把整页再缩放一次，字号和间距与浏览器不一致。
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = false
                        // 登录页已声明 user-scalable=no；App 内不再额外允许缩放，
                        // 避免双指缩放改变文档高度后出现整页上下滚动。
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        // 字体参数显式固定为 Chrome 默认值，避免 ROM 改写 textZoom / 默认字号
                        settings.textZoom = 100
                        settings.defaultFontSize = 16
                        settings.minimumFontSize = 0
                        settings.minimumLogicalFontSize = 8
                        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NORMAL)
                        // 页面渲染前的背景用主题色，避免白屏闪一下
                        setBackgroundColor(webBackground.toArgb())
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?,
                                url: String?,
                                favicon: android.graphics.Bitmap?,
                            ) {
                                super.onPageStarted(view, url, favicon)
                                view?.evaluateJavascript(VIEWPORT_FILL_OBSERVER_JS, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false

                                // 注入光标修复：每次输入后把插入点拉回末尾（绕开 vivo WebView 光标 bug）
                                view?.evaluateJavascript(CARET_FIX_JS, null)
                                // 每页加载完兜底检查一次 token
                                pollToken()
                                // 撑满登录页 main：App 内 html/body 高度塌陷会让表单整体偏高
                                view?.evaluateJavascript(VIEWPORT_FILL_JS, null)
                                if (isDebuggable) {
                                    val target = view
                                    target?.postDelayed({
                                        target.evaluateJavascript(VIEWPORT_METRICS_JS) { raw ->
                                            Log.i("WebLoginMetrics", raw)
                                        }
                                    }, 1500)
                                }

                            }

                            // 在 WebView 内打开所有链接（登录流程不跳出到外部浏览器）
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = false
                        }
                        // 不再清空 Cookie/localStorage：清空会丢掉横幅关闭状态、AB 分组等页面状态，
                        // 导致同一页面在 App 内多出更新公告横幅，登录表单整体下移。
                        // 已登录时 sign_in 会直接跳转，pollToken 仍能抓到 token 并自动关闭。
                        loadUrl(LOGIN_URL)
                    }
                },
                onRelease = { view ->
                    destroyed = true
                    view.destroy()
                },
            )

            // 加载遮罩：不透明且盖在最上层，避免转圈与界面重叠，同时遮挡 WebView 白屏瞬间
            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(pageBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "正在加载登录页…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            }
    }
}

/**
 * evaluateJavascript 回调值是 JSON 字符串（带两端引号）或 "null"，
 * 去掉引号还原为原始 token。
 */
private fun String?.parseJsString(): String {
    if (this == null) return ""
    val v = this.trim()
    if (v == "null" || v == "undefined" || v.isEmpty()) return ""
    return v.removeSurrounding("\"")
}
