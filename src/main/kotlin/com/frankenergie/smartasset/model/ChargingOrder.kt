package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class ChargingGroup(
    val id: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val neededChargeMWh: BigDecimal,
    val maxPowerMW: BigDecimal
)
