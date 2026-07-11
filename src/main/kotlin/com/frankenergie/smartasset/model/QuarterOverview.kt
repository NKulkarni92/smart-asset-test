package com.frankenergie.smartasset.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.LocalDateTime

data class QuarterOverview(
    @JsonProperty("delivery_start_time") val deliveryStartTime: LocalDateTime,
    @JsonProperty("delivery_end_time") val deliveryEndTime: LocalDateTime,
    @JsonProperty("highest_buy_price") val highestBuyPrice: BigDecimal?,
    @JsonProperty("lowest_sell_price") val lowestSellPrice: BigDecimal?
)