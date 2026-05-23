package com.example.applimiter.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 计时器全局状态，让 UI 可以观察"当前正在计时哪条规则、剩余多少秒"。
 */
data class ActiveTimer(
    val ruleId: Long,
    val pageLabel: String,
    val appName: String,
    val totalSeconds: Int,
    val remainingSeconds: Int
)

object ActiveTimerState {
    private val _current = MutableStateFlow<ActiveTimer?>(null)
    val current: StateFlow<ActiveTimer?> = _current

    fun update(timer: ActiveTimer?) {
        _current.value = timer
    }

    fun clear() {
        _current.value = null
    }
}
