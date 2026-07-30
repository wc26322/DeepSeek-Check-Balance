package com.deepseek.balance.network

import com.deepseek.balance.model.BalanceInfo
import com.deepseek.balance.model.BalanceResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 余额 API 网络请求客户端
 */
object ApiClient {

    private const val BASE_URL = "https://api.deepseek.com"
    private const val BALANCE_ENDPOINT = "/user/balance"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * 查询余额（挂起函数，需在协程中调用）
     */
    suspend fun getBalance(apiKey: String): BalanceResponse =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL$BALANCE_ENDPOINT")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string()
                ?: throw ApiException("响应体为空")

            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(body).optString("error", body)
                } catch (_: Exception) {
                    body
                }
                throw ApiException(
                    message = when (response.code) {
                        401 -> "API Key 无效，请检查密钥"
                        429 -> "请求过于频繁，请稍后再试"
                        else -> "请求失败 (${response.code})"
                    },
                    code = response.code
                )
            }

            try {
                parseBalanceResponse(body)
            } catch (e: Exception) {
                throw ApiException("数据解析失败: ${e.message}")
            }
        }

    /**
     * 解析 JSON 响应
     */
    private fun parseBalanceResponse(json: String): BalanceResponse {
        val root = JSONObject(json)

        val isAvailable = root.optBoolean("is_available", false)
        val balanceInfos = mutableListOf<BalanceInfo>()

        val infoArray = root.optJSONArray("balance_infos")
        if (infoArray != null) {
            for (i in 0 until infoArray.length()) {
                val item = infoArray.getJSONObject(i)
                balanceInfos.add(
                    BalanceInfo(
                        currency = item.optString("currency", "CNY"),
                        totalBalance = item.optString("total_balance", "0.00"),
                        grantedBalance = item.optString("granted_balance", "0.00"),
                        toppedUpBalance = item.optString("topped_up_balance", "0.00")
                    )
                )
            }
        }

        return BalanceResponse(
            isAvailable = isAvailable,
            balanceInfos = balanceInfos
        )
    }
}

/** 自定义 API 异常 */
class ApiException(
    message: String,
    val code: Int = -1
) : Exception(message)
