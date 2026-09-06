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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.metrics.performance.JankStats
import com.deepseek.balance.model.BalanceResponse
import com.deepseek.balance.model.DailyUsage
import com.deepseek.balance.model.ModelDailyUsage
import com.deepseek.balance.model.ModelUsage
import com.deepseek.balance.model.UsageData
import com.deepseek.balance.network.ApiClient
import com.deepseek.balance.network.UsageClient
import com.deepseek.balance.ui.AppBackground
import com.deepseek.balance.ui.BottomTabs
import com.deepseek.balance.ui.NavGlassTuning
import com.deepseek.balance.ui.LocalLiquidCardBackdrop
import com.deepseek.balance.ui.MainScreen
import com.deepseek.balance.ui.SettingsScreen
import com.deepseek.balance.ui.WebLoginScreen
import com.deepseek.balance.ui.theme.DeepSeekBalanceTheme
import com.deepseek.balance.widget.BalanceWidgetProvider
import com.deepseek.balance.widget.WidgetAutoRefreshService
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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

        // 透明状态栏/导航栏（edge-to-edge）：背景层（自定义图片/光斑）延伸到系统栏后面，
        // 系统图标深浅自动跟随系统明暗模式；
        // 内容侧防遮挡：两屏内容吃 Scaffold 的 innerPadding，悬浮玻璃顶/底栏各自 *BarsPadding
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
        )

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

    /** 检查指定前台服务是否正在运行（用于把 UI 开关状态与服务真实状态对齐） */
    @Suppress("DEPRECATION")
    private fun isServiceRunning(serviceClass: Class<*>): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        for (service in am.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) return true
        }
        return false
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

        // 自愈：实时刷新开关为开、但前台服务因被系统回收而没在运行时，自动拉起。
        // 放在 onResume 是因为此时 Activity 确定在前台，避免 Android 12+ 在 onCreate 阶段直接
        // startForegroundService 触发「后台启动前台服务」限制，导致启动被系统抑制。
        if (prefs.getBoolean("widget_realtime", true)
            && !isServiceRunning(WidgetAutoRefreshService::class.java)
        ) {
            ensureNotificationPermissionThenStart()
        }

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

