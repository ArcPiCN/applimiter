package com.example.applimiter.ui.screen.tab

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.applimiter.service.OverlayService
import com.example.applimiter.ui.screen.PermissionsSnapshot
import com.example.applimiter.ui.theme.AppPalette
import com.example.applimiter.ui.theme.GlassPrimaryButton
import com.example.applimiter.ui.theme.GlassSurface
import com.example.applimiter.ui.viewmodel.MainViewModel

@Composable
fun HomeTab(
    viewModel: MainViewModel,
    perm: PermissionsSnapshot,
    contentPadding: PaddingValues
) {
    val ctx = LocalContext.current
    val activeTimer by viewModel.activeTimer.collectAsState()

    val accessibilityReady = perm.accessibilityEnabled
    val overlayReady = perm.overlayPermitted

    // Switch 本地状态：立刻响应用户操作，避免等 Lifecycle.Resume 才刷新造成"卡住不动"
    var overlayRunningLocal by remember(perm.overlayRunning) {
        mutableStateOf(perm.overlayRunning)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            com.example.applimiter.ui.screen.ScrollableTitle(
                title = "戒刷止刷",
                subtitle = "保持专注 · 守住时间"
            )
        }

        item {
            HeroCard(allReady = accessibilityReady && overlayReady)
        }

        if (activeTimer != null) {
            item { ActiveTimerCard(activeTimer!!) }
        }

        item {
            PermissionRow(
                title = "无障碍服务",
                subtitle = if (accessibilityReady) "已启用" else "用于检测前台页面并按规则计时",
                granted = accessibilityReady,
                buttonText = if (accessibilityReady) "已开启" else "去开启",
                onAction = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
        }

        item {
            PermissionRow(
                title = "悬浮窗",
                subtitle = when {
                    !overlayReady -> "用于在任意 App 显示一键添加规则的浮窗"
                    overlayRunningLocal -> "已授权 · 浮窗运行中"
                    else -> "已授权 · 浮窗未启动"
                },
                granted = overlayReady,
                buttonText = if (overlayReady) "已授权" else "去授权",
                onAction = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${ctx.packageName}")
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    ctx.startActivity(intent)
                },
                trailingExtra = {
                    if (overlayReady) {
                        Switch(
                            checked = overlayRunningLocal,
                            onCheckedChange = { wantOn ->
                                overlayRunningLocal = wantOn   // 立刻 UI 响应
                                if (wantOn) OverlayService.start(ctx)
                                else OverlayService.stop(ctx)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color(0xFFB0B0B0),
                                uncheckedBorderColor = Color(0xFFB0B0B0)
                            )
                        )
                    }
                }
            )
        }

        item { TipCard() }
    }
}

@Composable
private fun HeroCard(allReady: Boolean) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(AppPalette.PrimaryLight, AppPalette.PrimaryDeep)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (allReady) Icons.Default.Check else Icons.Default.Bolt,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    if (allReady) "一切就绪" else "准备就绪后开始",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (allReady) "可以前往「规则」页设置目标页面"
                    else "请下方授权所需权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonText: String,
    onAction: () -> Unit,
    trailingExtra: @Composable () -> Unit = {}
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (granted) Brush.linearGradient(
                                listOf(AppPalette.PrimaryLight, AppPalette.PrimaryDeep)
                            )
                            else Brush.linearGradient(
                                listOf(Color(0x33FFFFFF), Color(0x11FFFFFF))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (granted) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = if (granted) Color.White else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                trailingExtra()
            }
            Spacer(Modifier.height(12.dp))
            GlassPrimaryButton(
                text = buttonText,
                onClick = onAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                enabled = !granted || buttonText != "已开启" && buttonText != "已授权"
            )
        }
    }
}

@Composable
private fun ActiveTimerCard(timer: com.example.applimiter.service.ActiveTimer) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(AppPalette.PrimaryLight, AppPalette.PrimaryDeep)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = Color.White)
                }
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "正在计时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "${timer.appName} · ${timer.pageLabel}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "${timer.remainingSeconds}s",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            val progress = if (timer.totalSeconds > 0) {
                timer.remainingSeconds.toFloat() / timer.totalSeconds
            } else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )
        }
    }
}

@Composable
private fun TipCard() {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "使用建议",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "1. 国产机请允许应用后台运行，避免无障碍/悬浮窗被杀\n" +
                    "2. 在任意 App 内打开浮窗后点击 1 分等按钮即可一键限制当前页面\n" +
                    "3. 时间到自动返回桌面",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
