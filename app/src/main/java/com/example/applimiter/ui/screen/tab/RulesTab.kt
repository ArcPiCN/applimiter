package com.example.applimiter.ui.screen.tab

import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ToggleOff
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.applimiter.ui.theme.GlassPrimaryButton
import com.example.applimiter.ui.theme.GlassSurface
import com.example.applimiter.ui.viewmodel.MainViewModel
import com.example.applimiter.util.AppInfoProvider
import kotlinx.coroutines.launch

private data class AppGroup(
    val packageName: String,
    val appName: String,
    val ruleCount: Int,
    val enabledCount: Int,
    val icon: Drawable?
)

@Composable
fun RulesTab(
    viewModel: MainViewModel,
    onAddRuleClick: () -> Unit,
    onEditRuleClick: (Long) -> Unit,
    contentPadding: PaddingValues
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val rules by viewModel.allRules.collectAsState()

    var showImportDialog by remember { mutableStateOf(false) }
    var pendingDeleteApp by remember { mutableStateOf<AppGroup?>(null) }

    // 多选模式
    var selectionMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<String>() }
    var pendingBatchDelete by remember { mutableStateOf(false) }

    // 按 packageName 分组
    val groups by remember(rules) {
        derivedStateOf {
            rules.groupBy { it.packageName }.map { (pkg, list) ->
                AppGroup(
                    packageName = pkg,
                    appName = list.firstOrNull { it.appName.isNotBlank() }?.appName ?: pkg,
                    ruleCount = list.size,
                    enabledCount = list.count { it.isEnabled },
                    icon = AppInfoProvider.getAppIcon(ctx, pkg)
                )
            }.sortedBy { it.appName }
        }
    }

    // 当所有规则被清空时自动退出多选
    if (groups.isEmpty() && selectionMode) {
        selectionMode = false
        selected.clear()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                runCatching {
                    val json = viewModel.exportToJson()
                    ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }.fold(
                    onSuccess = { Toast.makeText(ctx, "已导出 ${rules.size} 条规则", Toast.LENGTH_SHORT).show() },
                    onFailure = { Toast.makeText(ctx, "导出失败：${it.message}", Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val r = viewModel.importFromUri(ctx, uri)
                Toast.makeText(ctx, importMessage(r), Toast.LENGTH_LONG).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = contentPadding.calculateBottomPadding() + 88.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            com.example.applimiter.ui.screen.ScrollableTitle(
                title = "规则",
                subtitle = "管理你设置的所有限制"
            )
        }

        item {
            if (selectionMode) {
                BatchToolbar(
                    selectedCount = selected.size,
                    totalCount = groups.size,
                    onSelectAll = {
                        if (selected.size == groups.size) {
                            selected.clear()
                        } else {
                            selected.clear()
                            selected.addAll(groups.map { it.packageName })
                        }
                    },
                    onEnable = {
                        viewModel.setEnabledByPackages(selected.toList(), true)
                        selectionMode = false
                        selected.clear()
                        Toast.makeText(ctx, "已启用所选规则", Toast.LENGTH_SHORT).show()
                    },
                    onDisable = {
                        viewModel.setEnabledByPackages(selected.toList(), false)
                        selectionMode = false
                        selected.clear()
                        Toast.makeText(ctx, "已禁用所选规则", Toast.LENGTH_SHORT).show()
                    },
                    onDelete = {
                        if (selected.isNotEmpty()) pendingBatchDelete = true
                    },
                    onCancel = {
                        selectionMode = false
                        selected.clear()
                    }
                )
            } else {
                ToolBarCard(
                    appCount = groups.size,
                    ruleCount = rules.size,
                    onExport = {
                        if (rules.isEmpty()) {
                            Toast.makeText(ctx, "没有可导出的规则", Toast.LENGTH_SHORT).show()
                        } else {
                            exportLauncher.launch("applimiter-rules.json")
                        }
                    },
                    onImport = { showImportDialog = true },
                    onAddApp = onAddRuleClick
                )
            }
        }

        if (groups.isEmpty()) {
            item { EmptyRules() }
        } else {
            items(groups, key = { it.packageName }) { group ->
                AppGroupCard(
                    group = group,
                    selectionMode = selectionMode,
                    selected = group.packageName in selected,
                    onClick = {
                        if (selectionMode) {
                            if (group.packageName in selected) {
                                selected.remove(group.packageName)
                                if (selected.isEmpty()) selectionMode = false
                            } else {
                                selected.add(group.packageName)
                            }
                        } else {
                            AppRulesNavBus.navigateTo = group.packageName
                        }
                    },
                    onLongPress = {
                        if (!selectionMode) {
                            selectionMode = true
                            if (group.packageName !in selected) selected.add(group.packageName)
                        }
                    },
                    onDelete = { pendingDeleteApp = group }
                )
            }
        }
    }

    // 单 app 删除确认
    pendingDeleteApp?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDeleteApp = null },
            title = { Text("删除应用规则") },
            text = { Text("将删除「${target.appName}」下的全部 ${target.ruleCount} 条规则。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteByPackages(listOf(target.packageName))
                    pendingDeleteApp = null
                }) { Text("全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteApp = null }) { Text("取消") }
            }
        )
    }

    // 批量删除确认
    if (pendingBatchDelete) {
        val targets = selected.toList()
        AlertDialog(
            onDismissRequest = { pendingBatchDelete = false },
            title = { Text("批量删除") },
            text = { Text("将删除所选 ${targets.size} 个应用的全部规则，此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteByPackages(targets)
                    pendingBatchDelete = false
                    selectionMode = false
                    selected.clear()
                }) { Text("全部删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingBatchDelete = false }) { Text("取消") }
            }
        )
    }

    if (showImportDialog) {
        ImportDialog(
            onDismiss = { showImportDialog = false },
            onPickFile = {
                showImportDialog = false
                importFileLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
            },
            onSubmitUrl = { url ->
                showImportDialog = false
                scope.launch {
                    val r = viewModel.importFromUrl(url)
                    Toast.makeText(ctx, importMessage(r), Toast.LENGTH_LONG).show()
                }
            },
            onLoadBuiltIn = {
                showImportDialog = false
                scope.launch {
                    val r = viewModel.importBuiltInRules(ctx)
                    Toast.makeText(ctx, importMessage(r), Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}

/** 跨屏导航总线：RulesTab 设置目标 packageName，MainScaffold 监听并 navigate */
internal object AppRulesNavBus {
    @Volatile
    var navigateTo: String? = null
}

private fun importMessage(r: MainViewModel.ImportResult): String = when (r) {
    is MainViewModel.ImportResult.Success ->
        "已导入 ${r.imported} 条" + if (r.skipped > 0) "（跳过 ${r.skipped} 条无效）" else ""
    is MainViewModel.ImportResult.Failure -> "导入失败：${r.message}"
}

@Composable
private fun ToolBarCard(
    appCount: Int,
    ruleCount: Int,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAddApp: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "我的规则",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (appCount == 0) "暂无规则"
                    else "$appCount 个应用 · 共 $ruleCount 条页面规则 · 长按可多选",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onAddApp) {
                Icon(Icons.Default.Add, contentDescription = "添加应用", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onImport) {
                Icon(Icons.Default.Download, contentDescription = "导入", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Upload, contentDescription = "导出", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun BatchToolbar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onEnable: () -> Unit,
    onDisable: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit
) {
    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "退出多选",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    "已选 $selectedCount / $totalCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BatchAction(
                    icon = Icons.Default.SelectAll,
                    label = if (selectedCount == totalCount) "取消全选" else "全选",
                    onClick = onSelectAll
                )
                BatchAction(
                    icon = Icons.Default.ToggleOn,
                    label = "启用",
                    enabled = selectedCount > 0,
                    onClick = onEnable
                )
                BatchAction(
                    icon = Icons.Default.ToggleOff,
                    label = "禁用",
                    enabled = selectedCount > 0,
                    onClick = onDisable
                )
                BatchAction(
                    icon = Icons.Default.Delete,
                    label = "删除",
                    enabled = selectedCount > 0,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onDelete
                )
            }
        }
    }
}

@Composable
private fun BatchAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = onClick, enabled = enabled) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun AppGroupCard(
    group: AppGroup,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    onDelete: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent

    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(group.packageName, selectionMode) {
                detectTapGestures(
                    onLongPress = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onLongPress()
                    },
                    onTap = { onClick() }
                )
            }
            .border(
                width = if (selected) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 多选时左侧勾选圆
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
            }
            // 应用图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                if (group.icon != null) {
                    AsyncImage(
                        model = group.icon,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    group.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    group.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 规则数 badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "${group.enabledCount}/${group.ruleCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // 非多选态显示删除按钮
            if (!selectionMode) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除该应用全部规则",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRules() {
    GlassSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "还没有规则",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "点击右上角「下载」可加载内置推荐规则、导入文件或网络规则\n或点「+」从应用列表添加自己的规则",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ImportDialog(
    onDismiss: () -> Unit,
    onPickFile: () -> Unit,
    onSubmitUrl: (String) -> Unit,
    onLoadBuiltIn: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入规则") },
        text = {
            Column {
                Text(
                    "推荐规则",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "包含抖音、微信视频号等常见短视频页面",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                GlassPrimaryButton(
                    text = "加载内置推荐规则",
                    onClick = onLoadBuiltIn,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "从本地 JSON 文件导入",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                GlassPrimaryButton(
                    text = "选择本地文件",
                    onClick = onPickFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "从网络 URL 导入（http/https）",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    placeholder = { Text("https://example.com/rules.json") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.5f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.35f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (url.isNotBlank()) onSubmitUrl(url) },
                enabled = url.isNotBlank()
            ) { Text("从 URL 导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
