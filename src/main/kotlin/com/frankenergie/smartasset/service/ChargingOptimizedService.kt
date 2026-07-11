package com.frankenergie.smartasset.service

import com.frankenergie.smartasset.client.SteeringSignalClient
import com.frankenergie.smartasset.domain.OrderBook
import com.frankenergie.smartasset.model.*
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDateTime

@Service
class ChargingOptimizerService(
    private val chargingGroups: List<ChargingGroup>,
    private val steeringSignalClient: SteeringSignalClient
) {
    private val quarterDurationHours = BigDecimal("0.25")

    fun optimizePlan(
        orderBook: OrderBook,
        chargedSoFar: Map<String, BigDecimal> = emptyMap()
    ): ChargingPlan {
        val groupAllocations = chargingGroups.map { group ->
            optimizeGroup(group, orderBook, chargedSoFar[group.id] ?: BigDecimal.ZERO)
        }

        val totalCost = groupAllocations.sumOf { it.totalCost }
        return ChargingPlan(groupAllocations, totalCost)
    }

    fun optimize(orderBook: OrderBook, chargedSoFar: Map<String, BigDecimal> = emptyMap()): ChargingPlan {
        val plan = optimizePlan(orderBook, chargedSoFar)

        plan.groupAllocations.forEach { groupAllocation ->
            groupAllocation.allocations.forEach { allocation ->
                val signal = SteeringSignal(
                    groupId = groupAllocation.groupId,
                    quarterStart = allocation.startTime,
                    quarterEnd = allocation.endTime,
                    chargePowerMW = allocation.mwh.divide(quarterDurationHours)
                )
                steeringSignalClient.sendSignal(signal)
            }
        }

        return plan
    }

    private fun optimizeGroup(
        group: ChargingGroup,
        orderBook: OrderBook,
        alreadyCharged: BigDecimal
    ): GroupAllocation {
        val remainingNeed = (group.neededChargeMWh - alreadyCharged).max(BigDecimal.ZERO)
        val maxPerQuarter = group.maxPowerMW * quarterDurationHours
        val quarters = getQuartersInWindow(group.startTime, group.endTime)

        val quartersWithPrices = quarters.mapNotNull { period ->
            val bestAsk = orderBook.getBestAsk(period)
            bestAsk?.let { period to it.price }
        }.sortedBy { it.second }

        val allocations = mutableListOf<QuarterAllocation>()
        var allocated = BigDecimal.ZERO

        for ((period, price) in quartersWithPrices) {
            if (allocated >= remainingNeed) break

            val toAllocate = maxPerQuarter.min(remainingNeed - allocated)
            allocations.add(
                QuarterAllocation(
                    startTime = period.startTime,
                    endTime = period.endTime,
                    mwh = toAllocate,
                    pricePerMWh = price
                )
            )
            allocated += toAllocate
        }

        return GroupAllocation(
            groupId = group.id,
            allocations = allocations,
            totalMWh = allocated,
            totalCost = allocations.sumOf { it.cost }
        )
    }

    private fun getQuartersInWindow(start: LocalDateTime, end: LocalDateTime): List<DeliveryPeriod> {
        val quarters = mutableListOf<DeliveryPeriod>()
        var current = start
        while (current < end) {
            val quarterEnd = current.plusMinutes(15)
            quarters.add(DeliveryPeriod(current, quarterEnd.coerceAtMost(end)))
            current = quarterEnd
        }
        return quarters
    }
}
