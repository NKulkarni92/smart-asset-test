package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

data class Trade(
    val tradeId: String,
    val buyOrderId: String,
    val sellOrderId: String,
    val price: BigDecimal,
    val quantity: BigDecimal,
    val executedAt: Instant
)
