package com.frankenergie.smartasset.service

import com.frankenergie.smartasset.model.ExecutedPurchase
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class PurchaseTrackerServiceTest {

    private lateinit var tracker: PurchaseTrackerService

    @BeforeEach
    fun setUp() {
        tracker = PurchaseTrackerService()
    }

    @Test
    fun `getSummary returns zeros when no purchases`() {
        val summary = tracker.getSummary()

        assertEquals(BigDecimal.ZERO, summary.totalMWh)
        assertEquals(BigDecimal.ZERO, summary.totalCost)
        assertEquals(BigDecimal.ZERO, summary.averagePricePerMWh)
    }

    @Test
    fun `getSummary calculates average price correctly`() {
        tracker.recordPurchase(
            ExecutedPurchase(
                groupId = "A",
                deliveryStart = LocalDateTime.of(2024, 1, 15, 10, 0),
                deliveryEnd = LocalDateTime.of(2024, 1, 15, 10, 15),
                quantity = BigDecimal("10"),
                pricePerMWh = BigDecimal("50.00")
            )
        )
        tracker.recordPurchase(
            ExecutedPurchase(
                groupId = "B",
                deliveryStart = LocalDateTime.of(2024, 1, 15, 10, 15),
                deliveryEnd = LocalDateTime.of(2024, 1, 15, 10, 30),
                quantity = BigDecimal("10"),
                pricePerMWh = BigDecimal("60.00")
            )
        )

        val summary = tracker.getSummary()

        assertEquals(BigDecimal("20"), summary.totalMWh)
        assertEquals(BigDecimal("1100.00"), summary.totalCost)
        assertEquals(BigDecimal("55.00"), summary.averagePricePerMWh)
    }

    @Test
    fun `getSummary calculates weighted average correctly`() {
        tracker.recordPurchase(
            ExecutedPurchase(
                groupId = "A",
                deliveryStart = LocalDateTime.of(2024, 1, 15, 10, 0),
                deliveryEnd = LocalDateTime.of(2024, 1, 15, 10, 15),
                quantity = BigDecimal("5"),
                pricePerMWh = BigDecimal("40.00")
            )
        )
        tracker.recordPurchase(
            ExecutedPurchase(
                groupId = "A",
                deliveryStart = LocalDateTime.of(2024, 1, 15, 10, 15),
                deliveryEnd = LocalDateTime.of(2024, 1, 15, 10, 30),
                quantity = BigDecimal("15"),
                pricePerMWh = BigDecimal("60.00")
            )
        )

        val summary = tracker.getSummary()

        // 5 * 40 + 15 * 60 = 200 + 900 = 1100
        // 1100 / 20 = 55
        assertEquals(BigDecimal("20"), summary.totalMWh)
        assertEquals(BigDecimal("1100.00"), summary.totalCost)
        assertEquals(BigDecimal("55.00"), summary.averagePricePerMWh)
    }

    @Test
    fun `getAllPurchases returns all recorded purchases`() {
        tracker.recordPurchase(
            ExecutedPurchase(
                "A",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                LocalDateTime.of(2024, 1, 15, 10, 15),
                BigDecimal("10"),
                BigDecimal("50.00")
            )
        )
        tracker.recordPurchase(
            ExecutedPurchase(
                "B",
                LocalDateTime.of(2024, 1, 15, 10, 15),
                LocalDateTime.of(2024, 1, 15, 10, 30),
                BigDecimal("5"),
                BigDecimal("55.00")
            )
        )

        val purchases = tracker.getAllPurchases()

        assertEquals(2, purchases.size)
    }

    @Test
    fun `clear removes all purchases`() {
        tracker.recordPurchase(
            ExecutedPurchase(
                "A",
                LocalDateTime.of(2024, 1, 15, 10, 0),
                LocalDateTime.of(2024, 1, 15, 10, 15),
                BigDecimal("10"),
                BigDecimal("50.00")
            )
        )

        tracker.clear()

        assertTrue(tracker.getAllPurchases().isEmpty())
        assertEquals(BigDecimal.ZERO, tracker.getSummary().totalMWh)
    }
}
