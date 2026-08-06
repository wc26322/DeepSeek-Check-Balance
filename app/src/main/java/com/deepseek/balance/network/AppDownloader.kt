package com.deepseek.balance.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 应用内下载器：OkHttp 流式下载到文件，支持进度回调与取消。
 * 不依赖系统 DownloadManager，由 UI 弹窗实时展示进度/速度。
 */
object AppDownloader {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var currentCall: Call? = null

    /** 取消当前下载：网络层立即中断（IO 阻塞随即抛出，协程层由 ensureActive 感知） */
    fun cancel() {
        currentCall?.cancel()
    }

    /**
     * 流式下载 [url] 到 [targetFile]。
     * [onProgress] 在 IO 线程回调，参数为（已下载字节, 总字节）。
     * 失败或取消会删除半成品文件；取消时抛 [CancellationException]。
     */
    suspend fun download(
        url: String,
        targetFile: File,
        onProgress: (downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val call = client.newCall(Request.Builder().url(url).get().build())
        currentCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw ApiException("下载失败 (${response.code})")
                }
                val body = response.body ?: throw ApiException("响应体为空")
                val total = body.contentLength()
                val source = body.source()
                targetFile.outputStream().use { out ->
                    val buffer = Buffer()
                    var downloaded = 0L
                    while (true) {
                        val read = source.read(buffer, 64 * 1024)
                        if (read == -1L) break
                        buffer.copyTo(out, read)
                        downloaded += read
                        onProgress(downloaded, total)
                        coroutineContext.ensureActive()
                    }
                }
                targetFile
            }
        } catch (e: CancellationException) {
            targetFile.delete()
            throw e
        } catch (e: IOException) {
            // 主动取消时 call.cancel() 会让读取抛 IOException，转为取消语义
            if (call.isCanceled()) {
                targetFile.delete()
                throw CancellationException("下载已取消")
            }
            throw ApiException("网络错误: ${e.message}")
        } finally {
            currentCall = null
        }
    }
}
