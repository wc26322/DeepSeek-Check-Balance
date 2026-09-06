package com.deepseek.balance.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.deepseek.balance.ui.utils.DampedDragAnimation
import com.deepseek.balance.ui.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin

// 逐字复刻 AndroidLiquidGlass demo 的 LiquidBottomTabs（Apache-2.0）三层结构：
//   ① 可见玻璃栏 + 真实 tab（官方 clickable）；
//   ② alpha(0) 强调色 tab 副本行 → 录制进 tabsBackdrop（colorFilter 染 accentColor）；
//   ③ 无子内容的胶囊，折射 combined(backdrop, tabsBackdrop)，蓝色图标经折射呈现。
// 与官方的差异均有明确理由：
//   ① 防重影：胶囊/整栏 layerBlock 的 transformOrigin 钉在左上角（库逆变换
//      inverseTransformAtTopLeft 只认 Offset.Zero），官方默认中心基准会导致正逆变换
//      抵消不干净、折射内容错位几十像素（重影根因，官方 demo 靠花哨壁纸遮掩）；
//      中心生长的观感改由外层 graphicsLayer 按最终缩放值反向平移补偿实现。
//   ② 桥接：tab 固定首页/设置两页，selectedTabIndex 用「无 key remember 的稳定 lambda」桥接
//      isSettings；currentIndex 用 remember(selectedTabIndex)，严禁按 isSettings 重建 state。
//   ③ 效果参数（扭曲/模糊/底色/磨砂/色散）由设置页 NavGlassTuning 实时调节。
internal val LocalLiquidBottomTabScale =
    staticCompositionLocalOf { { 1f } }

/**
 * 导航栏液态玻璃可调参数：由设置页「导航栏玻璃效果」卡的滑块实时调节，SharedPreferences 持久化。
 */
data class NavGlassTuning(
    /** 导航栏背景板（整条玻璃材质层）高度（dp）：胶囊/图标/手势层保持官方 56dp 不变，背景板独立升降。默认 64 */
    val barHeightDp: Float = 64f,
    /** 胶囊边缘扭曲带宽（静止基值，dp）。按压时叠加官方增量 +10dp */
    val refractionHeightDp: Float = 0f,
    /** 胶囊边缘扭曲强度（静止基值，dp）。按压时叠加官方增量 +14dp */
    val refractionAmountDp: Float = 0f,
    /** 整条玻璃栏的背景模糊半径（dp），默认 12 */
    val blurDp: Float = 12f,
    /** 玻璃底色不透明度（0=全透，1=不透明），默认 0 */
    val containerAlpha: Float = 0f,
    /** 胶囊磨砂度（dp）：雾化胶囊内透出的内容，拉高可消除"镜面"感 */
    val capsuleBlurDp: Float = 0f,
    /** 按住时胶囊上下边框外移量（dp）：在官方果冻按压缩放之上额外纵向拉伸，负值=向内收缩，0=关闭。默认 12 */
    val pressStretchVDp: Float = 12f,
    /** 按住时胶囊左右边框外移量（dp）：在官方果冻按压缩放之上额外横向拉伸，负值=向内收缩，0=官方原版。默认 6 */
    val pressStretchHDp: Float = 6f,
    /** 边缘模糊（dp）：只把胶囊边缘一圈磨砂（环宽=模糊半径），0=关闭；中心折射不受影响 */
    val edgeBlurDp: Float = 0f,
    /** 胶囊边缘彩色色散（彩虹边），默认开启 */
    val chromaticAberration: Boolean = true,
    /**
     * 切换粘滞强度（0=完全跟手，0.9=强磁吸）：拖动时靠近 tab 位置移动变慢（像被吸住），
     * 越过中点后加速"弹"过去 → 物理档位感。上限 0.9（=1 时档位处导数为 0，会彻底拖不动）。默认 0
     */
    val detentStrength: Float = 0f,
    /** 段落量化（0=连续跟手，1=纯档位跳变）：拖动时按 tab 位置逐格跳动。默认 1 */
    val detentQuantize: Float = 1f,
    /** 段落震动：拖动跨越档位的瞬间来一记轻震动 */
    val detentHaptics: Boolean = true,
)