@OptIn(ExperimentalMaterial3Api::class)
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

    // 网页一键登录（WebView 覆盖层）
    var showWebLogin by remember { mutableStateOf(false) }

    // 标记当前页面，便于在 Logcat 中区分主界面/设置页的掉帧
    LaunchedEffect(showSettings) {
        onScreenChange(if (showSettings) "settings" else "main")
    }

    // API Key（官方余额接口鉴权）
    var apiKey by remember {
        mutableStateOf(prefs.getString("api_key", "") ?: "")
    }

    // 网页令牌（platform.deepseek.com 用量接口鉴权，与 API Key 不同）
    var webToken by remember {
        mutableStateOf(prefs.getString("web_token", "") ?: "")
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

    // 自定义背景图片（filesDir 内的文件路径；空串 = 默认光斑背景）。
    // 设置页选图后把图片复制进 filesDir，这里只存路径，重启后依然生效
    var backgroundImage by remember {
        mutableStateOf(prefs.getString("background_image", "") ?: "")
    }

    // 导航栏液态玻璃调参（设置页滑块实时调节，prefs 持久化）
    var navGlassTuning by remember {
        mutableStateOf(
            NavGlassTuning(
                barHeightDp = prefs.getFloat("nav_glass_bar_height", 64f),
                refractionHeightDp = prefs.getFloat("nav_glass_refraction_h", 0f),
                refractionAmountDp = prefs.getFloat("nav_glass_refraction_a", 0f),
                blurDp = prefs.getFloat("nav_glass_blur", 12f),
                containerAlpha = prefs.getFloat("nav_glass_alpha", 0f),
                capsuleBlurDp = prefs.getFloat("nav_glass_capsule_blur", 0f),
                pressStretchVDp = prefs.getFloat("nav_glass_press_stretch_v", 12f),
                pressStretchHDp = prefs.getFloat("nav_glass_press_stretch_h", 6f),
                edgeBlurDp = prefs.getFloat("nav_glass_edge_blur", 0f),
                chromaticAberration = prefs.getBoolean("nav_glass_chroma", true),
                detentStrength = prefs.getFloat("nav_glass_detent", 0f),
                detentQuantize = prefs.getFloat("nav_glass_detent_quant", 1f),
                detentHaptics = prefs.getBoolean("nav_glass_detent_haptic", true),
            )
        )
    }
    // 调参持久化节流：拖动滑块每帧回调，磁盘写入最多每 500ms 一次（内存预览不受影响）
    var lastNavGlassPersist by remember { mutableLongStateOf(0L) }

    // 数据
    var isLoading by remember { mutableStateOf(false) }
    var result by remember {
        // 有 API Key 的冷启动：先显示 0 余额占位卡片（不闪空状态提示），
        // 自动刷新完成后更新为新数据；无 API Key 时不显示，由 EmptyState 引导去设置
        val hasKey = !(prefs.getString("api_key", "") ?: "").isBlank()
        val initial: com.deepseek.balance.model.BalanceResponse? =
            if (hasKey) {
                com.deepseek.balance.model.BalanceResponse(
                    isAvailable = true,
                    balanceInfos = listOf(
                        com.deepseek.balance.model.BalanceInfo("CNY", "0.00", "0.00", "0.00"),
                    ),
                )
            } else {
                null
            }
        mutableStateOf(initial)
    }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var lastQueryTime by remember { mutableStateOf<String?>(null) }

    // 刷新完成计数：每次成功刷新 +1，驱动余额数字从 0 滚动到当前值
    var refreshCount by remember { mutableStateOf(0) }

    // 数据已更新事件：成功刷新（拿到新数据）时 +1，MainScreen 收到即弹「已显示最新数据」提示
    var dataUpdatedTick by remember { mutableStateOf(0) }

    // 用量数据（网页令牌鉴权）
    var usage by remember {
        // 有网页令牌的冷启动：先显示结构完整、数值全 0 的占位卡片框架（与余额卡同理），
        // 自动刷新完成后原地填充真实数据；无令牌时不显示，由 NoTokenHint 引导去设置
        val hasToken = !(prefs.getString("web_token", "") ?: "").isBlank()
        val initial: UsageData? = if (hasToken) placeholderUsage() else null
        mutableStateOf(initial)
    }
    var usageError by remember { mutableStateOf<String?>(null) }
    // 网页令牌是否已失效（HTTP 401/403 或业务码 40003）：失效时在主界面显示「重新登录」引导
    var webTokenInvalid by remember { mutableStateOf(false) }

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

    suspend fun doRefresh(showErrors: Boolean = true, minDurationMs: Long = 0) {
        val startMs = System.currentTimeMillis()
        isLoading = true
        if (showErrors) {
            errorMessage = null
            usageError = null
        }
        var balanceResp: com.deepseek.balance.model.BalanceResponse? = null
        var balanceErr: String? = null
        var usageResp: com.deepseek.balance.model.UsageData? = null
        var usageErr: String? = null
        try {
            coroutineScope {
                val bj = async {
                    if (apiKey.isNotBlank()) {
                        try {
                            ApiClient.getBalance(apiKey)
                        } catch (e: Exception) {
                            e
                        }
                    } else {
                        null
                    }
                }
                val uj = async {
                    if (webToken.isNotBlank()) {
                        try {
                            UsageClient.getUsage(webToken)
                        } catch (e: Exception) {
                            e
                        }
                    } else {
                        null
                    }
                }
                val b = bj.await()
                val u = uj.await()
                if (b is Exception) balanceErr = b.message ?: "余额查询失败"
                else if (b is com.deepseek.balance.model.BalanceResponse) balanceResp = b
                if (u is Exception) {
                    usageErr = u.message ?: "用量查询失败"
                    // 令牌失效（HTTP 401/403 或业务码 40003）→ 标记，UI 显示「重新登录」引导
                    if (u is com.deepseek.balance.network.ApiException &&
                        u.code in TOKEN_INVALID_CODES
                    ) {
                        webTokenInvalid = true
                    }
                } else if (u is com.deepseek.balance.model.UsageData) usageResp = u
            }

            if (balanceResp != null) {
                val prev = result?.balanceInfos?.find { it.currency == "CNY" }?.totalBalance
                val new = balanceResp!!.balanceInfos.find { it.currency == "CNY" }?.totalBalance
                result = balanceResp
                if (prev != new) saveWidgetData(prefs, balanceResp!!)
            }
            if (balanceErr != null && showErrors) errorMessage = balanceErr

            // 静默刷新失败时保留占位/旧数据（与余额一致）：仅成功时才替换，失败不清空
            if (usageResp != null) {
                usage = usageResp
                usageError = null
                webTokenInvalid = false
            }
            // 手动刷新才显示错误
            if (usageErr != null && showErrors) usageError = usageErr
            // 同步用量（总/今日 Tokens）到小组件存储
            if (usageResp != null) {
                com.deepseek.balance.widget.WidgetRefresh.saveTokens(prefs, usageResp)
            }

            if (result != null || usage != null) {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                lastQueryTime = sdf.format(Date())
                refreshCount++
                // 数据已更新：立即通知界面弹提示（不等刷新指示器收回）
                dataUpdatedTick++
            }
        } catch (e: Exception) {
            if (showErrors && errorMessage == null && usageError == null) {
                errorMessage = "网络错误: ${e.localizedMessage ?: "未知错误"}"
            }
        } finally {
            // 手动刷新时保证指示器至少转几圈再收回（避免网络快时一闪而过）
            if (minDurationMs > 0) {
                val remain = minDurationMs - (System.currentTimeMillis() - startMs)
                if (remain > 0) delay(remain)
            }
            isLoading = false
        }
    }

    // 自动刷新（静默，失败不弹错）—— 仅 refreshVersion 作为 key，避免在设置页输入 API Key 时反复触发取消/重启协程。
    // 任一凭证（API Key 或网页令牌）存在即自动刷新：占位卡片等待被真实数据填充
    LaunchedEffect(refreshVersion) {
        if ((apiKey.isNotBlank() || webToken.isNotBlank()) && refreshVersion > 0) {
            doRefresh(showErrors = false)
        }
    }

    // 手动查询（显示错误）；刷新动画至少转几圈（800ms）再收回
    val onQueryClick: () -> Unit = {
        scope.launch { doRefresh(minDurationMs = 800L) }
    }

    // 每日用量「本月/上月/自定义」维度：按日期范围拉取月度接口数据。
    // 网络/解析异常在此捕获并返回 null，避免在 LaunchedEffect 协程中抛异常导致闪退。
    val loadRangeDaily: suspend (java.time.LocalDate, java.time.LocalDate) -> List<com.deepseek.balance.model.ModelDailyUsage>? =
        { start, end ->
            if (webToken.isBlank()) null
            else try {
                UsageClient.getRangeDaily(webToken, start, end)
            } catch (e: Exception) {
                null
            }
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
    BackHandler(enabled = showWebLogin) {
        showWebLogin = false
    }
    BackHandler(enabled = showSettings && !showWebLogin) {
        showSettings = false
    }

    // 液态玻璃：录制环境光斑/壁纸背景层（官方 BackdropDemoScaffold 中"壁纸"的同角色），
    // 供全 app 玻璃组件（LiquidCard 卡片 + BottomTabs 底栏）折射。
    // 对齐官方架构：所有玻璃组件只折射壁纸层、绝不折射滚动内容 ——
    // ① 卡片/底栏在录制层之外（兄弟子树），无 prepareTree 递归风险；
    // ② 壁纸层静止不随滚动重录，滚动零额外录制开销（此前两屏录制层是设置页滚动掉帧主因）；
    // ③ 胶囊里不会出现"卡片内容副本"（之前被误认为多出一层的东西）。
    val ambientBackdrop = rememberLayerBackdrop()

    // 两屏始终在组合树中，通过 graphicsLayer 位移驱动平滑滑动
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
    ) {
        // ---- 背景层（最底层，官方"壁纸"角色）：自定义图片或默认光斑 ----
        // 录制修饰符挂在与所有玻璃组件（卡片/底栏）为兄弟关系的背景层上
        AppBackground(
            backgroundImagePath = backgroundImage,
            modifier = Modifier.layerBackdrop(ambientBackdrop),
        )

        CompositionLocalProvider(LocalLiquidCardBackdrop provides ambientBackdrop) {
        // ---- 主界面 + 设置页（滑动切换） ----
        // 两屏不录制进任何 backdrop（官方架构：玻璃只折射壁纸层）。
        // 底栏 BottomTabs 改为消费 ambientBackdrop，与卡片同源。
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
                    usage = usage,
                    usageError = usageError,
                    hasWebToken = webToken.isNotBlank(),
                    webTokenInvalid = webTokenInvalid,
                    onWebLoginClick = { showWebLogin = true },
                    loadRangeDaily = loadRangeDaily,
                    alertEnabled = alertEnabled,
                    alertThreshold = alertThreshold.toDoubleOrNull() ?: 50.0,
                    settingsVisible = showSettings,
                    refreshCount = refreshCount,
                    dataUpdatedTick = dataUpdatedTick,
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
                    webToken = webToken,
                    onWebTokenChange = {
                        webToken = it
                        prefs.edit().putString("web_token", it).apply()
                    },
                    onWebLoginClick = { showWebLogin = true },
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
                    backgroundImagePath = backgroundImage,
                    onBackgroundImageChange = {
                        backgroundImage = it
                        prefs.edit().putString("background_image", it).apply()
                    },
                    navGlassTuning = navGlassTuning,
                    onNavGlassTuningChange = { tuning ->
                        // 内存立即更新（实时预览）；磁盘写入节流——拖动滑块每帧都在回调，
                        // 每帧 apply() 会产生大量异步 IO 排队，是调参掉帧的次要来源。
                        // 拖动结束后 500ms 内的改动会随最后一次节流写入落盘，最多丢半秒调整值
                        navGlassTuning = tuning
                        val now = System.currentTimeMillis()
                        // 持久化时剔除 interactiveDegrade（拖动降级是临时标记，不应写盘）
                        if (now - lastNavGlassPersist >= 500 && !tuning.interactiveDegrade) {
                            lastNavGlassPersist = now
                            persistNavGlassTuning(prefs, tuning)
                        }
                    },
                )
            }
        }
        } // CompositionLocalProvider(LocalLiquidCardBackdrop)

        // ---- 悬浮底部标签栏（首页/设置）：两屏从其下方滑过 ----
        // 对齐官方：底栏玻璃折射壁纸层（ambientBackdrop），不折射滚动内容
        BottomTabs(
            backdrop = ambientBackdrop,
            isSettings = showSettings,
            onTabSelected = { showSettings = it },
            tuning = navGlassTuning,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // edge-to-edge：避开透明导航栏/手势条
                .padding(bottom = 16.dp)
                .padding(horizontal = 24.dp)
                .fillMaxWidth(),
        )

        // ---- 网页一键登录（WebView 全屏覆盖层） ----
        if (showWebLogin) {
            WebLoginScreen(
                onTokenObtained = { token ->
                    webToken = token
                    prefs.edit().putString("web_token", token).apply()
                    showWebLogin = false
                    scope.launch { doRefresh() }
                },
                onClose = { showWebLogin = false },
            )
        }
    }
}

