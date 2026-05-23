package com.example.applimiter.service

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.applimiter.AppLimiterApplication
import com.example.applimiter.MainActivity
import com.example.applimiter.data.db.RuleEntity
import com.example.applimiter.util.CapturedActivities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 监听前台 Activity，按规则倒计时强制返回桌面。
 *
 * 设计要点（性能 / 正确性）：
 * - 启用规则使用 [ruleIndex]（HashMap）做 O(1) 查找，避免每次事件遍历列表
 * - 不再依赖 currentPackage/currentActivity 的"是否变化"判断处理与否，统一用"当前页面是否命中规则"驱动状态机，
 *   修复"用户被强制退出后快速重新进入同一页面，由于上次状态未刷新而不再触发"的问题
 * - Toast 节流到关键节点（30/10/5/3/2/1 秒），避免每 5 秒打扰
 * - 倒计时结束后立刻重置 currentXxx，下次任何事件都强制重新评估
 */
class PageLimitService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 监听到的当前前台包名 / Activity（仅用于发布到 ForegroundPageState 的 dedupe） */
    @Volatile private var currentPackage: String? = null
    @Volatile private var currentActivity: String? = null

    /** 当前规则的倒计时 Job 与 toast Job */
    private var countdownJob: Job? = null
    private var toastJob: Job? = null

    /** 当前生效的规则；切页就清空 */
    @Volatile private var activeRule: RuleEntity? = null

    /** 启用规则索引：(pkg, activity) -> rule，O(1) 命中。 */
    @Volatile private var ruleIndex: Map<Pair<String, String>, RuleEntity> = emptyMap()

    /** 监听 DB 启用规则变化的 Job */
    private var dbWatcherJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService connected")
        ensureNotificationChannel()
        watchEnabledRules()
        running = true
    }

    private fun watchEnabledRules() {
        dbWatcherJob?.cancel()
        val repo = AppLimiterApplication.instance.repository
        dbWatcherJob = scope.launch(Dispatchers.IO) {
            repo.enabledRules.collect { rules ->
                // 索引包含所有启用规则（含 0 秒，0 秒表示进入即返回）
                ruleIndex = rules
                    .associateBy { it.packageName to it.activityName }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        val activity = event.className?.toString().orEmpty()

        // 忽略系统 UI / 输入法等噪声
        if (pkg == "android" || pkg == "com.android.systemui") return
        // 忽略自己应用本身
        if (pkg == packageName) return
        // className 有时是 Dialog/Toast，不是 Activity；过滤非全限定名
        if (activity.isBlank() || !activity.contains('.')) return

        // 状态发布（用于悬浮窗 / UI），仅在变化时发，避免 Compose 无谓重组
        if (pkg != currentPackage || activity != currentActivity) {
            currentPackage = pkg
            currentActivity = activity
            CapturedActivities.record(pkg, activity)
            ForegroundPageState.update(ForegroundPage(packageName = pkg, activityName = activity))
        }

        // 状态机：根据"当前页面是否命中规则"驱动倒计时
        val matched = ruleIndex[pkg to activity]
        val active = activeRule
        when {
            // 命中的就是正在计时的规则 → 保持
            matched != null && active != null && matched.id == active.id -> Unit

            // 命中规则 → 取消旧计时，启动新计时
            matched != null -> {
                cancelTimers()
                startCountdown(matched)
            }

            // 不命中 → 取消计时（如果有）
            active != null -> cancelTimers()
        }
    }

    private fun startCountdown(rule: RuleEntity) {
        activeRule = rule
        val total = rule.maxStaySeconds

        // 提示节点（按剩余秒数取并集）。短规则只提示开头与最后 3 秒。
        val toastPoints = buildSet {
            if (total >= 30) add(30)
            if (total >= 10) add(10)
            add(5); add(3); add(2); add(1)
        }

        countdownJob = scope.launch {
            try {
                var remaining = total
                publishTimer(rule, total, remaining)

                if (total == 0) {
                    // 不允许停留：直接返回桌面
                    Log.i(TAG, "0s rule: ${rule.pageLabel}, immediate HOME")
                    showToast("「${rule.pageLabel}」不允许停留，已返回桌面")
                } else {
                    showToast("「${rule.pageLabel}」剩余 ${formatTime(total)}")
                }

                var notifiedFinal = false
                while (remaining > 0) {
                    delay(1000L)
                    remaining -= 1
                    publishTimer(rule, total, remaining)

                    if (remaining in toastPoints) {
                        showToast("「${rule.pageLabel}」剩余 ${formatTime(remaining)}")
                    }
                    if (!notifiedFinal && remaining in 1..10) {
                        notifiedFinal = true
                        postFinalCountdownNotification(rule, remaining)
                    }
                }

                // 时间到 → 强制返回桌面（HOME 比 BACK 更可靠，不会被应用拦截）
                Log.i(TAG, "Time up: ${rule.pageLabel}")
                if (total > 0) {
                    showToast("「${rule.pageLabel}」时间到，已返回桌面")
                }
                performGlobalAction(GLOBAL_ACTION_HOME)
                delay(300L)
                performGlobalAction(GLOBAL_ACTION_HOME)
            } finally {
                ActiveTimerState.clear()
                activeRule = null
                // 关键：重置当前页面，使得下次"快速重新进入同一页面"能再次触发
                currentPackage = null
                currentActivity = null
            }
        }
    }

    private fun publishTimer(rule: RuleEntity, total: Int, remaining: Int) {
        ActiveTimerState.update(
            ActiveTimer(
                ruleId = rule.id,
                pageLabel = rule.pageLabel,
                appName = rule.appName,
                totalSeconds = total,
                remainingSeconds = remaining
            )
        )
    }

    private fun cancelTimers() {
        countdownJob?.cancel()
        toastJob?.cancel()
        countdownJob = null
        toastJob = null
        activeRule = null
        ActiveTimerState.clear()
    }

    private fun showToast(text: String) {
        // Toast 必须在主线程
        mainHandler.post {
            Toast.makeText(applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }

    private fun postFinalCountdownNotification(rule: RuleEntity, remaining: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pi = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("时间快到了")
            .setContentText("「${rule.pageLabel}」将在 $remaining 秒后自动退出")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify(NOTIF_ID_COUNTDOWN, notif)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "倒计时提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "页面停留时间即将结束时提醒"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun formatTime(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "%d:%02d".format(m, s) else "${s}秒"
    }

    override fun onInterrupt() {
        cancelTimers()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        cancelTimers()
        running = false
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelTimers()
        dbWatcherJob?.cancel()
        scope.cancel()
        running = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PageLimitService"
        private const val CHANNEL_ID = "page_limit_countdown"
        private const val NOTIF_ID_COUNTDOWN = 1001

        @Volatile
        private var running: Boolean = false

        /** 服务是否已经被系统启动并连接（仅作辅助提示，不能完全替代系统设置里的开关）。 */
        fun isRunning(): Boolean = running

        /** 检查无障碍服务是否在系统设置中被启用。 */
        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${PageLimitService::class.java.name}"
            val enabledServices = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