@Composable
private fun RowScope.LiquidBottomTab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val scale = LocalLiquidBottomTabScale.current
    Column(
        modifier
            .clip(Capsule())
            .clickable(
                interactionSource = null,
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                val scale = scale()
                scaleX = scale
                scaleY = scale
            },
        verticalArrangement = Arrangement.spacedBy(2f.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
internal fun BottomTabs(
    backdrop: Backdrop,
    isSettings: Boolean,
    onTabSelected: (Boolean) -> Unit,
    tuning: NavGlassTuning = NavGlassTuning(),
    modifier: Modifier = Modifier,
) {
    val tabsCount = 2
    // 官方签名：selectedTabIndex: () -> Int。用「无 key remember 的稳定 lambda」桥接
    // isSettings —— lambda 身份终生不变，currentIndex 不会被重建；
    // isSettings 经 rememberUpdatedState 中转：直接捕获参数会固化首次组合的旧值
    // （外部切页时 snapshotFlow 永远读旧值 → 胶囊不动），经 State 中转才能读到实时值。
    val currentIsSettings by rememberUpdatedState(isSettings)
    // 同理：DampedDragAnimation 也是 remember(animationScope) 一次性创建，其回调 lambda 会
    // 固化首次组合捕获的值。调参滑块要实时生效，tuning 也必须经 rememberUpdatedState 中转。
    val currentTuning by rememberUpdatedState(tuning)
    val selectedTabIndex: () -> Int = remember { { if (currentIsSettings) 1 else 0 } }
    val onTabSelectedIndex: (index: Int) -> Unit = { onTabSelected(it == 1) }

    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val contentColor =
        if (isLightTheme) Color.Black else Color.White
    val containerColor =
        if (isLightTheme) Color(0xFFFAFAFA).copy(alpha = tuning.containerAlpha)
        else Color(0xFF121212).copy(alpha = tuning.containerAlpha)

    // 层②录制层：只录"纯玻璃底板"（磨砂背景+底色），不含任何图标——
    // 胶囊折射 combined(壁纸, 本层) 后与整条栏材质一致（官方观感），
    // 且图标不进折射内容，边缘扭曲永远卷不出"图案倒影"。
    val tabsBackdrop = rememberLayerBackdrop()
    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        // 档位手感状态（普通数组而非 State：拖拽中改变不能触发重组）
        // ① rawDrag：手指【原始行程】累加器（未经粘滞变换）。必须单独记录——
        //    量化开启后 targetValue 会被吸附到整数档位，若增量基于它累加会丢失行程 → 拖不动。
        // ② lastDetentIndex：上一次震动所在档位，跨档才震（避免同档内抖动连震）
        val rawDrag = remember { floatArrayOf(0f) }
        val lastDetentIndex = remember { intArrayOf(-1) }
        // 触觉反馈：Compose UI 1.12 已移除 LocalHapticFeedback（PlatformHapticFeedback 是 internal，
        // 外部不可用）→ 直接用平台 API。API 34+ 用 SEGMENT_TICK（专为分段控件/滑块档位设计的"咔哒"），
        // 低版本回退 CLOCK_TICK（API 21+，同为轻"咔哒"，34 起被标记废弃但仍可用）
        val view = LocalView.current
        val detentTick: () -> Unit = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
            } else {
                @Suppress("DEPRECATION")
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 1.2f,
                onDragStarted = { _ ->
                    // 手势挂在胶囊自身（官方架构）：按下时胶囊已经在该 tab 位置上，
                    // **没有落位动作**（官方 demo 同款空实现）。
                    // 不可用 down 坐标换算 v —— down 是胶囊 Box 的【局部】坐标，旧公式按
                    // 全栏坐标设计，胶囊在最右档（设置）时会把 v 算成 0 → 一按就滑回首页。
                    // 拖动基准 rawDrag 已由 onDragStopped / LaunchedEffect 同步为当前档位。
                },
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    // 同步原始行程到吸附后的档位，避免下次拖拽从漂移位置起算
                    rawDrag[0] = targetIndex.toFloat()
                    lastDetentIndex[0] = targetIndex
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    // 关键修复：必须拦截零位移回调！inspectDragGestures 在 onDragStart 之后会
                    // 【同步】补调一次 onDrag(down, Offset.Zero)。此刻 onDragStarted 里
                    // updateValue(手指位置) 刚 launch 的协程尚未被调度执行，targetValue
                    // 读到的还是旧值 → 不拦截的话，这次零位移回调会拿旧值再 launch 一次
                    // animateTo(旧位置)；Animatable 动画互斥、后启动者胜，直接把"滑到手指"
                    // 的动画顶掉——表现为"按下时滑块纹丝不动"（此前的 bug 根因）。
                    // 官方 drag() 循环只在真实位移时才回调，拦截零值语义上绝对安全。
                    if (dragAmount.x != 0f) {
                        // ① 先累加手指原始行程（未变换）——粘滞/量化都只作用于显示值
                        rawDrag[0] = (rawDrag[0] + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        // ② 粘滞曲线：档位附近慢（被吸住）、越过中点快（弹过去）
                        val shaped = applyDetent(rawDrag[0], currentTuning.detentStrength)
                        // ③ 段落量化：按权重把连续位置拉向最近档位 → 逐格跳动
                        val quantized = lerp(
                            shaped,
                            shaped.fastRoundToInt().toFloat(),
                            currentTuning.detentQuantize
                        )
                        val target = quantized.fastCoerceIn(0f, (tabsCount - 1).toFloat())
                        updateValue(target)
                        // ④ 段落震动：跨越档位的瞬间轻震一记
                        if (currentTuning.detentHaptics) {
                            val idx = target.fastRoundToInt()
                            if (idx != lastDetentIndex[0]) {
                                lastDetentIndex[0] = idx
                                detentTick()
                            }
                        }
                        animationScope.launch {
                            offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                        }
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    // 外部切页（点 tab）时同步档位状态，保证下次拖拽从正确基准起算
                    rawDrag[0] = index.toFloat()
                    lastDetentIndex[0] = index
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelectedIndex(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer {
                    // 官方滑动偏移 + 中心生长补偿：layerBlock 缩放基准钉左上角（与库逆变换
                    // 基准一致 → 折射内容零错位、无重影），增长量全推向右下；
                    // 此处按同一缩放值反向平移半个增量 → 视觉等效从中心放大（官方原版观感）
                    val progress = safeProgress(dampedDragAnimation.pressProgress)
                    val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                    translationX = panelOffset - (scale - 1f) * size.width / 2f
                    translationY = -(scale - 1f) * size.height / 2f
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(tuning.blurDp.dp.toPx())
                        lens(24f.dp.toPx(), 24f.dp.toPx())
                    },
                    layerBlock = {
                        // 官方原版：按压时整栏等比放大 16dp（X/Y 同值）。基准必须钉左上角
                        // （库逆变换 inverseTransformAtTopLeft 只认 Offset.Zero），中心放大的
                        // 观感由上方外层 graphicsLayer 的平移补偿实现
                        transformOrigin = TransformOrigin(0f, 0f)
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(tuning.barHeightDp.dp)
                .fillMaxWidth()
                .padding(vertical = 8f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = {
                LiquidBottomTab(onClick = { onTabSelected(false) }) {
                    Icon(Icons.Filled.Home, contentDescription = null, Modifier.size(28f.dp), tint = contentColor)
                    Text("首页", fontSize = 12f.sp, color = contentColor)
                }
                LiquidBottomTab(onClick = { onTabSelected(true) }) {
                    Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(28f.dp), tint = contentColor)
                    Text("设置", fontSize = 12f.sp, color = contentColor)
                }
            }
        )

        // 层②（官方原版结构）：隐藏的强调色 tab 副本行——alpha(0) 自身不可见，
        // 录制进 tabsBackdrop 仅供胶囊折射；选中 tab 的图标经折射在胶囊内呈现强调色，
        // 整行 colorFilter 染色为 accentColor；按压时图标按 1→1.2 弹跳（LocalLiquidBottomTabScale）。
        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            vibrancy()
                            blur(tuning.blurDp.dp.toPx())
                            lens(
                                24f.dp.toPx() * progress,
                                24f.dp.toPx() * progress
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    LiquidBottomTab(onClick = { onTabSelected(false) }) {
                        Icon(Icons.Filled.Home, contentDescription = null, Modifier.size(28f.dp), tint = contentColor)
                        Text("首页", fontSize = 12f.sp, color = contentColor)
                    }
                    LiquidBottomTab(onClick = { onTabSelected(true) }) {
                        Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(28f.dp), tint = contentColor)
                        Text("设置", fontSize = 12f.sp, color = contentColor)
                    }
                }
            )
        }

        // 胶囊（官方原版结构）：无子内容，折射 combined(壁纸, 层②强调色副本)——
        // 选中 tab 的蓝色图标经折射呈现在胶囊内，随胶囊滑动/缩放天然同步。
        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    // 官方滑动位移
                    val baseX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                    // 中心生长补偿（含速度挤压项）：layerBlock 缩放基准钉在左上角（与库逆变换
                    // 基准一致，折射内容零错位），增长量会全部推向右下；此处按「最终缩放值」
                    // 反向平移半个增量 → 视觉等效从中心放大/挤压。
                    // 最终缩放 = 按压弹簧缩放 ∘ 速度形变，须与下方 layerBlock 内的数学逐字一致，
                    // 否则正逆变换抵消不干净又会出现内容错位。
                    val sx = safeScale(dampedDragAnimation.scaleX)
                    val sy = safeScale(dampedDragAnimation.scaleY)
                    // 速度形变项：取绝对值（左右果冻镜像对称）并夹在 ±0.2；
                    // 非有限值（NaN/Infinity）视为 0，避免把缩放算成 NaN 导致胶囊被压扁/拉伸且不恢复
                    val v = safeVelocity(dampedDragAnimation.velocity)
                    val progress = safeProgress(dampedDragAnimation.pressProgress)
                    // 最终缩放 = 按压弹簧缩放 ∘ 速度形变 ∘ 按压边框外移（上下/左右，滑块可调），
                    // 各项均须与下方 layerBlock 内的数学逐字一致，否则正逆变换抵消不干净会重现重影
                    val totalSx = sx / (1f - v * 0.75f) *
                        lerp(1f, 1f + tuning.pressStretchHDp.dp.toPx() / size.width, progress)
                    val totalSy = sy * (1f - v * 0.25f) *
                        lerp(1f, 1f + tuning.pressStretchVDp.dp.toPx() / size.height, progress)
                    translationX = baseX - (totalSx - 1f) * size.width / 2f
                    translationY = -(totalSy - 1f) * size.height / 2f
                }
                // 官方架构：手势（高亮光斑 + 拖动）挂在胶囊自身，不覆盖整条栏 ——
                // 点 tab（胶囊外）只走 clickable → 单一动画路径（LaunchedEffect → animateToValue），
                // 不会像全宽手势层那样与点击切换抢 press/value 动画，杜绝「静止变扁/拉长不恢复」竞态
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        // 磨砂度 > 0 时雾化胶囊内透出的内容（消除"镜面反射"感），
                        // 边缘扭曲照常作用于雾化后的内容
                        if (tuning.capsuleBlurDp > 0f) blur(tuning.capsuleBlurDp.dp.toPx())
                        // 扭曲参数来自设置页滑块：静止用基值，按压时叠加官方原版增量（+10dp/+14dp）
                        lens(
                            (tuning.refractionHeightDp + 10f * progress).dp.toPx(),
                            (tuning.refractionAmountDp + 14f * progress).dp.toPx(),
                            chromaticAberration = tuning.chromaticAberration
                        )
                    },
                    highlight = {
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        Highlight.Default.copy(alpha = progress)
                    },
                    shadow = {
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        Shadow(alpha = progress)
                    },
                    innerShadow = {
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        InnerShadow(
                            radius = 8f.dp * progress,
                            alpha = progress
                        )
                    },
                    // 官方原版 layerBlock：按压缩放 + 速度挤压形变（果冻感）。
                    // 关键修复：库的逆变换(InverseLayerScope)以「左上角」为基准做 1/scale 补偿，
                    // 而 graphicsLayer 默认以「中心」缩放——基准不一致会残留 (S-1)×中心 的偏移
                    // （按压时 S≈1.39 → 折射内容错位几十像素，即此前的重影根因，官方 demo 靠花哨壁纸遮掩）。
                    // 在此把缩放基准显式钉到左上角：正/逆变换精确抵消，胶囊弹跳与内容对齐兼得。
                    layerBlock = {
                        transformOrigin = TransformOrigin(0f, 0f)
                        // 与外层 graphicsLayer 同源的安全化值，数学逐字一致（详见上方注释）
                        val sx = safeScale(dampedDragAnimation.scaleX)
                        val sy = safeScale(dampedDragAnimation.scaleY)
                        val velocity = safeVelocity(dampedDragAnimation.velocity)
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        scaleX = sx / (1f - velocity * 0.75f)
                        scaleY = sy * (1f - velocity * 0.25f)
                        // 用户要求可调：按住时胶囊边框外移——上下/左右各自在官方果冻按压
                        // 之上额外拉伸（设置页「按住时上下/左右边框外移」滑块，0=关闭）。
                        // 外层 graphicsLayer 的 totalSx/totalSy 已按同式补偿，正逆变换精确抵消
                        scaleX *= lerp(1f, 1f + tuning.pressStretchHDp.dp.toPx() / size.width, progress)
                        scaleY *= lerp(1f, 1f + tuning.pressStretchVDp.dp.toPx() / size.height, progress)
                    },
                    onDrawSurface = {
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        drawRect(
                            if (isLightTheme) Color.Black.copy(0.1f)
                            else Color.White.copy(0.1f),
                            alpha = 1f - progress
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56f.dp)
                .fillMaxWidth(1f / tabsCount)
        )

        // 边缘模糊环：把 combined(壁纸, 层②) 模糊后只画在胶囊边缘一圈（环宽 = 模糊值，滑块可调）。
        // 实现原理：onDrawBackdrop 在录制期用「外胶囊 − 内缩胶囊」的 EvenOdd 环形路径裁剪，
        // blur RenderEffect 在合成期对环内像素做高斯模糊（环缘自然羽化，外缘被胶囊形状收口）。
        // 位移/缩放补偿与主胶囊逐字一致 → 拖拽/按压/速度挤压全程贴合，折射内容零错位。
        // 无手势修饰符 → 触摸穿透到下方；0dp 时不组合此节点（否则空 effects 会画出清晰环）。
        // 注意：本节点唯一效果是 blur() 且 edgeTreatment 走默认 TileMode.Clamp —— 按库实现
        // （Blur.kt）此时【不会】设置出血边距 padding，故层尺寸 = 节点尺寸、画布无平移，
        // onDrawBackdrop 的坐标原点即节点左上角、size 即节点实际尺寸（此前误减 2×pad 导致
        // 外轮廓收缩、磨砂环整体向左上偏移且右下缺边，已修正）。
        if (tuning.edgeBlurDp > 0f) {
            Box(
                Modifier
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer {
                        val baseX = if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                            else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                        val sx = safeScale(dampedDragAnimation.scaleX)
                        val sy = safeScale(dampedDragAnimation.scaleY)
                        val v = safeVelocity(dampedDragAnimation.velocity)
                        val progress = safeProgress(dampedDragAnimation.pressProgress)
                        val totalSx = sx / (1f - v * 0.75f) *
                            lerp(1f, 1f + tuning.pressStretchHDp.dp.toPx() / size.width, progress)
                        val totalSy = sy * (1f - v * 0.25f) *
                            lerp(1f, 1f + tuning.pressStretchVDp.dp.toPx() / size.height, progress)
                        translationX = baseX - (totalSx - 1f) * size.width / 2f
                        translationY = -(totalSy - 1f) * size.height / 2f
                    }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = { Capsule() },
                        effects = { blur(tuning.edgeBlurDp.dp.toPx()) },
                        layerBlock = {
                            transformOrigin = TransformOrigin(0f, 0f)
                            val sx = safeScale(dampedDragAnimation.scaleX)
                            val sy = safeScale(dampedDragAnimation.scaleY)
                            val velocity = safeVelocity(dampedDragAnimation.velocity)
                            val progress = safeProgress(dampedDragAnimation.pressProgress)
                            scaleX = sx / (1f - velocity * 0.75f)
                            scaleY = sy * (1f - velocity * 0.25f)
                            scaleX *= lerp(1f, 1f + tuning.pressStretchHDp.dp.toPx() / size.width, progress)
                            scaleY *= lerp(1f, 1f + tuning.pressStretchVDp.dp.toPx() / size.height, progress)
                        },
                        highlight = null,
                        shadow = null,
                        innerShadow = null,
                        onDrawSurface = null,
                        onDrawBackdrop = { drawBackdrop ->
                            // 无出血边（见上方注释）：DrawScope 原点 = 节点左上角，size = 节点实际尺寸。
                            // 外轮廓直接取节点尺寸（与胶囊形状同坐标贴边），内缩 band 形成均匀环形。
                            val pad = tuning.edgeBlurDp.dp.toPx()
                            val w = size.width
                            val h = size.height
                            val band = pad.coerceAtMost(minOf(w, h) / 2f - 1f).coerceAtLeast(0f)
                            if (w > 0f && h > 0f && band > 0f) {
                                val ring = Path().apply {
                                    fillType = PathFillType.EvenOdd
                                    addPath(capsulePath(w, h, Offset.Zero))
                                    addPath(capsulePath(w - 2f * band, h - 2f * band, Offset(band, band)))
                                }
                                clipPath(ring) { drawBackdrop() }
                            }
                        }
                    )
                    .height(56f.dp)
                    .fillMaxWidth(1f / tabsCount)
            )
        }
    }
}

