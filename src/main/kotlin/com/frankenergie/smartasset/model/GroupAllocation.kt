package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class GroupAllocation(
    val groupId: String,
    val allocations: List<QuarterAllocation>,
    val totalMWh: BigDecimal,
    val totalCost: BigDecimal
)
