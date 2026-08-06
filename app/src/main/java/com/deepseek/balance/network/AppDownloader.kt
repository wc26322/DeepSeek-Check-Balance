package com.deepseek.balance.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 应用内下载器：OkHttp 流式下载到文件。
 * - 多源自动切换：按 [sources] 顺序尝试，失败自动切下一个源（保留已下载字节）
 * - 断点续传：已存在文件时用 HTTP Range 从断点继续；服务器不支持 Range 时从头下载
 * - 取消/失败均保留半成品文件，便于下次续传
 */
object AppDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentCall: Call? = null

    /** 取消当前下载：网络层立即中断（IO 阻塞随即抛出，协程层由 ensureActive 感知） */
    fun cancel() {
        currentCall?.cancel()
    }

    /**
     * 按 [sources] 顺序下载到 [targetFile]；每个源失败自动切下一个（断点续传）。
     * [onProgress] 在 IO 线程回调（已下载字节, 总字节）；[onSource] 回调当前源 URL。
     * 全部源失败时抛最后一个异常；取消抛 [CancellationException]。
     */
    suspend fun download(
        sources: List<String>,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
        onSource: (String) -> Unit = {},
    ): File {
        var lastError: Exception? = null
        for (url in sources) {
            onSource(url)
            try {
                return downloadOnce(url, targetFile, onProgress)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw lastError ?: ApiException("下载失败")
    }

    private suspend fun downloadOnce(
        url: String,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val existing = if (targetFile.exists()) targetFile.length() else 0L
        val requestBuilder = Request.Builder().url(url).get()
        // 已有部分字节：请求断点续传
        if (existing > 0) {
            requestBuilder.header("Range", "bytes=$existing-")
        }
        val call = client.newCall(requestBuilder.build())
        currentCall = call
        try {
            call.execute().use { response ->
                if (response.code != 200 && response.code != 206) {
                    throw ApiException("下载失败 (${response.code})")
                }
                val isPartial = response.code == 206
                // 服务器忽略 Range（返回 200 全量）：删除旧文件从头下载
                if (!isPartial && existing > 0) {
                    targetFile.delete()
                }
                val body = response.body ?: throw ApiException("响应体为空")
                val remaining = body.contentLength()
                val total = if (isPartial) existing + remaining else remaining
                val base = if (isPartial) existing else 0L
                FileOutputStream(targetFile, isPartial && existing > 0).use { out ->
                    body.byteStream().use { input ->
                        val buf = ByteArray(64 * 1024)
                        var written = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            written += n
                            onProgress(base + written, total)
                            coroutineContext.ensureActive()
                        }
                    }
                }
                targetFile
            }
        } catch (e: CancellationException) {
            // 取消：保留已下载字节（断点），下次可续传
            throw e
        } catch (e: IOException) {
            // 主动取消时 call.cancel() 会让读取抛 IOException，转为取消语义
            if (call.isCanceled()) {
                throw CancellationException("下载已取消")
            }
            throw ApiException("网络连接失败，请检查网络后重试")
        } finally {
            currentCall = null
        }
    }
}