/** 网页令牌失效的错误码：HTTP 401/403（fetchJson）与业务码 40003（bizData，Authorization Failed） */
private val TOKEN_INVALID_CODES = setOf(401, 403, 40003)

/** 冷启动占位用量数据：结构与真实数据完全一致、数值全 0，渲染出真实卡片框架 */
private fun placeholderUsage(): UsageData {
    // 近 7 天真实日期（MM-dd），数值全 0：柱状图显示空架子（无柱子），日期刻度正常
    val fmt = java.text.SimpleDateFormat("MM-dd", Locale.getDefault())
    val dates = (6 downTo 0).map { fmt.format(Date(System.currentTimeMillis() - it * 86400000L)) }
    return UsageData(
        totalCostCny = 0.0,
        apiCalls = 0,
        totalTokens = 0L,
        windowDays = 30,
        byModel = listOf(
            ModelUsage("deepseek-v4-flash", 0, 0L, 0.0),
            ModelUsage("deepseek-v4-pro", 0, 0L, 0.0),
        ),
        byKey = listOf(),
        byModelDaily = listOf(
            ModelDailyUsage(
                model = "deepseek-v4-flash",
                daily = dates.map { DailyUsage(it, 0, 0L, 0L, 0L) },
            ),
            ModelDailyUsage(
                model = "deepseek-v4-pro",
                daily = dates.map { DailyUsage(it, 0, 0L, 0L, 0L) },
            ),
        ),
    )
}

