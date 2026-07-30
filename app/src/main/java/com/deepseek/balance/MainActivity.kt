package com.deepseek.balance

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.WindowManager
import java.util.ArrayDeque
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.metrics.performance.JankStats
import com.deepseek.balance.model.BalanceResponse
import com.deepseek.balance.network.ApiClient
import com.deepseek.balance.ui.MainScreen
import com.deepseek.balance.ui.SettingsScreen
import com.deepseek.balance.ui.theme.DeepSeekBalanceTheme
import com.deepseek.balance.widget.BalanceWidgetProvider
import com.deepseek.balance.widget.WidgetAutoRefreshService
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private val prefs by lazy {
        getSharedPreferences("deepseek_balance", Context.MODE_PRIVATE)
    }

    private lateinit var jankStats: JankStats
    private var currentScreen = "main"

    // 真实 FPS 监控（调试用）：通过 Choreographer 统计最近 2 秒实际接收的 vsync 帧率，
    // 用于确认 App 是否真的跑在 120Hz，还是被系统动态刷新率压回 60/90。
    private val fpsFrameTimes = ArrayDeque<Long>()
    private val fpsIntervals = ArrayDeque<Double>() // 每帧 vsync 间隔(ms)，用于发现"掉拍"(frame pacing 抖动)
    private var lastFrameNanos = 0L
    private var fpsMonitorRunning = false
    private val choreographer = Choreographer.getInstance()
    private val fpsFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (lastFrameNanos != 0L) {
                val intervalMs = (frameTimeNanos - lastFrameNanos) / 1_000_000.0
                fpsIntervals.addLast(intervalMs)
                while (fpsIntervals.size > 180) fpsIntervals.removeFirst()
            }
            lastFrameNanos = frameTimeNanos
            fpsFrameTimes.addLast(frameTimeNanos)
            while (fpsFrameTimes.size > 1 && frameTimeNanos - fpsFrameTimes.first() > 2_000_000_000L) {
                fpsFrameTimes.removeFirst()
            }
            if (fpsFrameTimes.size > 1 && fpsIntervals.isNotEmpty()) {
                val spanSec = (frameTimeNanos - fpsFrameTimes.first()) / 1_000_000_000.0
                val fps = (fpsFrameTimes.size - 1) / spanSec
                val sorted = fpsIntervals.sorted()
                val maxI = sorted.last()
                val p99 = sorted[(sorted.size * 0.99).toInt().coerceAtMost(sorted.size - 1)]
                // 120Hz 下理想每帧 ~8.33ms；>9ms 即这一帧多等了一拍(被复用上一帧)，肉眼可见顿挫
                val over9 = fpsIntervals.count { it > 9.0 }
                val overRatio = 100.0 * over9 / fpsIntervals.size
                Log.d("FPS_MONITOR", String.format(Locale.US,
                    "平均FPS≈%.1f 帧=%d | 帧间隔 max=%.1fms p99=%.1fms 掉拍率=%.1f%%",
                    fps, fpsFrameTimes.size - 1, maxI, p99, overRatio))
            }
            if (fpsMonitorRunning) choreographer.postFrameCallback(this)
        }
    }

    private fun startFpsMonitor() {
        if (fpsMonitorRunning) return
        fpsMonitorRunning = true
        fpsFrameTimes.clear()
        choreographer.postFrameCallback(fpsFrameCallback)
        Log.i("FPS_MONITOR", "FPS监控已启动")
    }

    private fun stopFpsMonitor() {
        fpsMonitorRunning = false
        Log.i("FPS_MONITOR", "FPS监控已停止")
    }

    // JankStats 持久化记录（写入 App 私有文件，避免 logcat 被系统冲掉）
    private lateinit var jankLogFile: File
    private var totalFrames = 0
    private var jankFrames = 0
    private var maxFrameMs = 0f
    private var sumFrameMs = 0.0

    private val requestPostNotif = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        // 无论是否授权都尝试启动：未授权时服务仍运行，只是通知可能被系统抑制
        if (prefs.getBoolean("widget_realtime", true)) {
            startRealtimeService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        jankLogFile = File(filesDir, "jank_log.txt")
        try {
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            FileWriter(jankLogFile, true).use { it.append("========== App 启动 $ts ==========\n") }
        } catch (_: Exception) { }

        setContent {
            DeepSeekBalanceTheme {
                BalanceAppContent(
                    prefs = prefs,
                    onScreenChange = { currentScreen = it },
                    onRealtimeToggle = { enabled ->
                        if (enabled) ensureNotificationPermissionThenStart() else stopRealtimeService()
                    },
                    onRealtimeIntervalChange = {
                        // 间隔变化：若正在运行则重启服务以立即生效
                        if (prefs.getBoolean("widget_realtime", true)) {
                            stopRealtimeService()
                            ensureNotificationPermissionThenStart()
                        }
                    },
                )
            }
        }

        // 首次启动：若已开启实时刷新，则拉起前台服务
        if (prefs.getBoolean("widget_realtime", true)) {
            ensureNotificationPermissionThenStart()
        }
    }

    private fun ensureNotificationPermissionThenStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                requestPostNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        startRealtimeService()
    }

    private fun startRealtimeService() {
        val intent = Intent(this, WidgetAutoRefreshService::class.java)
        startForegroundService(intent)
    }

    private fun stopRealtimeService() {
        stopService(Intent(this, WidgetAutoRefreshService::class.java))
    }

    override fun onResume() {
        super.onResume()
        // 强制 120Hz：优先用 preferredDisplayModeId 直接指定 120Hz 显示模式（modeId=1），
        // 比 preferredRefreshRate 更强制，能绕过 OriginOS 的刷新率分区(静止60/交互90)限制。
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val display = wm.defaultDisplay
            val mode120 = display.supportedModes.firstOrNull { it.refreshRate >= 119f }
            val params = window.attributes
            if (mode120 != null) {
                params.preferredDisplayModeId = mode120.modeId
                Log.i("REFRESH", "锁定显示模式 modeId=${mode120.modeId} refreshRate=${mode120.refreshRate}")
            } else {
                Log.w("REFRESH", "未找到 120Hz 模式，回退 preferredRefreshRate=120")
            }
            @Suppress("DEPRECATION")
            params.preferredRefreshRate = 120f
            window.attributes = params
        } catch (e: Exception) {
            Log.w("REFRESH", "锁定120Hz失败: ${e.message}")
        }
        startFpsMonitor()
        // DecorView 在 setContentView 之后才创建，必须等它就绪再创建 JankStats，否则崩溃
        window.decorView.post {
            if (!::jankStats.isInitialized) {
                jankStats = JankStats.createAndTrack(window) { frameData ->
                    totalFrames++
                    val durationMs = frameData.frameDurationUiNanos / 1_000_000f
                    sumFrameMs += durationMs
                    if (durationMs > maxFrameMs) maxFrameMs = durationMs
                    if (frameData.isJank) {
                        jankFrames++
                        val msg = String.format(
                            Locale.US,
                            "JANK 耗时=%.1fms 页面=%s",
                            durationMs, currentScreen,
                        )
                        Log.w("JANKSTATS", msg)
                    }
                }
                // 降低 jank 判定倍率到 1x：任何超过一帧预算(>8.3ms@120Hz)的帧都记为 jank，
                // 才能抓到"平均120但偶有掉拍"的 frame pacing 问题（默认 2x 会漏掉）。
                jankStats.jankHeuristicMultiplier = 1.0f
                Log.i("JANKSTATS", "JankStats 已启动，滑动设置页时观察本 tag 的掉帧日志")
            }
            jankStats.isTrackingEnabled = true
        }
    }

    override fun onPause() {
        super.onPause()
        stopFpsMonitor()
        if (::jankStats.isInitialized) {
            jankStats.isTrackingEnabled = false
            try {
                val avg = if (totalFrames > 0) sumFrameMs / totalFrames else 0.0
                val ratio = if (totalFrames > 0) 100f * jankFrames / totalFrames else 0f
                val summary = String.format(
                    Locale.US,
                    "==== 暂停汇总 总帧=%d 掉帧=%d (%.1f%%) 最大=%.1fms 平均=%.2fms 页面=%s ====\n",
                    totalFrames, jankFrames, ratio, maxFrameMs, avg, currentScreen,
                )
                FileWriter(jankLogFile, true).use { it.append(summary) }
            } catch (_: Exception) { }
        }
    }
}

