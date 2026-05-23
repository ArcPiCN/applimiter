package com.example.applimiter.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 一条页面停留时长规则。
 *
 * @param packageName 目标 App 包名
 * @param appName App 显示名（缓存，避免每次反查 PackageManager）
 * @param activityName 目标 Activity 全名
 * @param pageLabel 页面别名（用户自定义/预设）
 * @param maxStaySeconds 允许停留秒数
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val activityName: String,
    val pageLabel: String,
    val maxStaySeconds: Int,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
