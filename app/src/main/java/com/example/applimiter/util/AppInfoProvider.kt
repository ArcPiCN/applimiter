package com.example.applimiter.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * 已安装应用信息查询。
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean
)

object AppInfoProvider {

    /**
     * 获取已安装应用列表。
     *
     * @param includeSystem 是否包含系统应用。默认 false，只返回用户安装的 App。
     */
    fun getInstalledApps(context: Context, includeSystem: Boolean = false): List<InstalledApp> {
        val pm = context.packageManager
        val flags = PackageManager.GET_META_DATA
        val all = pm.getInstalledApplications(flags)
        return all
            .filter { ai ->
                // 必须能从启动器启动，否则用户根本没法在桌面看到
                pm.getLaunchIntentForPackage(ai.packageName) != null &&
                    (includeSystem || (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                        (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)
            }
            .map { ai ->
                InstalledApp(
                    packageName = ai.packageName,
                    appName = pm.getApplicationLabel(ai).toString(),
                    icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull(),
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }
            .sortedBy { it.appName }
    }

    fun getAppName(context: Context, packageName: String): String {
        return runCatching {
            val ai = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(ai).toString()
        }.getOrDefault(packageName)
    }

    /** 取应用图标 Drawable，失败返回 null（请调用方自己兜底）。 */
    fun getAppIcon(context: Context, packageName: String): Drawable? {
        return runCatching {
            context.packageManager.getApplicationIcon(packageName)
        }.getOrNull()
    }
}
