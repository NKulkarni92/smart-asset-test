package com.frankenergie.smartasset.model

import java.time.LocalDateTime

data class DeliveryPeriod(
    val startTime: LocalDateTime,
    val endTime: LocalDateTime
)
