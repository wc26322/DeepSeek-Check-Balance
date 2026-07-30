package com.deepseek.balance.model

/**
 * DeepSeek 余额 API 响应数据模型
 */
data class BalanceResponse(
    val isAvailable: Boolean,
    val balanceInfos: List<BalanceInfo>
)

data class BalanceInfo(
    val currency: String,        // "CNY" 或 "USD"
    val totalBalance: String,    // 总余额
    val grantedBalance: String,  // 赠金余额
    val toppedUpBalance: String  // 充值余额
) {
    /** 币种符号 */
    val symbol: String
        get() = when (currency) {
            "CNY" -> "¥"
            "USD" -> "$"
            else -> "$"
        }
}
