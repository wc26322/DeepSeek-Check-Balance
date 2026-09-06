package com.deepseek.balance.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
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

    // 单源 8s 超时：connect/read/write 快速判死，失败立刻切下一个源（官方直连在国内常被阻塞）
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * 拉取最新 Release。多源依次尝试（官方 API → 加速镜像），任一源成功即返回；
     * 全部失败抛最后一个异常（切源时优先传播取消，避免吞掉协程取消）。
     * OkHttp 不读系统代理，官方 api.github.com 直连常被阻塞 → 必须有多源兜底。
     */
    suspend fun checkLatest(): LatestRelease = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        for (url in checkSources()) {
            try {
                return@withContext fetchLatest(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: ApiException("获取版本信息失败")
    }

    /** 单个源请求 + 解析；网络/解析失败抛 ApiException */
    private fun fetchLatest(url: String): LatestRelease {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "DeepSeekBalanceApp")
            .get()
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw ApiException("网络连接失败，请检查网络后重试")
        }
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
        return LatestRelease(
            version = tag.removePrefix("v"),
            tagName = tag,
            apkUrl = apkUrl,
            notes = root.optString("body", ""),
        )
    }

    /** Release 页面地址（无 APK 直链时的兜底入口） */
    fun releasePageUrl(tagName: String): String = RELEASES_PAGE_URL + tagName

    /**
     * 下载加速镜像（2026-08 实测可用；官方直连慢/失败时自动切换）。
     * 同时用于「检查更新」（前缀代理 api.github.com 请求）与 APK 下载。
     * 注意：APK 有系统签名校验，镜像内容被篡改时安装会被拒绝，信任风险可控。
     */
    private val DOWNLOAD_MIRRORS = listOf(
        "https://gh-proxy.com/",
        "https://gh.llkk.cc/",
        "https://ghfast.top/",
    )

    /** 检查源：官方 API → 加速镜像，逐个尝试直到成功 */
    private fun checkSources(): List<String> =
        listOf(LATEST_URL) + DOWNLOAD_MIRRORS.map { it + LATEST_URL }

    /** 按优先级排列的下载地址：官方直链 + 加速镜像（AppDownloader 依次尝试） */
    fun downloadSources(apkUrl: String): List<String> =
        listOf(apkUrl) + DOWNLOAD_MIRRORS.map { it + apkUrl }

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
