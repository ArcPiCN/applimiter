package com.example.applimiter.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.applimiter.ui.theme.AppPalette
import com.example.applimiter.ui.theme.GlassSurface
import com.example.applimiter.ui.theme.LiquidGlassBackground
import com.example.applimiter.ui.viewmodel.MainViewModel
import com.example.applimiter.util.AppInfoProvider

/** 跨屏传递选中的 App 包名（保留 pickedApp 兼容老入口，但当前只用 lockedPackageName）。 */
internal object AddRuleNavBus {
    @Volatile
    var pickedApp: Pair<String, String>? = null

    /** 从应用规则页进入"添加规则"时锁定 packageName */
    @Volatile
    var lockedPackageName: String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRuleScreen(
    viewModel: MainViewModel,
    onPickApp: () -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    editingRuleId: Long? = null
) {
    val isEditing = editingRuleId != null
    val ctx = LocalContext.current

    var packageName by remember { mutableStateOf("") }
    var appName by remember { mutableStateOf("") }
    var activityName by remember { mutableStateOf("") }
    var pageLabel by remember { mutableStateOf("") }
    var seconds by remember { mutableStateOf(0) }
    var customSeconds by remember { mutableStateOf("") }
    var ruleEnabled by remember { mutableStateOf(true) }
    var loaded by remember { mutableStateOf(!isEditing) }

    LaunchedEffect(editingRuleId) {
        if (editingRuleId != null) {
            val rule = viewModel.getRule(editingRuleId)
            if (rule != null) {
                packageName = rule.packageName
                appName = rule.appName
                activityName = rule.activityName
                pageLabel = rule.pageLabel
                seconds = rule.maxStaySeconds
                ruleEnabled = rule.isEnabled
                if (rule.maxStaySeconds !in listOf(0, 5, 15, 30, 60)) {
                    customSeconds = rule.maxStaySeconds.toString()
                }
            }
            loaded = true
        }
    }

    LaunchedEffect(Unit) {
        // 从 AppRulesScreen 进入时锁定 app
        AddRuleNavBus.lockedPackageName?.let { pkg ->
            if (packageName.isBlank()) {
                packageName = pkg
                appName = AppInfoProvider.getAppName(ctx, pkg)
            }
            AddRuleNavBus.lockedPackageName = null
        }
        // 从 AppListScreen 选完 app 返回（兼容路径）
        AddRuleNavBus.pickedApp?.let { (pkg, name) ->
            packageName = pkg
            appName = name
            activityName = ""
            pageLabel = ""
            AddRuleNavBus.pickedApp = null
        }
    }

    LiquidGlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                GlassPageHeader(
                    title = if (isEditing) "编辑规则" else "添加页面规则",
                    subtitle = if (appName.isNotBlank()) appName else packageName,
                    onBack = onCancel
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                item {
                    GlassTextField(
                        value = activityName,
                        onValueChange = { activityName = it },
                        label = "Activity 全名"
                    )
                }
                item {
                    GlassTextField(
                        value = pageLabel,
                        onValueChange = { pageLabel = it },
                        label = "页面别名（如 视频详情页）"
                    )
                }

                item {
                    Text(
                        "允许停留时长",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
                item {
                    val presets = listOf(0, 5, 15, 30, 60)
                    val labels = listOf("0秒", "5秒", "15秒", "30秒", "1分钟")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        presets.forEachIndexed { i, sec ->
                            DurationChip(
                                label = labels[i],
                                selected = seconds == sec && customSeconds.isBlank(),
                                onClick = {
                                    seconds = sec
                                    customSeconds = ""
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                item {
                    GlassTextField(
                        value = customSeconds,
                        onValueChange = { v ->
                            customSeconds = v.filter { it.isDigit() }
                            customSeconds.toIntOrNull()?.let { seconds = it }
                        },
                        label = "自定义秒数（可选）",
                        keyboardType = KeyboardType.Number
                    )
                }

                item {
                    val canSave = packageName.isNotBlank() &&
                        activityName.isNotBlank() &&
                        pageLabel.isNotBlank() &&
                        seconds >= 0 &&
                        loaded
                    com.example.applimiter.ui.theme.GlassPrimaryButton(
                        text = if (isEditing) "保存修改" else "保存规则",
                        enabled = canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp),
                        leadingContent = {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                        },
                        onClick = {
                            if (isEditing && editingRuleId != null) {
                                viewModel.updateRule(
                                    id = editingRuleId,
                                    packageName = packageName,
                                    appName = appName,
                                    activityName = activityName.trim(),
                                    pageLabel = pageLabel.trim(),
                                    maxStaySeconds = seconds,
                                    isEnabled = ruleEnabled
                                )
                            } else {
                                viewModel.addRule(
                                    packageName = packageName,
                                    appName = appName,
                                    activityName = activityName.trim(),
                                    pageLabel = pageLabel.trim(),
                                    maxStaySeconds = seconds
                                )
                            }
                            onDone()
                        }
                    )
                }

                item { Spacer(Modifier.height(40.dp)) }
            }
        }
    }
}

@Composable
private fun GlassPageHeader(title: String, subtitle: String, onBack: () -> Unit) {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DurationChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(shape)
            .background(
                brush = if (selected) {
                    Brush.linearGradient(
                        colors = listOf(AppPalette.PrimaryLight, AppPalette.PrimaryDeep)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.35f)
                        )
                    )
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun GlassTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 独立显示在外面的标签：避免 OutlinedTextField label 浮起时切开圆角
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White.copy(alpha = 0.7f),
                unfocusedContainerColor = Color.White.copy(alpha = 0.5f),
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.White.copy(alpha = 0.7f)
            )
        )
    }
}
