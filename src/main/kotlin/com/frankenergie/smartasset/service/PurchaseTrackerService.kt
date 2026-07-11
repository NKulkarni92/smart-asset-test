package com.frankenergie.smartasset.service

import com.frankenergie.smartasset.model.ExecutedPurchase
import com.frankenergie.smartasset.model.PurchaseSummary
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.concurrent.ConcurrentLinkedQueue

@Service
class PurchaseTrackerService {

    private val purchases: ConcurrentLinkedQueue<ExecutedPurchase> = ConcurrentLinkedQueue()

    fun recordPurchase(purchase: ExecutedPurchase) {
        purchases.add(purchase)
    }

    fun getAllPurchases(): List<ExecutedPurchase> = purchases.toList()

    fun getSummary(): PurchaseSummary {
        val allPurchases = purchases.toList()
        val totalMWh = allPurchases.sumOf { it.quantity }
        val totalCost = allPurchases.sumOf { it.totalCost }
        val averagePrice = if (totalMWh > BigDecimal.ZERO) {
            totalCost.divide(totalMWh, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return PurchaseSummary(totalMWh, totalCost, averagePrice)
    }

    fun clear() {
        purchases.clear()
    }
}
