package com.frankenergie.smartasset.config

import com.frankenergie.smartasset.model.ChargingGroup
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Configuration
class ChargingGroupsConfig {

    @Bean
    fun chargingGroups(): List<ChargingGroup> {
        val today = LocalDate.now()
        return listOf(
            ChargingGroup("A", today.atTime(0, 0), today.atTime(8, 30), BigDecimal("5"), BigDecimal("2")),
            ChargingGroup("B", today.atTime(0, 0), today.atTime(11, 0), BigDecimal("10"), BigDecimal("3")),
            ChargingGroup("C", today.atTime(13, 0), today.atTime(18, 0), BigDecimal("4"), BigDecimal("1")),
            ChargingGroup("D", today.atTime(13, 0), today.atTime(21, 0), BigDecimal("20"), BigDecimal("6")),
            ChargingGroup("E", today.atTime(17, 30), today.atTime(22, 0), BigDecimal("5"), BigDecimal("2")),
            ChargingGroup("F", today.atTime(17, 30), today.plusDays(1).atTime(0, 0), BigDecimal("15"), BigDecimal("5"))
        )
    }
}
