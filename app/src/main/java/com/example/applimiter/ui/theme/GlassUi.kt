package com.example.applimiter.ui.theme

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 液态玻璃背景：四个绿色光斑沿正余弦曲线游走，叠加 blur 形成水滴流动。
 * Android 12+ 使用 [Modifier.blur] 真模糊。
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val palette = if (dark) AppPalette.BgGradDark else AppPalette.BgGradLight

    val transition = rememberInfiniteTransition(label = "liquid-bg")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 22_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // 如果用户在 res/drawable 里放了 background.png，就用它作为底图；否则走纯色渐变
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bgResId = remember {
        ctx.resources.getIdentifier("background", "drawable", ctx.packageName)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (bgResId != 0) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(id = bgResId),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                alpha = if (dark) 0.85f else 1.0f
            )
            // 底图上叠一层很薄的渐变薄纱，仅用于柔化文字对比，不再压暗背景
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (dark) {
                                listOf(Color(0x4D06120C), Color(0x33000000))
                            } else {
                                listOf(Color(0x1AFFFFFF), Color(0x0DFFFFFF))
                            }
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (dark) {
                                listOf(Color(0xFF0A1020), Color(0xFF101A2E))
                            } else {
                                listOf(AppPalette.SilverBlue, AppPalette.FogWhite)
                            }
                        )
                    )
            )
        }

        val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val blobLayer = Modifier
            .fillMaxSize()
            .let { if (canBlur) it.blur(64.dp) else it }

        Canvas(modifier = blobLayer) {
            val w = size.width
            val h = size.height
            data class Blob(val color: Color, val cx: Float, val cy: Float, val r: Float)

            // 有自定义背景图时光斑显著减弱，避免遮住背景；纯色背景时保持原浓度
            val a = if (bgResId != 0) {
                if (dark) 0.20f else 0.30f
            } else {
                if (dark) 0.55f else 0.85f
            }
            val blobs = listOf(
                Blob(palette[0].copy(alpha = a),
                    w * (0.28f + 0.22f * cos(phase)),
                    h * (0.18f + 0.12f * sin(phase * 1.1f)), w * 0.70f),
                Blob(palette[1].copy(alpha = a * 0.95f),
                    w * (0.78f + 0.16f * cos(phase + 1.7f)),
                    h * (0.42f + 0.18f * sin(phase * 0.9f + 0.6f)), w * 0.78f),
                Blob(palette[2].copy(alpha = a * 0.9f),
                    w * (0.18f + 0.20f * cos(phase * 0.7f + 3.2f)),
                    h * (0.78f + 0.14f * sin(phase + 2.0f)), w * 0.85f),
                Blob(palette.getOrElse(3) { palette[0] }.copy(alpha = a * 0.85f),
                    w * (0.62f + 0.18f * cos(phase * 1.3f + 4.5f)),
                    h * (0.85f + 0.10f * sin(phase * 0.8f + 1.2f)), w * 0.65f)
            )

            blobs.forEach { b ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(b.color, Color.Transparent),
                        center = Offset(b.cx, b.cy),
                        radius = b.r
                    ),
                    radius = b.r,
                    center = Offset(b.cx, b.cy)
                )
            }
        }

        content()
    }
}

