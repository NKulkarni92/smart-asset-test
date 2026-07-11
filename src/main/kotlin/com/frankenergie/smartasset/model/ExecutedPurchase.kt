package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

data class ExecutedPurchase(
    val groupId: String,
    val deliveryStart: LocalDateTime,
    val deliveryEnd: LocalDateTime,
    val quantity: BigDecimal,
    val pricePerMWh: BigDecimal,
    val timestamp: Instant = Instant.now()
) {
    val totalCost: BigDecimal get() = quantity * pricePerMWh
}
