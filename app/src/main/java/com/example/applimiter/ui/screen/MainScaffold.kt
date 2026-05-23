package com.example.applimiter.ui.screen

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.applimiter.ui.screen.tab.AboutTab
import com.example.applimiter.ui.screen.tab.HomeTab
import com.example.applimiter.ui.screen.tab.RulesTab
import com.example.applimiter.ui.theme.AppPalette
import com.example.applimiter.ui.theme.GlassSurface
import com.example.applimiter.ui.theme.LiquidGlassBackground
import com.example.applimiter.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

internal enum class Tab(val title: String, val subtitle: String, val icon: ImageVector) {
    Home("首页", "保持专注 · 守住时间", Icons.Default.Home),
    Rules("规则", "管理你设置的所有限制", Icons.Default.List),
    About("关于", "设计与初衷", Icons.Default.Info)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    viewModel: MainViewModel,
    onAddRuleClick: () -> Unit,
    onEditRuleClick: (Long) -> Unit,
    onOpenAppRules: (String) -> Unit
) {
    var current by rememberSaveable { mutableStateOf(Tab.Home) }
    val perm = rememberPermissionsSnapshot()

    // 监听规则列表里的"打开 app 规则页"请求（轮询模式，简单稳）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(100)
            val target = com.example.applimiter.ui.screen.tab.AppRulesNavBus.navigateTo
            if (target != null) {
                com.example.applimiter.ui.screen.tab.AppRulesNavBus.navigateTo = null
                onOpenAppRules(target)
            }
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                LiquidNavBar(
                    current = current,
                    onSelect = { current = it }
                )
            },
            floatingActionButton = {
                // 规则页 FAB 已移除：添加规则只能进入某个应用后再添加
            }
        ) { padding ->
            AnimatedContent(
                targetState = current,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "tab"
            ) { tab ->
                when (tab) {
                    Tab.Home -> HomeTab(
                        viewModel = viewModel,
                        perm = perm,
                        contentPadding = padding
                    )
                    Tab.Rules -> RulesTab(
                        viewModel = viewModel,
                        onAddRuleClick = onAddRuleClick,
                        onEditRuleClick = onEditRuleClick,
                        contentPadding = padding
                    )
                    Tab.About -> AboutTab(contentPadding = padding)
                }
            }
        }
    }
}

