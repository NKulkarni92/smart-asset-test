package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

data class Order(
    val orderId: String,
    val deliveryStartTime: LocalDateTime,
    val deliveryEndTime: LocalDateTime,
    val orderSide: OrderSide,
    val originalQuantity: BigDecimal,
    var remainingQuantity: BigDecimal,
    val price: BigDecimal,
    val createdAt: Instant
) {
    val isFilled: Boolean
        get() = remainingQuantity.compareTo(BigDecimal.ZERO) == 0
}