/** 将导航栏玻璃调参持久化到 SharedPreferences（拖动时按 500ms 节流调用） */
private fun persistNavGlassTuning(
    prefs: android.content.SharedPreferences,
    tuning: NavGlassTuning,
) {
    // interactiveDegrade 是拖动期临时降级标记，不持久化（调用方已过滤，双重保险）
    val tuning = tuning.copy(interactiveDegrade = false)
    prefs.edit()
        .putFloat("nav_glass_bar_height", tuning.barHeightDp)
        .putFloat("nav_glass_refraction_h", tuning.refractionHeightDp)
        .putFloat("nav_glass_refraction_a", tuning.refractionAmountDp)
        .putFloat("nav_glass_blur", tuning.blurDp)
        .putFloat("nav_glass_alpha", tuning.containerAlpha)
        .putFloat("nav_glass_capsule_blur", tuning.capsuleBlurDp)
        .putFloat("nav_glass_press_stretch_v", tuning.pressStretchVDp)
        .putFloat("nav_glass_press_stretch_h", tuning.pressStretchHDp)
        .putFloat("nav_glass_edge_blur", tuning.edgeBlurDp)
        .putBoolean("nav_glass_chroma", tuning.chromaticAberration)
        .putFloat("nav_glass_detent", tuning.detentStrength)
        .putFloat("nav_glass_detent_quant", tuning.detentQuantize)
        .putBoolean("nav_glass_detent_haptic", tuning.detentHaptics)
        .apply()
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
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
        )
        .apply()
}
