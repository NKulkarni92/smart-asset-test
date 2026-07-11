package com.frankenergie.smartasset.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

data class MarketOverviewResponse(
    @JsonProperty("quarters") val quarters: List<QuarterOverview>
)
