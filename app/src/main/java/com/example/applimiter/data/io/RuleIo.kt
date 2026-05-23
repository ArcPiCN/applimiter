package com.example.applimiter.data.io

import android.content.Context
import android.net.Uri
import com.example.applimiter.data.db.RuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 规则文件 JSON Schema v2（按应用分组）：
 * {
 *   "schema": "applimiter.rules",
 *   "version": 2,
 *   "exportedAt": 1716480000000,
 *   "apps": [
 *     {
 *       "packageName": "com.ss.android.ugc.aweme",
 *       "appName": "抖音",
 *       "pages": [
 *         {
 *           "activityName": "...",
 *           "pageLabel": "视频详情页",
 *           "maxStaySeconds": 15,
 *           "isEnabled": true
 *         }
 *       ]
 *     }
 *   ]
 * }
 *
 * 解析时同时兼容 v1 扁平格式（顶层 rules 数组）。
 */
object RuleIo {

    private const val SCHEMA = "applimiter.rules"
    private const val VERSION = 2
    private const val MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024 // 2MB

    /** 序列化为漂亮的 JSON 字符串（v2，按应用分组）。 */
    fun toJson(rules: List<RuleEntity>): String {
        val grouped = rules.groupBy { it.packageName }
        val appsArr = JSONArray()

        grouped.forEach { (pkg, list) ->
            val appName = list.firstOrNull { it.appName.isNotBlank() }?.appName ?: pkg
            val pages = JSONArray()
            list.forEach { r ->
                pages.put(
                    JSONObject()
                        .put("activityName", r.activityName)
                        .put("pageLabel", r.pageLabel)
                        .put("maxStaySeconds", r.maxStaySeconds)
                        .put("isEnabled", r.isEnabled)
                )
            }
            appsArr.put(
                JSONObject()
                    .put("packageName", pkg)
                    .put("appName", appName)
                    .put("pages", pages)
            )
        }

        val root = JSONObject()
            .put("schema", SCHEMA)
            .put("version", VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("apps", appsArr)
        return root.toString(2)
    }

    /**
     * 解析 JSON：
     * - v2：顶层 apps 数组，每个 app 含 packageName/appName/pages
     * - v1（兼容）：顶层 rules 数组，每条含完整字段
     */
    fun parseJson(text: String): ParseResult {
        if (text.isBlank()) return ParseResult.Failure("内容为空")
        val root = runCatching { JSONObject(text) }.getOrElse {
            return ParseResult.Failure("不是合法的 JSON")
        }

        val out = mutableListOf<RuleEntity>()
        var skipped = 0
        val now = System.currentTimeMillis()

        when {
            root.has("apps") -> {
                val apps = root.optJSONArray("apps") ?: return ParseResult.Failure("apps 字段不是数组")
                for (i in 0 until apps.length()) {
                    val appObj = apps.optJSONObject(i)
                    if (appObj == null) {
                        skipped++
                        continue
                    }
                    val pkg = appObj.optString("packageName").orEmptyToNull()
                    if (pkg == null) {
                        skipped++
                        continue
                    }
                    val appName = appObj.optString("appName").ifBlank { pkg }
                    val pages = appObj.optJSONArray("pages") ?: continue
                    for (j in 0 until pages.length()) {
                        val p = pages.optJSONObject(j)
                        if (p == null) {
                            skipped++
                            continue
                        }
                        val act = p.optString("activityName").orEmptyToNull()
                        val sec = p.optInt("maxStaySeconds", -1)
                        if (act == null || sec < 0) {
                            skipped++
                            continue
                        }
                        out.add(
                            RuleEntity(
                                id = 0L,
                                packageName = pkg,
                                appName = appName,
                                activityName = act,
                                pageLabel = p.optString("pageLabel").ifBlank {
                                    act.substringAfterLast('.')
                                },
                                maxStaySeconds = sec,
                                isEnabled = p.optBoolean("isEnabled", true),
                                createdAt = now
                            )
                        )
                    }
                }
            }
            root.has("rules") -> {
                // v1 兼容
                val arr = root.optJSONArray("rules")
                    ?: return ParseResult.Failure("rules 字段不是数组")
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i)
                    if (o == null) {
                        skipped++
                        continue
                    }
                    val pkg = o.optString("packageName").orEmptyToNull()
                    val act = o.optString("activityName").orEmptyToNull()
                    val sec = o.optInt("maxStaySeconds", -1)
                    if (pkg == null || act == null || sec < 0) {
                        skipped++
                        continue
                    }
                    out.add(
                        RuleEntity(
                            id = 0L,
                            packageName = pkg,
                            appName = o.optString("appName").ifBlank { pkg },
                            activityName = act,
                            pageLabel = o.optString("pageLabel").ifBlank {
                                act.substringAfterLast('.')
                            },
                            maxStaySeconds = sec,
                            isEnabled = o.optBoolean("isEnabled", true),
                            createdAt = now
                        )
                    )
                }
            }
            else -> return ParseResult.Failure("不是规则文件（缺少 apps 或 rules 字段）")
        }

        return ParseResult.Success(out, skipped)
    }

    suspend fun readUri(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(StandardCharsets.UTF_8)
        } ?: error("无法打开文件")
    }

    suspend fun downloadFromUrl(urlStr: String): String = withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        require(url.protocol.equals("http", true) || url.protocol.equals("https", true)) {
            "仅支持 http/https"
        }
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json,text/plain,*/*")
            setRequestProperty("User-Agent", "AppLimiter/1.0")
            instanceFollowRedirects = true
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val stream = conn.inputStream
            val bytes = stream.use { s ->
                val baos = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > MAX_DOWNLOAD_BYTES) error("文件过大（>2MB）")
                    baos.write(buf, 0, n)
                }
                baos.toByteArray()
            }
            bytes.toString(StandardCharsets.UTF_8)
        } finally {
            conn.disconnect()
        }
    }

    sealed class ParseResult {
        data class Success(val rules: List<RuleEntity>, val skipped: Int) : ParseResult()
        data class Failure(val message: String) : ParseResult()
    }

    private fun String.orEmptyToNull(): String? = ifBlank { null }
}
