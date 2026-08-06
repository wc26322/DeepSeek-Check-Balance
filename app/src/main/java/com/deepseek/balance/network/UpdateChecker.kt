package com.deepseek.balance.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** GitHub 最新 Release 信息 */
data class LatestRelease(
    val version: String,   // 去掉 v 前缀，如 "1.3.2"
    val tagName: String,   // 如 "v1.3.2"
    val apkUrl: String,    // app-release.apk 下载直链（可能为空）
    val notes: String,     // Release 说明
)

/**
 * 检查 GitHub Releases 上的最新版本。
 * 使用公开仓库的 latest API，无需鉴权（未认证限流 60 次/小时，检查更新频率低，足够）。
 */
object UpdateChecker {

    private const val REPO = "wc26322/DeepSeek-Check-Balance"
    private const val LATEST_URL = "https://api.github.com/repos/$REPO/releases/latest"
    private const val RELEASES_PAGE_URL = "https://github.com/$REPO/releases/tag/"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 拉取最新 Release；网络/解析失败抛 ApiException */
    suspend fun checkLatest(): LatestRelease = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DeepSeekBalanceApp")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string() ?: throw ApiException("响应体为空")
        if (!response.isSuccessful) {
            throw ApiException("获取版本信息失败 (${response.code})")
        }
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ApiException("响应解析失败: ${e.message}")
        }
        val tag = root.optString("tag_name", "")
        if (tag.isBlank()) throw ApiException("响应缺少 tag_name")

        // 在 assets 里找 app-release.apk 的下载直链
        var apkUrl = ""
        val assets = root.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i)
                if (a != null && a.optString("name", "") == "app-release.apk") {
                    apkUrl = a.optString("browser_download_url", "")
                    break
                }
            }
        }
        LatestRelease(
            version = tag.removePrefix("v"),
            tagName = tag,
            apkUrl = apkUrl,
            notes = root.optString("body", ""),
        )
    }

    /** Release 页面地址（无 APK 直链时的兜底入口） */
    fun releasePageUrl(tagName: String): String = RELEASES_PAGE_URL + tagName

    /**
     * 语义化版本比较：latest 是否比 current 新。
     * 逐段数字比较，段数不足按 0 补齐（如 "1.3" < "1.3.1"）。
     */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        val b = current.removePrefix("v").split('.').mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
