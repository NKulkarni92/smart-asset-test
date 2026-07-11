package com.frankenergie.smartasset.model

import java.math.BigDecimal

data class ChargingPlan(
    val groupAllocations: List<GroupAllocation>,
    val totalCost: BigDecimal
) {
    val averagePricePerMWh: BigDecimal
        get() {
            val totalMWh = groupAllocations.sumOf { it.totalMWh }
            return if (totalMWh > BigDecimal.ZERO) totalCost.divide(totalMWh, 2, java.math.RoundingMode.HALF_UP) else BigDecimal.ZERO
        }
}
