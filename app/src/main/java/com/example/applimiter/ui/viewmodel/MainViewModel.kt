package com.example.applimiter.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.applimiter.AppLimiterApplication
import com.example.applimiter.data.db.RuleEntity
import com.example.applimiter.data.io.RuleIo
import com.example.applimiter.service.ActiveTimer
import com.example.applimiter.service.ActiveTimerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = (application as AppLimiterApplication).repository

    val allRules: StateFlow<List<RuleEntity>> = repository.allRules
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeTimer: StateFlow<ActiveTimer?> = ActiveTimerState.current

    fun deleteRule(rule: RuleEntity) {
        viewModelScope.launch { repository.delete(rule) }
    }

    fun toggleRule(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    /** 批量删除：按包名删除所选应用下的所有规则 */
    fun deleteByPackages(packageNames: Collection<String>) {
        if (packageNames.isEmpty()) return
        val targets = packageNames.toSet()
        viewModelScope.launch {
            repository.allRulesNow()
                .filter { it.packageName in targets }
                .forEach { repository.delete(it) }
        }
    }

    /** 批量启用 / 禁用：所选应用下的所有规则 */
    fun setEnabledByPackages(packageNames: Collection<String>, enabled: Boolean) {
        if (packageNames.isEmpty()) return
        val targets = packageNames.toSet()
        viewModelScope.launch {
            repository.allRulesNow()
                .filter { it.packageName in targets }
                .forEach { repository.setEnabled(it.id, enabled) }
        }
    }

    suspend fun getRule(id: Long): RuleEntity? = repository.getById(id)

    /** 导出当前所有规则为 JSON 文本。 */
    suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        RuleIo.toJson(repository.allRulesNow())
    }

    /**
     * 导入：从 JSON 文本写入数据库。
     * 替换语义：JSON 中出现的每个 packageName 会先删除该 app 下的所有旧规则，再插入新规则；
     * 不在 JSON 中的应用规则保留不动。
     */
    suspend fun importFromJson(json: String): ImportResult = withContext(Dispatchers.IO) {
        when (val r = RuleIo.parseJson(json)) {
            is RuleIo.ParseResult.Failure -> ImportResult.Failure(r.message)
            is RuleIo.ParseResult.Success -> {
                if (r.rules.isEmpty()) {
                    ImportResult.Failure("文件里没有有效规则")
                } else {
                    // 先按包名删除旧规则，再插入新规则
                    val pkgs = r.rules.map { it.packageName }.toSet()
                    pkgs.forEach { repository.deleteByPackage(it) }
                    repository.insertAll(r.rules)
                    ImportResult.Success(r.rules.size, r.skipped)
                }
            }
        }
    }

    suspend fun importFromUri(context: android.content.Context, uri: Uri): ImportResult {
        return runCatching { RuleIo.readUri(context, uri) }
            .fold(
                onSuccess = { importFromJson(it) },
                onFailure = { ImportResult.Failure("读文件失败：${it.message}") }
            )
    }

    suspend fun importFromUrl(url: String): ImportResult {
        return runCatching { RuleIo.downloadFromUrl(url.trim()) }
            .fold(
                onSuccess = { importFromJson(it) },
                onFailure = { ImportResult.Failure("下载失败：${it.message}") }
            )
    }

    /** 导入内置默认规则（res/raw/default_rules.json） */
    suspend fun importBuiltInRules(context: android.content.Context): ImportResult =
        withContext(Dispatchers.IO) {
            runCatching {
                context.resources.openRawResource(
                    com.example.applimiter.R.raw.default_rules
                ).use { it.readBytes().toString(Charsets.UTF_8) }
            }.fold(
                onSuccess = { importFromJson(it) },
                onFailure = { ImportResult.Failure("读取内置规则失败：${it.message}") }
            )
        }

    sealed class ImportResult {
        data class Success(val imported: Int, val skipped: Int) : ImportResult()
        data class Failure(val message: String) : ImportResult()
    }

    fun updateRule(
        id: Long,
        packageName: String,
        appName: String,
        activityName: String,
        pageLabel: String,
        maxStaySeconds: Int,
        isEnabled: Boolean
    ) {
        viewModelScope.launch {
            val existing = repository.getById(id) ?: return@launch
            repository.update(
                existing.copy(
                    packageName = packageName,
                    appName = appName,
                    activityName = activityName,
                    pageLabel = pageLabel,
                    maxStaySeconds = maxStaySeconds,
                    isEnabled = isEnabled
                )
            )
        }
    }

    fun addRule(
        packageName: String,
        appName: String,
        activityName: String,
        pageLabel: String,
        maxStaySeconds: Int
    ) {
        viewModelScope.launch {
            repository.insert(
                RuleEntity(
                    packageName = packageName,
                    appName = appName,
                    activityName = activityName,
                    pageLabel = pageLabel,
                    maxStaySeconds = maxStaySeconds
                )
            )
        }
    }
}