/** 动画值安全化：Animatable 偶发 NaN/Infinity（速度估算极端时序、动画互斥打断）会把
 *  缩放/进度算成 NaN → 胶囊被渲染成扁平/拉伸且不恢复。统一回退到常态值再参与计算。 */
private fun safeScale(v: Float) = if (v.isFinite()) v.coerceIn(0.5f, 2f) else 1f

private fun safeProgress(v: Float) = if (v.isFinite()) v.coerceIn(0f, 1f) else 0f

/** 速度形变项：取绝对值（左右果冻镜像对称）并夹在 ±0.2，非有限值视为 0 */
private fun safeVelocity(v: Float) = if (v.isFinite()) (abs(v) / 10f).fastCoerceIn(-0.2f, 0.2f) else 0f

/**
 * 档位粘滞曲线：把手指的线性行程映射为"有档位感"的位置。
 *
 * 公式：D(x) = ⌊x⌋ + t − s·sin(2πt)/(2π)，其中 t = x − ⌊x⌋，s = 粘滞强度
 * 性质（三条缺一不可，都是刻意设计出来的）：
 *   1. D(k) = k —— 整数档位是【不动点】，档位处滑块位置与手指行程基准一致；
 *   2. D(0.5) = 0.5 —— 中点也是不动点，且 D 单调递增 ⇒ "有没有拖过一半"的判定
 *      与无粘滞时【完全等价】，不会出现"明明拖过一半又弹回去"；
 *   3. 导数 D'(t) = 1 − s·cos(2πt)：档位附近 = 1−s（慢，像被吸住），
 *      中点 = 1+s（快，"弹"过去）——这就是磁吸档位/段落感的数学来源。
 * s 必须 < 1（s=1 时档位处导数为 0，滑块会被彻底"焊死"在档位上拖不动）。
 */
private fun applyDetent(x: Float, strength: Float): Float {
    if (strength <= 0f) return x
    val twoPi = (2.0 * PI).toFloat()
    val k = floor(x)
    val t = x - k
    return k + t - strength * sin(twoPi * t) / twoPi
}

/** 胶囊轮廓路径（FullyRounded：圆角半径 = 短边一半） */
private fun capsulePath(width: Float, height: Float, topLeft: Offset): Path = Path().apply {
    val r = minOf(width, height) / 2f
    addRoundRect(
        RoundRect(
            rect = Rect(topLeft, Size(width, height)),
            cornerRadius = CornerRadius(r, r)
        )
    )
}
