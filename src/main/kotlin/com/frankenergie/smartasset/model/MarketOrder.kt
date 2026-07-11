package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

data class MarketOrder(
    val orderId: String,
    val groupId: String,
    val deliveryStart: LocalDateTime,
    val deliveryEnd: LocalDateTime,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val timestamp: Instant = Instant.now()
)
