package com.frankenergie.smartasset.model

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

data class SteeringSignal(
    val groupId: String,
    val quarterStart: LocalDateTime,
    val quarterEnd: LocalDateTime,
    val chargePowerMW: BigDecimal,
    val timestamp: Instant = Instant.now()
)
