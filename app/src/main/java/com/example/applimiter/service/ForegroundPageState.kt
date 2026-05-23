package com.example.applimiter.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 当前前台页面状态。由 [PageLimitService] 写入，由悬浮窗 / UI 订阅。
 *
 * 仅在无障碍服务运行时有效。系统 UI / 输入法等噪声页面会被过滤掉。
 */
data class ForegroundPage(
    val packageName: String,
    val activityName: String
)

object ForegroundPageState {
    private val _current = MutableStateFlow<ForegroundPage?>(null)
    val current: StateFlow<ForegroundPage?> = _current

    fun update(page: ForegroundPage?) {
        _current.value = page
    }
}
