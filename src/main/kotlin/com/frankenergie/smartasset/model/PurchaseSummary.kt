package com.frankenergie.smartasset.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class PurchaseSummary(
    @JsonProperty("total_mwh") val totalMWh: BigDecimal,
    @JsonProperty("total_cost") val totalCost: BigDecimal,
    @JsonProperty("average_price_per_mwh") val averagePricePerMWh: BigDecimal
)
