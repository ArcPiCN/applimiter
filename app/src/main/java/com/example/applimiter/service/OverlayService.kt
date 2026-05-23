package com.example.applimiter.service

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.applimiter.AppLimiterApplication
import com.example.applimiter.MainActivity
import com.example.applimiter.data.db.RuleEntity
import com.example.applimiter.util.AppInfoProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * 悬浮窗：实时显示当前前台页面（包名 + Activity），并提供"一键添加规则"按钮。
 *
 * 使用方式：
 * - [start] 启动服务（需先获得悬浮窗权限）
 * - [stop] 停止服务
 *
 * 依赖 [PageLimitService]（无障碍服务）发布的 [ForegroundPageState]。
 */
class OverlayService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var collectJob: Job? = null

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var pkgText: TextView? = null
    private var actText: TextView? = null
    private var labelInput: EditText? = null

    /** 当前展示的前台页面 */
    private var currentPage: ForegroundPage? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        ensureNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Android 14+ 必须显式声明 fgs 类型；这里使用 specialUse（manifest 已声明）
            startForeground(NOTIF_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, buildNotification())
        }
        addFloatingView()
        observeForegroundPage()
        running = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun observeForegroundPage() {
        collectJob?.cancel()
        collectJob = scope.launch {
            // StateFlow 已自带去重，无需 distinctUntilChanged
            ForegroundPageState.current.collect { page ->
                currentPage = page
                renderPage(page)
            }
        }
    }

    private fun renderPage(page: ForegroundPage?) {
        if (page == null) {
            pkgText?.text = "（暂无前台页面）"
            actText?.text = "请确认无障碍服务已开启"
            return
        }
        val appName = withRuntimeAppName(page.packageName)
        pkgText?.text = "$appName · ${page.packageName}"
        actText?.text = page.activityName
    }

    private fun withRuntimeAppName(packageName: String): String {
        return runCatching { AppInfoProvider.getAppName(this, packageName) }
            .getOrDefault(packageName)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addFloatingView() {
        val view = buildFloatingView()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(12)
            y = dp(120)
        }

        windowManager.addView(view, params)
        floatingView = view
        layoutParams = params

        attachDragHandler(view, params)
    }

    private fun buildFloatingView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(220, 30, 30, 30))
                setStroke(dp(1), Color.argb(120, 255, 255, 255))
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
            // 限制最大宽度，避免横屏过宽
            val maxW = dp(280)
            layoutParams = LinearLayout.LayoutParams(maxW, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "戒刷止刷 · 一键限制"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            )
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener { stopSelf() }
        }
        titleRow.addView(title)
        titleRow.addView(closeBtn)

        pkgText = TextView(this).apply {
            text = "（暂无前台页面）"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, dp(6), 0, 0)
        }
        actText = TextView(this).apply {
            text = ""
            setTextColor(Color.argb(200, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setPadding(0, dp(2), 0, dp(8))
        }

        // 页面别名输入框
        labelInput = EditText(this).apply {
            hint = "页面别名（可选，先点这里输入）"
            setHintTextColor(Color.argb(140, 255, 255, 255))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setSingleLine(true)
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(Color.argb(60, 255, 255, 255))
                setStroke(dp(1), Color.argb(80, 255, 255, 255))
            }
            setPadding(dp(10), dp(8), dp(10), dp(8))
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
            layoutParams = lp
            // 关键：悬浮窗默认 NOT_FOCUSABLE，EditText 无法弹键盘
            // 点击时动态去掉 NOT_FOCUSABLE，让 IME 能输入；blur 后恢复
            setOnClickListener {
                setOverlayFocusable(true)
                requestFocus()
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) setOverlayFocusable(false)
            }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        listOf(0 to "0秒", 5 to "5秒", 15 to "15秒", 30 to "30秒", 60 to "1分").forEach { (sec, label) ->
            val btn = Button(this).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                background = GradientDrawable().apply {
                    cornerRadius = dp(8).toFloat()
                    setColor(Color.argb(255, 91, 140, 255))
                }
                setPadding(dp(4), dp(4), dp(4), dp(4))
                minWidth = 0
                minimumWidth = 0
                val lp = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
                lp.setMargins(dp(2), 0, dp(2), 0)
                layoutParams = lp
                setOnClickListener { addRuleForCurrentPage(sec, label) }
            }
            btnRow.addView(btn)
        }

        container.addView(titleRow)
        container.addView(pkgText)
        container.addView(actText)
        container.addView(labelInput)
        container.addView(btnRow)
        return container
    }

    /**
     * 处理拖动 + 点击的区分：移动距离超过阈值才视为拖拽，点击事件保持原有响应。
     *
     * 为了让按钮点击仍然生效，我们不在外层 LinearLayout 拦截 touch，而是只让顶部的"标题栏"区域负责拖动。
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragHandler(view: View, params: WindowManager.LayoutParams) {
        val touchSlop = dp(6)
        view.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var dragging = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = e.rawX
                        initialTouchY = e.rawY
                        dragging = false
                        // 返回 true，确保后续 MOVE/UP 仍能收到。子 View（按钮）的点击在事件分发阶段优先处理，
                        // 这里 setOnTouchListener 是兜底监听，不影响子 view 的点击事件。
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - initialTouchX).toInt()
                        val dy = (e.rawY - initialTouchY).toInt()
                        if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                            dragging = true
                        }
                        if (dragging) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            runCatching { windowManager.updateViewLayout(view, params) }
                            return true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return dragging
                    }
                }
                return false
            }
        })
    }

    private fun addRuleForCurrentPage(seconds: Int, label: String) {
        val page = currentPage
        if (page == null) {
            toast("当前没有捕获到前台页面")
            return
        }
        val pkg = page.packageName
        val act = page.activityName
        // 优先用用户在 EditText 里输入的别名；为空则回退到 Activity 短名
        val customLabel = labelInput?.text?.toString()?.trim().orEmpty()
        val ctx = applicationContext
        scope.launch {
            val appName = withContext(Dispatchers.IO) { AppInfoProvider.getAppName(ctx, pkg) }
            val pageLabel = customLabel.ifBlank {
                act.substringAfterLast('.').ifBlank { "未命名页面" }
            }
            val rule = RuleEntity(
                packageName = pkg,
                appName = appName,
                activityName = act,
                pageLabel = pageLabel,
                maxStaySeconds = seconds
            )
            withContext(Dispatchers.IO) {
                AppLimiterApplication.instance.repository.insert(rule)
            }
            toast("已添加：$appName · $pageLabel · $label")
            // 添加成功后清空别名输入框，方便添加下一个
            labelInput?.post { labelInput?.setText("") }
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
    }

    /**
     * 动态切换悬浮窗 FLAG_NOT_FOCUSABLE。
     * - true：去掉 NOT_FOCUSABLE，让 EditText 能弹出输入法
     * - false：恢复 NOT_FOCUSABLE，避免遮挡其它 App 的触摸
     */
    private fun setOverlayFocusable(focusable: Boolean) {
        val view = floatingView ?: return
        val params = layoutParams ?: return
        params.flags = if (focusable) {
            params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv() or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        } else {
            params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        runCatching { windowManager.updateViewLayout(view, params) }
    }

    private fun dp(v: Int): Int {
        val px = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            v.toFloat(),
            resources.displayMetrics
        )
        return px.toInt()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗服务运行中"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openPi = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("一键限制悬浮窗运行中")
            .setContentText("点击返回应用 · 通知按钮可关闭悬浮窗")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openPi)
            .addAction(0, "关闭悬浮窗", stopPi)
            .build()
    }

    override fun onDestroy() {
        collectJob?.cancel()
        scope.cancel()
        floatingView?.let {
            runCatching { windowManager.removeView(it) }
        }
        floatingView = null
        running = false
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "overlay_floating"
        private const val NOTIF_ID = 2001
        private const val ACTION_STOP = "com.example.applimiter.action.STOP_OVERLAY"

        @Volatile
        private var running: Boolean = false

        fun isRunning(): Boolean = running

        fun hasPermission(context: Context): Boolean {
            return Settings.canDrawOverlays(context)
        }

        fun start(context: Context) {
            val intent = Intent(context, OverlayService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OverlayService::class.java))
        }
    }
}
