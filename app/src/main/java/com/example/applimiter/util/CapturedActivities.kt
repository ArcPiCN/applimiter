package com.example.applimiter.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 运行时由 [com.example.applimiter.service.PageLimitService] 捕获到的 Activity 名缓冲池。
 *
 * 用户在 AddRuleScreen 中选"自定义页面"时可以从这里挑选最近见过的 Activity，
 * 解决预置 Activity 名失效（App 升级改名）的问题。
 *
 * 仅保留每个包名最近 30 个不重复 Activity 名，按出现时间倒序。
 */
object CapturedActivities {

    private const val MAX_PER_PACKAGE = 30

    /** key: packageName, value: ordered list (最近的在前) */
    private val _captured = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val captured: StateFlow<Map<String, List<String>>> = _captured

    @Synchronized
    fun record(packageName: String, activityName: String) {
        if (packageName.isBlank() || activityName.isBlank()) return
        val current = _captured.value.toMutableMap()
        val list = current[packageName].orEmpty().toMutableList()
        list.remove(activityName)
        list.add(0, activityName)
        if (list.size > MAX_PER_PACKAGE) {
            list.subList(MAX_PER_PACKAGE, list.size).clear()
        }
        current[packageName] = list
        _captured.value = current
    }

    fun forPackage(packageName: String): List<String> {
        return _captured.value[packageName].orEmpty()
    }
}
