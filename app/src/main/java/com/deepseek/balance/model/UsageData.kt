package com.deepseek.balance.model

/**
 * DeepSeek 用量/资产聚合数据模型（网页用量页口径）
 *
 * 说明：余额来自官方 API Key 接口（BalanceResponse）；
 * 以下用量数据来自 platform.deepseek.com 网页接口，需用「网页令牌」鉴权。
 *
 * 注意口径：
 *  - totalCostCny 为「累计消费」（来自 get_user_summary，全量累计）
 *  - apiCalls / totalTokens / byModel / byKey 为「近 windowDays 天」窗口聚合
 *    （对应网页用量信息页默认显示的时间窗口）
 */
data class UsageData(
    val totalCostCny: Double,
    val apiCalls: Int,
    val totalTokens: Long,
    val windowDays: Int,
    val byModel: List<ModelUsage>,
    val byKey: List<KeyUsage>,
    val byModelDaily: List<ModelDailyUsage> = emptyList(),
)

data class ModelUsage(
    val model: String,
    val apiCalls: Int,
    val totalTokens: Long,
    val costCny: Double,
)

/** 单个模型每天的用量明细 */
data class ModelDailyUsage(
    val model: String,
    val daily: List<DailyUsage>,
)

/** 某一天（date 为 MM-dd，UTC 口径）的用量与 Tokens 拆解 */
data class DailyUsage(
    val date: String,
    val apiCalls: Int,
    val cacheHitTokens: Long,
    val cacheMissTokens: Long,
    val responseTokens: Long,
) {
    val totalTokens: Long
        get() = cacheHitTokens + cacheMissTokens + responseTokens
}

data class KeyUsage(
    val name: String,
    val trackingId: String,
    val sensitiveId: String,
    val valid: Boolean,
    val apiCalls: Int,
    val totalTokens: Long,
    val costCny: Double,
)
