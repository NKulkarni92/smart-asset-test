package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class QuarterAllocation(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val mwh: BigDecimal,
    val pricePerMWh: BigDecimal
) {
    val cost: BigDecimal get() = mwh * pricePerMWh
}
