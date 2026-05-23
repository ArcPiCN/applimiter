package com.example.applimiter

import android.app.Application
import android.util.Log
import com.example.applimiter.data.db.AppDatabase
import com.example.applimiter.data.io.RuleIo
import com.example.applimiter.data.repository.RuleRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.nio.charset.StandardCharsets

class AppLimiterApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { RuleRepository(database.ruleDao()) }

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        instance = this
        seedBuiltInRulesIfFirstLaunch()
    }

    /**
     * 首次启动时把 res/raw/default_rules.json 写入数据库，作为初始规则集合。
     * 用 SharedPreferences 记一个标记位，确保只跑一次；用户后续可自由删改。
     */
    private fun seedBuiltInRulesIfFirstLaunch() {
        val prefs = getSharedPreferences("app_state", MODE_PRIVATE)
        if (prefs.getBoolean(KEY_SEEDED, false)) return

        ioScope.launch {
            runCatching {
                val json = resources.openRawResource(R.raw.default_rules).use { stream ->
                    stream.readBytes().toString(StandardCharsets.UTF_8)
                }
                when (val r = RuleIo.parseJson(json)) {
                    is RuleIo.ParseResult.Failure -> {
                        Log.w(TAG, "Seed parse failed: ${r.message}")
                    }
                    is RuleIo.ParseResult.Success -> {
                        if (r.rules.isNotEmpty()) {
                            // 二次确认数据库为空，避免用户已有数据被覆盖
                            val existing = repository.allRulesNow()
                            if (existing.isEmpty()) {
                                repository.insertAll(r.rules)
                                Log.i(TAG, "Seeded ${r.rules.size} built-in rules")
                            } else {
                                Log.i(TAG, "DB not empty (${existing.size}), skip seeding")
                            }
                        }
                    }
                }
                Unit  // 让 runCatching lambda 返回 Unit，避免 when 被当作表达式触发 if/else 检查
            }.onFailure { Log.e(TAG, "Seed error", it) }

            // 无论成功失败都置位，避免每次启动都尝试
            prefs.edit().putBoolean(KEY_SEEDED, true).apply()
        }
    }

    companion object {
        private const val TAG = "AppLimiterApp"
        private const val KEY_SEEDED = "seeded_built_in_rules_v1"

        @Volatile
        lateinit var instance: AppLimiterApplication
            private set
    }
}