@Composable
private fun BalanceAppContent(
    prefs: android.content.SharedPreferences,
    onScreenChange: (String) -> Unit,
    onRealtimeToggle: (Boolean) -> Unit,
    onRealtimeIntervalChange: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // 导航
    var showSettings by remember { mutableStateOf(false) }

    // 标记当前页面，便于在 Logcat 中区分主界面/设置页的掉帧
    LaunchedEffect(showSettings) {
        onScreenChange(if (showSettings) "settings" else "main")
    }

    // API Key
    var apiKey by remember {
        mutableStateOf(prefs.getString("api_key", "") ?: "")
    }

    // 余额预警
    var alertEnabled by remember {
        mutableStateOf(prefs.getBoolean("alert_enabled", false))
    }
    var alertThreshold by remember {
        mutableStateOf(prefs.getString("alert_threshold", "50") ?: "50")
    }

    // 小组件实时刷新
    var widgetRealtime by remember {
        mutableStateOf(prefs.getBoolean("widget_realtime", true))
    }
    var widgetIntervalSec by remember {
        mutableStateOf(prefs.getInt("widget_interval_sec", 300))
    }

    // 数据
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<BalanceResponse?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastQueryTime by remember { mutableStateOf<String?>(null) }

    // 每次进入前台时自动刷新
    var refreshVersion by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshVersion++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    suspend fun doRefresh(showErrors: Boolean = true) {
        isLoading = true
        if (showErrors) errorMessage = null
        try {
            val response = ApiClient.getBalance(apiKey)
            val prevBalance = result?.balanceInfos?.find { it.currency == "CNY" }?.totalBalance
            val newBalance = response.balanceInfos.find { it.currency == "CNY" }?.totalBalance
            result = response
            errorMessage = null
            if (prevBalance != newBalance) saveWidgetData(prefs, response)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            lastQueryTime = sdf.format(Date())
        } catch (e: com.deepseek.balance.network.ApiException) {
            if (showErrors) errorMessage = e.message
        } catch (e: Exception) {
            if (showErrors) errorMessage = "网络错误: ${e.localizedMessage ?: "未知错误"}"
        } finally {
            isLoading = false
        }
    }

    // 自动刷新（静默，失败不弹错）—— 仅 refreshVersion 作为 key，避免在设置页输入 API Key 时反复触发取消/重启协程
    LaunchedEffect(refreshVersion) {
        if (apiKey.isNotBlank() && refreshVersion > 0) {
            doRefresh(showErrors = false)
        }
    }

    // 手动查询（显示错误）
    val onQueryClick: () -> Unit = {
        scope.launch { doRefresh() }
    }

    // ============================================================
    // 页面切换：两屏始终在组合树中，通过 graphicsLayer 位移驱动滑动。
    // 设置页在 App 启动时就已预组合，入口动画时无 Composition 开销。
    // 动画由 animateFloatAsState 驱动（最轻量的 Compose 动画路径）。
    // ============================================================
    val animProgress by animateFloatAsState(
        targetValue = if (showSettings) 1f else 0f,
        animationSpec = tween(260),
        label = "slide",
    )

    // 系统返回键处理
    BackHandler(enabled = showSettings) {
        showSettings = false
    }

    // 两屏始终在组合树中，通过 graphicsLayer 位移驱动平滑滑动
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        // ---- 主界面 ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = -animProgress * size.width.toFloat()
                },
        ) {
            MainScreen(
                apiKey = apiKey,
                onSettingsClick = { showSettings = true },
                onQueryClick = onQueryClick,
                isLoading = isLoading,
                result = result,
                errorMessage = errorMessage,
                lastQueryTime = lastQueryTime,
                alertEnabled = alertEnabled,
                alertThreshold = alertThreshold.toDoubleOrNull() ?: 50.0,
                settingsVisible = showSettings,
            )
        }
        // ---- 设置页 ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = (1f - animProgress) * size.width.toFloat()
                },
        ) {
            SettingsScreen(
                apiKey = apiKey,
                onApiKeyChange = {
                    apiKey = it
                    prefs.edit().putString("api_key", it).apply()
                },
                onBackClick = { showSettings = false },
                alertEnabled = alertEnabled,
                onAlertEnabledChange = {
                    alertEnabled = it
                    prefs.edit().putBoolean("alert_enabled", it).apply()
                },
                alertThreshold = alertThreshold,
                onAlertThresholdChange = {
                    alertThreshold = it
                    prefs.edit().putString("alert_threshold", it).apply()
                },
                widgetRealtime = widgetRealtime,
                onWidgetRealtimeChange = {
                    widgetRealtime = it
                    prefs.edit().putBoolean("widget_realtime", it).apply()
                    onRealtimeToggle(it)
                },
                widgetIntervalSec = widgetIntervalSec,
                onWidgetIntervalSecChange = {
                    widgetIntervalSec = it
                    prefs.edit().putInt("widget_interval_sec", it).apply()
                    onRealtimeIntervalChange()
                },
            )
        }
    }
}

/** 保存数据到 Widget 共享存储 */
private fun saveWidgetData(
    prefs: android.content.SharedPreferences,
    response: com.deepseek.balance.model.BalanceResponse,
) {
    val cnyInfo = response.balanceInfos.find { it.currency == "CNY" }
    prefs.edit()
        .putString(BalanceWidgetProvider.KEY_BALANCE, cnyInfo?.totalBalance ?: "0.00")
        .putString(BalanceWidgetProvider.KEY_SYMBOL, cnyInfo?.symbol ?: "¥")
        .putString(BalanceWidgetProvider.KEY_GRANTED, cnyInfo?.grantedBalance ?: "0.00")
        .putString(BalanceWidgetProvider.KEY_TOPPED_UP, cnyInfo?.toppedUpBalance ?: "0.00")
        .putBoolean(BalanceWidgetProvider.KEY_AVAILABLE, response.isAvailable)
        .putString(
            BalanceWidgetProvider.KEY_UPDATE_TIME,
            java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
        )
        .apply()
}
