package com.deepseek.balance.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay

// 登录页（未登录时 SPA 会重定向到这里；登录成功后在 localStorage 写入 userToken）
private const val LOGIN_URL = "https://platform.deepseek.com/login"
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
 * 全屏 WebView 登录页。
 *
 * 打开前会清空 WebView 会话（Cookie + localStorage），确保显示登录表单而不是
 * 直接带旧登录态跳转。用户直接在网页表单里登录，App 轮询 localStorage 抓取
 * userToken 后自动回调保存。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebLoginScreen(
    onTokenObtained: (String) -> Unit,
    onClose: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var pageTitle by remember { mutableStateOf("") }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (pageTitle.isBlank()) "登录 DeepSeek 网页端" else pageTitle,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { destroyed = true; onClose() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "关闭",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        // AndroidView 的 factory 不是 @Composable 上下文，需先在此处取好主题色
        val webBackground = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewHolder.value = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        // 移动端视口：整页适配屏宽，避免内容显示不全 / 比例错乱
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.setSupportZoom(false)
                        settings.builtInZoomControls = false
                        settings.displayZoomControls = false
                        // 页面渲染前的背景用主题色，避免白屏闪一下
                        setBackgroundColor(webBackground.toArgb())
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                                view?.evaluateJavascript("document.title") { t ->
                                    pageTitle = t.parseJsString()
                                }
                                // 注入光标修复：每次输入后把插入点拉回末尾（绕开 vivo WebView 光标 bug）
                                view?.evaluateJavascript(CARET_FIX_JS, null)
                                // 每页加载完兜底检查一次 token
                                pollToken()
                            }

                            // 在 WebView 内打开所有链接（登录流程不跳出到外部浏览器）
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?,
                            ): Boolean = false
                        }
                        // 打开登录页前清空 WebView 会话（Cookie + localStorage）：
                        // 否则已登录时 /login 会直接带登录态跳转、token 秒抓，看不到登录表单
                        CookieManager.getInstance().removeAllCookies {
                            CookieManager.getInstance().flush()
                            WebStorage.getInstance().deleteAllData()
                            loadUrl(LOGIN_URL)
                        }
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
                        .background(MaterialTheme.colorScheme.background),
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
