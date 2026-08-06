package com.deepseek.balance.network

import com.deepseek.balance.model.DailyUsage
import com.deepseek.balance.model.KeyUsage
import com.deepseek.balance.model.ModelDailyUsage
import com.deepseek.balance.model.ModelUsage
import com.deepseek.balance.model.UsageData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * DeepSeek 网页用量接口客户端（platform.deepseek.com）
 *
 * 这些接口需要「网页令牌」（从 platform.deepseek.com 登录态取得，非官方 API Key）。
 * 与官方余额接口独立的鉴权体系。
 *
 * 关键约束（已验证）：by_api_key 的 start/end 必须是 UTC 午夜的整数 Unix 秒
 * （86400 的整数倍），否则返回 biz_code:1 INVALID_PARAM。
 */
object UsageClient {

    private const val BASE_URL = "https://platform.deepseek.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private fun buildRequest(path: String, token: String): Request {
        return Request.Builder()
            .url("$BASE_URL$path")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Referer", "https://platform.deepseek.com/usage")
            .header("x-app-version", "1.0.0")
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
    }

    /**
     * 计算对齐到 UTC 午夜的 [start, end) 窗口（最近 days 天）。
     * 结束点取「明天零点」：这样今天的桶才会被包含进窗口（若止于今天零点，最后一天是昨天）。
     */
    private fun window(days: Int): Pair<Long, Long> {
        val endDay = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC)
        val startDay = endDay.minusDays(days.toLong())
        return startDay.toEpochSecond() to endDay.toEpochSecond()
    }

    /**
     * 拉取用量聚合数据。token 为网页令牌。
     * 并行请求：用户总览（累计消费） + 按 Key 用量 + 按 Key 成本，最后汇总。
     *
     * days = 聚合统计窗口（近 N 天，用于概览/按模型/按 Key）；
     * dailyDays = 每日明细窗口。注意：平台用量接口只返回最近约 30 天的每日数据，
     * 更早的窗口返回空，因此 dailyDays 上限即 30。
     */
    suspend fun getUsage(token: String, days: Int = 30, dailyDays: Int = 30): UsageData =
        withContext(Dispatchers.IO) {
            val (aggStart, aggEnd) = window(days)
            val qAgg = "start=$aggStart&end=$aggEnd&tz=0"

            val summaryJson = fetchJson("/api/v0/users/get_user_summary", token)
            val costJson = fetchJson("/api/v0/usage/by_api_key/cost?$qAgg", token)
            val amountChunks = fetchAmountChunks(token, dailyDays)

            parseUsage(summaryJson, amountChunks, costJson, days, aggStart)
        }

    /**
     * 分块拉取每日用量：接口单次最多约 31 天，把 totalDays 拆成多块（每块 ≤30 天）顺序拉取。
     * 返回结果按「最近 → 更早」排列。
     */
    private fun fetchAmountChunks(token: String, totalDays: Int, chunkDays: Int = 30): List<JSONObject> {
        // 终点取「明天零点」，使今天的每日数据也被返回
        val endDay = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC)
        val result = mutableListOf<JSONObject>()
        var curEnd = endDay
        var remaining = totalDays
        while (remaining > 0) {
            val len = minOf(chunkDays, remaining)
            val curStart = curEnd.minusDays(len.toLong())
            val q = "start=${curStart.toEpochSecond()}&end=${curEnd.toEpochSecond()}&tz=0"
            result.add(fetchJson("/api/v0/usage/by_api_key/amount?$q", token))
            curEnd = curStart
            remaining -= len
        }
        return result
    }

    /**
     * 拉取任意日期范围内的每日用量（按模型），用于「本月 / 上月 / 自定义」维度。
     * 平台月度接口 usage/amount?year&month 返回整月每天的按模型数据（含 REQUEST、
     * 输入命中/未命中、输出 Tokens），因此把范围涉及的月份各拉一次，合并后过滤到 [start, end]。
     * 建议 start/end 间隔 ≤31 天。
     */
    suspend fun getRangeDaily(
        token: String,
        start: LocalDate,
        end: LocalDate,
    ): List<ModelDailyUsage> = withContext(Dispatchers.IO) {
        if (end.isBefore(start)) return@withContext emptyList()

        val months = LinkedHashSet<YearMonth>()
        var d = start
        while (!d.isAfter(end)) {
            months.add(YearMonth.from(d))
            d = d.plusDays(1)
        }

        val merged = LinkedHashMap<String, LinkedHashMap<LocalDate, DailyAcc>>()
        for (ym in months) {
            val json = fetchJson("/api/v0/usage/amount?year=${ym.year}&month=${ym.monthValue}", token)
            val bd = bizData(json)
            val daysArr = bd.optJSONArray("days") ?: continue
            for (i in 0 until daysArr.length()) {
                val day = daysArr.getJSONObject(i)
                val date = try {
                    LocalDate.parse(day.optString("date", ""))
                } catch (e: Exception) {
                    continue
                }
                if (date.isBefore(start) || date.isAfter(end)) continue
                val dataArr = day.optJSONArray("data") ?: continue
                for (j in 0 until dataArr.length()) {
                    val ent = dataArr.getJSONObject(j)
                    val model = ent.optString("model", "unknown")
                    val acc = merged.getOrPut(model) { LinkedHashMap() }.getOrPut(date) { DailyAcc() }
                    val usageArr = ent.optJSONArray("usage") ?: continue
                    for (k in 0 until usageArr.length()) {
                        val u = usageArr.getJSONObject(k)
                        when (u.optString("type", "")) {
                            "REQUEST" -> acc.calls += u.optLong("amount", 0L)
                            "PROMPT_CACHE_HIT_TOKEN" -> acc.cacheHit += u.optLong("amount", 0L)
                            "PROMPT_CACHE_MISS_TOKEN" -> acc.cacheMiss += u.optLong("amount", 0L)
                            "RESPONSE_TOKEN" -> acc.response += u.optLong("amount", 0L)
                        }
                    }
                }
            }
        }

        val deprecated = setOf("deepseek-chat & deepseek-reasoner", "deepseek-chat", "deepseek-reasoner")
        merged
            .filterKeys { it !in deprecated }
            .map { (model, byDate) ->
                ModelDailyUsage(
                    model = model,
                    daily = byDate.entries
                        .sortedBy { it.key }
                        .map { (date, acc) ->
                            DailyUsage(
                                date = date.format(MM_DD),
                                apiCalls = acc.calls.toInt(),
                                cacheHitTokens = acc.cacheHit,
                                cacheMissTokens = acc.cacheMiss,
                                responseTokens = acc.response,
                            )
                        },
                )
            }
            .sortedByDescending { it.daily.sumOf { x -> x.totalTokens } }
    }

    private val MM_DD: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")

    /** 执行请求并返回根 JSONObject；非 0 业务码抛 ApiException */
    private fun fetchJson(path: String, token: String): JSONObject {
        val response = client.newCall(buildRequest(path, token)).execute()
        val body = response.body?.string() ?: throw ApiException("响应体为空")
        if (!response.isSuccessful) {
            throw ApiException(
                message = when (response.code) {
                    401 -> "网页令牌无效或已过期，请重新获取"
                    403 -> "网页令牌无权访问"
                    429 -> "请求过于频繁，请稍后再试"
                    else -> "用量请求失败 (${response.code})"
                },
                code = response.code,
            )
        }
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ApiException("响应解析失败: ${e.message}")
        }
    }

    /** 取出 biz_data（成功时）；否则抛错 */
    private fun bizData(root: JSONObject): JSONObject {
        val code = root.optInt("code", -1)
        if (code != 0) {
            // DeepSeek 网页接口鉴权失败：HTTP 200 + 业务码 40003（Authorization Failed）。
            // 映射为带 code 的 ApiException，供上层识别「令牌失效」并引导重新登录。
            if (code == 40003) {
                throw ApiException("网页令牌无效或已过期，请重新获取", code = 40003)
            }
            throw ApiException(root.optString("msg", "业务错误 code=$code"))
        }
        val data = root.optJSONObject("data") ?: throw ApiException("响应缺少 data 字段")
        val bizCode = data.optInt("biz_code", -1)
        if (bizCode != 0) {
            throw ApiException(data.optString("biz_msg", "业务错误 biz_code=$bizCode"))
        }
        return data.optJSONObject("biz_data")
            ?: throw ApiException("响应缺少 biz_data 字段")
    }

    private fun parseUsage(
        summaryRoot: JSONObject,
        amountChunks: List<JSONObject>,
        costRoot: JSONObject,
        days: Int,
        aggStart: Long,
    ): UsageData {
        // 累计消费（get_user_summary.total_costs）
        var totalCostCny = 0.0
        val summaryBiz = bizData(summaryRoot)
        val totalCosts = summaryBiz.optJSONArray("total_costs")
        if (totalCosts != null) {
            for (i in 0 until totalCosts.length()) {
                val c = totalCosts.getJSONObject(i)
                if (c.optString("currency", "") == "CNY") {
                    totalCostCny += c.optDouble("amount", 0.0)
                }
            }
        }

        val costBiz = bizData(costRoot)

        // 按模型 / 按 Key 累加
        val modelMap = LinkedHashMap<String, ModelUsageAcc>()
        val keyMap = LinkedHashMap<String, KeyUsageAcc>()
        // model → 日期(epoch秒) → 累加器；同一模型可能有多个 series（不同 api_key），按日期合并
        val modelDailyMap = LinkedHashMap<String, LinkedHashMap<Long, DailyAcc>>()

        // amount 分块：每块提供 调用次数 + tokens + 每日明细；聚合只统计 time >= aggStart 的桶
        for (chunk in amountChunks) {
            val amountBiz = bizData(chunk) ?: continue
            val series = amountBiz.optJSONArray("series") ?: continue
            for (i in 0 until series.length()) {
                val s = series.getJSONObject(i)
                val apiKey = s.optJSONObject("api_key") ?: JSONObject()
                val model = s.optString("model", "unknown")
                val tid = apiKey.optString("tracking_id", "")
                val buckets = s.optJSONArray("buckets") ?: continue

                var calls = 0L
                var tokens = 0L
                val modelDays = modelDailyMap.getOrPut(model) { LinkedHashMap() }
                for (b in 0 until buckets.length()) {
                    val bucket = buckets.getJSONObject(b)
                    val time = bucket.optLong("time", 0L)
                    val u = bucket.optJSONObject("usage") ?: JSONObject()
                    if (time >= aggStart) {
                        calls += u.optLong("REQUEST", 0L)
                        tokens += u.optLong("RESPONSE_TOKEN", 0L) +
                            u.optLong("PROMPT_CACHE_HIT_TOKEN", 0L) +
                            u.optLong("PROMPT_CACHE_MISS_TOKEN", 0L)
                    }
                    // 每日明细：始终收集（输入命中/未命中 + 输出）
                    val acc = modelDays.getOrPut(time) { DailyAcc() }
                    acc.calls += u.optLong("REQUEST", 0L)
                    acc.cacheHit += u.optLong("PROMPT_CACHE_HIT_TOKEN", 0L)
                    acc.cacheMiss += u.optLong("PROMPT_CACHE_MISS_TOKEN", 0L)
                    acc.response += u.optLong("RESPONSE_TOKEN", 0L)
                }
                modelMap.getOrPut(model) { ModelUsageAcc() }.apply {
                    this.calls += calls; this.tokens += tokens
                }
                keyMap.getOrPut(tid) { KeyUsageAcc() }.apply {
                    this.name = apiKey.optString("name", "unknown")
                    this.sensitiveId = apiKey.optString("sensitive_id", "")
                    this.valid = apiKey.optBoolean("valid", true)
                    this.calls += calls; this.tokens += tokens
                }
            }
        }

        // cost 提供 金额（CNY 累加）
        val costData = costBiz.optJSONArray("data")
        if (costData != null) {
            for (i in 0 until costData.length()) {
                val d = costData.getJSONObject(i)
                if (d.optString("currency", "") != "CNY") continue
                val cs = d.optJSONArray("series") ?: continue
                for (j in 0 until cs.length()) {
                    val s = cs.getJSONObject(j)
                    val model = s.optString("model", "unknown")
                    val tid = s.optJSONObject("api_key")?.optString("tracking_id", "") ?: ""
                    val buckets = s.optJSONArray("buckets") ?: continue
                    var cost = 0.0
                    for (b in 0 until buckets.length()) {
                        val c = buckets.getJSONObject(b).optString("cost", "0")
                        cost += c.toDoubleOrNull() ?: 0.0
                    }
                    modelMap[model]?.costCny = (modelMap[model]?.costCny ?: 0.0) + cost
                    keyMap[tid]?.costCny = (keyMap[tid]?.costCny ?: 0.0) + cost
                }
            }
        }

        // 官网已下架的旧模型别名，不再展示
        val deprecatedModels = setOf("deepseek-chat & deepseek-reasoner", "deepseek-chat", "deepseek-reasoner")
        val byModel = modelMap
            .filterKeys { it !in deprecatedModels }
            .map { (model, acc) ->
                ModelUsage(model, acc.calls.toInt(), acc.tokens, acc.costCny)
            }
            .sortedByDescending { it.apiCalls }

        val byModelDaily = modelDailyMap
            .filterKeys { it !in deprecatedModels }
            .map { (model, days) ->
                ModelDailyUsage(
                    model = model,
                    daily = days.entries
                        .sortedBy { it.key } // 按时间升序（旧 → 新）
                        .map { (time, acc) ->
                            DailyUsage(
                                date = formatDay(time),
                                apiCalls = acc.calls.toInt(),
                                cacheHitTokens = acc.cacheHit,
                                cacheMissTokens = acc.cacheMiss,
                                responseTokens = acc.response,
                            )
                        },
                )
            }
            .sortedByDescending { it.daily.sumOf { d -> d.totalTokens } }

        val byKey = keyMap.map { (tid, acc) ->
            KeyUsage(acc.name, tid, acc.sensitiveId, acc.valid, acc.calls.toInt(), acc.tokens, acc.costCny)
        }.sortedByDescending { it.apiCalls }

        val apiCalls = byKey.sumOf { it.apiCalls }
        val totalTokens = byKey.sumOf { it.totalTokens }

        return UsageData(
            totalCostCny = totalCostCny,
            apiCalls = apiCalls,
            totalTokens = totalTokens,
            windowDays = days,
            byModel = byModel,
            byKey = byKey,
            byModelDaily = byModelDaily,
        )
    }

    /** 把 UTC 午夜的 Unix 秒转成 "MM-dd" */
    private fun formatDay(epochSecond: Long): String {
        if (epochSecond <= 0L) return ""
        return try {
            Instant.ofEpochSecond(epochSecond)
                .atZone(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("MM-dd"))
        } catch (e: Exception) {
            ""
        }
    }

    private class ModelUsageAcc(
        var calls: Long = 0L,
        var tokens: Long = 0L,
        var costCny: Double = 0.0,
    )

    private class KeyUsageAcc(
        var name: String = "unknown",
        var sensitiveId: String = "",
        var valid: Boolean = true,
        var calls: Long = 0L,
        var tokens: Long = 0L,
        var costCny: Double = 0.0,
    )

    private class DailyAcc(
        var calls: Long = 0L,
        var cacheHit: Long = 0L,
        var cacheMiss: Long = 0L,
        var response: Long = 0L,
    )
}