@Composable
fun ScrollableTitle(title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 液态玻璃底部导航栏（性能 / 视觉重构版）。
 *
 * 性能要点：
 * 1. 胶囊位置用 [Modifier.graphicsLayer] 的 translationX，只触发 placement 不触发 layout
 * 2. 拖拽期间 translationX = 手指原始 px（无动画/无装箱），跟手 60fps
 * 3. 松手后才用 [Animatable.animateTo] 走 spring 吸附，避免拖拽时弹簧"拽不住"手指
 * 4. 单一手势 awaitEachGesture，down→move→up 一气呵成，不和 ripple/clickable 冲突
 *
 * 视觉要点（色散 / 棱镜折射）：
 * 1. 外层 Modifier.blur(12.dp) 单独绘制色散光晕（沿胶囊外圈的彩虹 sweep），仅在 API 31+ 启用
 * 2. 胶囊外缘 chromatic aberration：分别用红绿蓝偏移描边，模拟真镜头色差
 * 3. 内圈柔光 + 顶部白色高光带 + 底部彩虹反射条
 * 4. sweep gradient 中心做 4.5s 椭圆轨迹偏移，让光"流动"
 */
@Composable
private fun LiquidNavBar(
    current: Tab,
    onSelect: (Tab) -> Unit
) {
    val tabs = remember { Tab.values().toList() }
    val density = LocalDensity.current
    val ld = LocalLayoutDirection.current
    val dark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()

    var pressing by remember { mutableStateOf(false) }
    var hoverIndex by remember { mutableIntStateOf(tabs.indexOf(current)) }
    // 胶囊 X 偏移（px），统一用 Animatable 管理：拖拽时 snapTo（无动画跟手），松手 animateTo（弹簧）
    val pillTranslateX = remember { Animatable(0f) }

    // 紫金发光色谱：紫 → 玫红 → 金 → 玫红 → 紫（首尾闭环，循环流动）
    val prismColors = remember {
        listOf(
            Color(0xFF9B6BFF),  // 紫
            Color(0xFFC773FF),  // 紫粉
            Color(0xFFFFB05A),  // 金橙
            Color(0xFFFFD068),  // 金
            Color(0xFFFFB05A),  // 金橙
            Color(0xFFC773FF),  // 紫粉
            Color(0xFF9B6BFF)   // 紫（闭环）
        )
    }

    val infinite = rememberInfiniteTransition(label = "prism")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // 整条 bar 在按压时放大
    val barScale by animateFloatAsState(
        targetValue = if (pressing) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "bar-scale"
    )
    // 折射强度（默认 0，按压时淡入到 1）
    val rimAlpha by animateFloatAsState(
        targetValue = if (pressing) 1f else 0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "rim-alpha"
    )

    // 当外部 current 变化时（如按返回键、初次进入），把胶囊吸到对应位置
    LaunchedEffect(current) {
        if (!pressing) {
            // 用 itemPx 算目标，下面 Box 里有 itemPx；这里先 snap，下面 box 重新 layout 时会校正
            // 实际同步在 BoxWithConstraints 内 LaunchedEffect 完成
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .graphicsLayer {
                scaleX = barScale
                scaleY = barScale
            }
    ) {
        GlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            // 导航栏单独提高填充不透明度，让胶囊和文字更清晰可读
            fillAlpha = 0.96f
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .height(60.dp)
            ) {
                val totalPx = with(density) { maxWidth.toPx() }
                val itemPx = totalPx / tabs.size
                val itemDp = with(density) { itemPx.toDp() }
                val pillShape = RoundedCornerShape(28.dp)

                // 仅在外部 current 变化（非手势）时把胶囊吸到对应位置
                LaunchedEffect(current, itemPx) {
                    if (itemPx <= 0f) return@LaunchedEffect
                    if (pressing) return@LaunchedEffect
                    val target = itemPx * tabs.indexOf(current)
                    if (kotlin.math.abs(pillTranslateX.value - target) > 0.5f) {
                        pillTranslateX.animateTo(
                            targetValue = target,
                            animationSpec = spring(
                                dampingRatio = 0.7f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        )
                    }
                }

                // 单一手势：按下 → 立刻吸附手指；移动 → snap 跟手；抬起 → 切换 + 弹簧落位
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(tabs, itemPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                pressing = true
                                val firstX = down.position.x
                                hoverIndex = (firstX / itemPx).toInt()
                                    .coerceIn(0, tabs.size - 1)
                                // snap 到手指
                                scope.launch {
                                    pillTranslateX.snapTo(
                                        (firstX - itemPx / 2f)
                                            .coerceIn(0f, totalPx - itemPx)
                                    )
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        // 抬起：手势这里直接弹簧落位，再 onSelect 通知外部
                                        // 落位 + onSelect 都同步触发，避免 LaunchedEffect 抢占
                                        val finalIdx = hoverIndex
                                        val targetX = itemPx * finalIdx
                                        scope.launch {
                                            pillTranslateX.animateTo(
                                                targetValue = targetX,
                                                animationSpec = spring(
                                                    dampingRatio = 0.65f,
                                                    stiffness = Spring.StiffnessMedium
                                                )
                                            )
                                        }
                                        pressing = false
                                        onSelect(tabs[finalIdx])
                                        break
                                    }
                                    if (change.positionChanged()) {
                                        change.consume()
                                        val x = change.position.x
                                        hoverIndex = (x / itemPx).toInt()
                                            .coerceIn(0, tabs.size - 1)
                                        // 拖拽：直接 snap，不走任何动画 → 极致跟手
                                        scope.launch {
                                            pillTranslateX.snapTo(
                                                (x - itemPx / 2f)
                                                    .coerceIn(0f, totalPx - itemPx)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // 计算胶囊当前覆盖的 tab：拖拽中跟 hoverIndex；非拖拽跟 current
                    val anchorIdx = if (pressing) hoverIndex else tabs.indexOf(current)

                    // 1) 底层：未选中态 tab 文字。被胶囊覆盖的那一格留空，避免文字与胶囊内文字重叠
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        tabs.forEachIndexed { idx, t ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (idx != anchorIdx) {
                                    NavLabel(tab = t, selected = false)
                                }
                            }
                        }
                    }

                    // 2) 色散光晕层：双层叠加营造"沿胶囊轮廓"的发光（仅按压时显现）
                    //    - 中晕：blur + clip 到胶囊形状，柔和的彩虹外圈
                    //    - 近核：极小 blur + clip 到胶囊形状，色彩最亮的描边
                    //    两层都裁剪到 pillShape，避免任何矩形溢色
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && rimAlpha > 0.01f) {
                        // 中晕：主彩虹层
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = pillTranslateX.value
                                    alpha = rimAlpha * 0.85f
                                }
                                .width(itemDp)
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .blur(
                                    radius = 8.dp,
                                    edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment(pillShape)
                                )
                                .drawWithCache {
                                    val outline = pillShape.createOutline(size, ld, this)
                                    val cx = size.width / 2f + 18f * cos(phase).toFloat()
                                    val cy = size.height / 2f + 6f * sin(phase).toFloat()
                                    val sweep = Brush.sweepGradient(
                                        prismColors,
                                        center = Offset(cx, cy)
                                    )
                                    onDrawWithContent {
                                        drawOutline(
                                            outline = outline,
                                            brush = sweep,
                                            style = Stroke(width = 5.dp.toPx())
                                        )
                                    }
                                }
                        )
                        // 近核：高饱和细描边
                        Box(
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = pillTranslateX.value
                                    alpha = rimAlpha
                                }
                                .width(itemDp)
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .blur(
                                    radius = 2.dp,
                                    edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment(pillShape)
                                )
                                .drawWithCache {
                                    val outline = pillShape.createOutline(size, ld, this)
                                    val cx = size.width / 2f + 14f * cos(phase).toFloat()
                                    val cy = size.height / 2f + 5f * sin(phase).toFloat()
                                    val sweep = Brush.sweepGradient(
                                        prismColors,
                                        center = Offset(cx, cy)
                                    )
                                    onDrawWithContent {
                                        drawOutline(
                                            outline = outline,
                                            brush = sweep,
                                            style = Stroke(width = 2.dp.toPx())
                                        )
                                    }
                                }
                        )
                    }

                    // 3) 浮动胶囊本体（用 graphicsLayer translationX 跟手，无 layout 开销）
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                translationX = pillTranslateX.value
                            }
                            .width(itemDp)
                            .fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .clip(pillShape)
                                .background(
                                    Brush.verticalGradient(
                                        if (dark) listOf(
                                            Color(0x55FFFFFF), Color(0x22FFFFFF)
                                        ) else listOf(
                                            Color.White.copy(alpha = 0.95f),
                                            Color.White.copy(alpha = 0.78f)
                                        )
                                    )
                                )
                                .drawWithCache {
                                    val outline = pillShape.createOutline(size, ld, this)
                                    val cx = size.width / 2f + 18f * cos(phase).toFloat()
                                    val cy = size.height / 2f + 6f * sin(phase).toFloat()

                                    // 顶部白色高光带（玻璃反光）
                                    val topGlossH = size.height * 0.32f
                                    val topGloss = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = if (dark) 0.22f else 0.55f),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = topGlossH
                                    )
                                    // 内圈柔光
                                    val innerGlow = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.30f),
                                            Color.Transparent
                                        ),
                                        center = Offset(size.width * 0.5f, size.height * 0.32f),
                                        radius = size.width * 0.55f
                                    )

                                    // 紫金双色偏移描边（替代彩色色差）：紫色和金色错位叠加
                                    val a = rimAlpha
                                    val purple = Color(0xFF9B6BFF).copy(alpha = 0.50f * a)
                                    val gold = Color(0xFFFFC76A).copy(alpha = 0.50f * a)
                                    val strokeW = (1.0f + 1.6f * a).dp.toPx()
                                    val ofs = (1.4f * a).dp.toPx()

                                    onDrawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = topGloss,
                                            size = Size(size.width, topGlossH)
                                        )
                                        drawRect(brush = innerGlow)

                                        if (a > 0.01f) {
                                            // 主紫金 sweep（流动渐变描边）
                                            val sweep = Brush.sweepGradient(
                                                prismColors.map { it.copy(alpha = 0.90f * a) },
                                                center = Offset(cx, cy)
                                            )
                                            drawOutline(
                                                outline = outline,
                                                brush = sweep,
                                                style = Stroke(width = strokeW + 0.8.dp.toPx())
                                            )
                                            // 紫金双色偏移：左紫右金
                                            translate(left = -ofs) {
                                                drawOutline(
                                                    outline = outline,
                                                    color = purple,
                                                    style = Stroke(width = strokeW)
                                                )
                                            }
                                            translate(left = ofs) {
                                                drawOutline(
                                                    outline = outline,
                                                    color = gold,
                                                    style = Stroke(width = strokeW)
                                                )
                                            }
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            NavLabel(tab = tabs[anchorIdx], selected = true)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavLabel(tab: Tab, selected: Boolean) {
    val unselectedColor = if (isSystemInDarkTheme()) Color(0xFFD8E2DC) else Color(0xFF1F2A24)
    val color = if (selected) MaterialTheme.colorScheme.primary else unselectedColor
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            tab.icon,
            contentDescription = tab.title,
            tint = color,
            modifier = Modifier.size(22.dp)
        )
        Text(
            tab.title,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun NeonFab(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .navigationBarsPadding()
            .size(60.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(AppPalette.PrimaryLight, AppPalette.PrimaryDeep)
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Add,
            contentDescription = "添加规则",
            tint = Color.White
        )
    }
}