/**
 * 液态玻璃卡片：
 * - 永远存在的彩虹边（chromatic aberration），按压时增强
 * - 按压触发 spring 缩放回弹（0.97×）
 * - 按压时彩虹外溢成 halo，仿真玻璃折射
 * - 彩虹中心动态跟随按下位置，实现真实折射方向
 * - 顶部白色高光带
 *
 * @param onClick 传入即开启交互动画。不传则保持静态玻璃。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    elevation: Dp = 8.dp,
    haloPadding: Dp = 4.dp,
    /** 覆盖默认填充透明度。null 用主题默认；范围 0f~1f。 */
    fillAlpha: Float? = null,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val ld = LocalLayoutDirection.current

    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var pressPos by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect { ev ->
            when (ev) {
                is PressInteraction.Press -> {
                    pressed = true
                    pressPos = ev.pressPosition
                }
                is PressInteraction.Release,
                is PressInteraction.Cancel -> {
                    pressed = false
                }
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "lg-scale"
    )
    // 默认 0（纯磨砂拟态），按下时才出现彩虹折射
    val rim by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 0f,
        animationSpec = tween(durationMillis = 240),
        label = "lg-rim"
    )
    val halo by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "lg-halo"
    )

    // 彩虹色环（红橙黄绿青紫粉，循环闭合）
    val rainbow = remember {
        listOf(
            Color(0xFFFF7A8C), Color(0xFFFFB37A), Color(0xFFF7E68C),
            Color(0xFF93EBB7), Color(0xFF7CD0FF), Color(0xFFA68CFF),
            Color(0xFFFF8AC9), Color(0xFFFF7A8C)
        )
    }

    val baseFillRaw = if (dark) Color(0xB31A2A22) else Color(0xD9FFFFFF)
    val baseFill = if (fillAlpha != null) {
        baseFillRaw.copy(alpha = fillAlpha.coerceIn(0f, 1f))
    } else baseFillRaw
    val topHL = if (dark) Color(0x33FFFFFF) else Color(0xCCFFFFFF)
    val edgeColor = if (dark) Color(0x66FFFFFF) else Color(0xAAFFFFFF)

    val clickMod = if (onClick != null && enabled) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    ) {
        // —— 第一层：halo（彩虹折射晕，按压时溢出 card 边界）——
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithCache {
                    val haloPx = haloPadding.toPx()
                    val cardW = size.width - 2 * haloPx
                    val cardH = size.height - 2 * haloPx
                    if (cardW <= 0f || cardH <= 0f) {
                        return@drawWithCache onDrawWithContent { drawContent() }
                    }
                    val cardSize = Size(cardW, cardH)
                    val cardOutline = shape.createOutline(cardSize, ld, this)
                    val raw = pressPos ?: Offset(cardW / 2f, cardH / 2f)
                    val sweepCenter = Offset(haloPx + raw.x, haloPx + raw.y)
                    val haloBrush = Brush.sweepGradient(
                        rainbow.map { it.copy(alpha = 0.6f * halo) },
                        center = sweepCenter
                    )
                    val haloStroke = haloPx * 2f + 5.dp.toPx()
                    onDrawWithContent {
                        if (halo > 0.01f) {
                            translate(haloPx, haloPx) {
                                drawOutline(
                                    outline = cardOutline,
                                    brush = haloBrush,
                                    style = Stroke(width = haloStroke)
                                )
                            }
                        }
                    }
                }
        )

        // —— 第二层：玻璃卡（halo 内部，inset by haloPadding）——
        Box(
            modifier = Modifier
                .padding(haloPadding)
                .shadow(
                    elevation = elevation,
                    shape = shape,
                    clip = false,
                    ambientColor = AppPalette.PrimaryLight.copy(alpha = 0.18f),
                    spotColor = AppPalette.PrimaryLight.copy(alpha = 0.10f)
                )
                .clip(shape)
                // 平面化：纯色填充，不再用 verticalGradient
                .background(baseFill)
                .then(clickMod)
                .drawWithCache {
                    val outline = shape.createOutline(size, ld, this)
                    val ctr = pressPos ?: Offset(size.width / 2f, size.height / 2f)
                    val rimSweep = Brush.sweepGradient(
                        rainbow.map { it.copy(alpha = 0.85f * rim) },
                        center = ctr
                    )
                    val rimWidth = (2.6f * rim).dp.toPx()
                    onDrawWithContent {
                        drawContent()
                        // 仅按下时绘制彩虹折射边（默认 rim=0 不画任何东西）
                        if (rim > 0.01f) {
                            drawOutline(
                                outline = outline,
                                brush = rimSweep,
                                style = Stroke(width = rimWidth.coerceAtLeast(1f))
                            )
                        }
                        // 平面化的细描边（保留以分隔卡片与背景）
                        drawOutline(
                            outline = outline,
                            color = edgeColor,
                            style = Stroke(width = 0.8.dp.toPx())
                        )
                    }
                }
        ) {
            content()
        }
    }
}


/**
 * 渐变实心按钮：按下时缩放 + 彩虹折射边。
 */
@Composable
fun GlassPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null
) {
    val ld = LocalLayoutDirection.current
    val shape = RoundedCornerShape(16.dp)
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var pressPos by remember { mutableStateOf<Offset?>(null) }

    LaunchedEffect(interaction) {
        interaction.interactions.collect { ev ->
            when (ev) {
                is PressInteraction.Press -> { pressed = true; pressPos = ev.pressPosition }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "btn-scale"
    )
    val rim by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = tween(220),
        label = "btn-rim"
    )

    val rainbow = remember {
        listOf(
            Color(0xFFFF7A8C), Color(0xFFFFB37A), Color(0xFFF7E68C),
            Color(0xFF93EBB7), Color(0xFF7CD0FF), Color(0xFFA68CFF),
            Color(0xFFFF8AC9), Color(0xFFFF7A8C)
        )
    }

    val gradient = if (enabled) {
        Brush.linearGradient(listOf(AppPalette.PrimaryLight, AppPalette.PrimaryDeep))
    } else {
        Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.35f), Color.Gray.copy(alpha = 0.25f)))
    }

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(gradient)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .drawWithCache {
                val outline = shape.createOutline(size, ld, this)
                val ctr = pressPos ?: Offset(size.width / 2f, size.height / 2f)
                val rimSweep = Brush.sweepGradient(
                    rainbow.map { it.copy(alpha = 0.9f * rim) },
                    center = ctr
                )
                onDrawWithContent {
                    drawContent()
                    drawOutline(outline, brush = rimSweep, style = Stroke(width = (1.2f + 2.5f * rim).dp.toPx()))
                }
            }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        androidx.compose.foundation.layout.Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                androidx.compose.foundation.layout.Spacer(Modifier.padding(end = 8.dp))
            }
            androidx.compose.material3.Text(
                text,
                color = Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
        }
    }
}
