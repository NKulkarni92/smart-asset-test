package com.frankenergie.smartasset.service

import com.frankenergie.smartasset.domain.OrderBook
import com.frankenergie.smartasset.model.ChargingGroup
import com.frankenergie.smartasset.model.DeliveryPeriod
import com.frankenergie.smartasset.model.OrderSide
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class ChargingOptimizerServiceTest {

    private lateinit var optimizer: ChargingOptimizerService
    private lateinit var orderBook: OrderBook
    private lateinit var steeringSignalClient: com.frankenergie.smartasset.client.SteeringSignalClient
    private val testFilePath = "test_steering_signals_setup.log"

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path.of(testFilePath))
        steeringSignalClient = com.frankenergie.smartasset.client.SteeringSignalClient(testFilePath)

        val group = ChargingGroup(
            id = "A",
            startTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            endTime = LocalDateTime.of(2024, 1, 15, 11, 0),
            neededChargeMWh = BigDecimal("1"),
            maxPowerMW = BigDecimal("2")
        )

        optimizer = ChargingOptimizerService(listOf(group), steeringSignalClient)
        orderBook = OrderBook()
    }

    @Test
    fun `optimize returns empty allocations when no sell orders`() {
        val plan = optimizer.optimizePlan(orderBook)

        assertEquals(1, plan.groupAllocations.size)
        assertTrue(plan.groupAllocations[0].allocations.isEmpty())
        assertEquals(BigDecimal.ZERO, plan.totalCost)
    }

    @Test
    fun `optimize allocates cheapest quarters first`() {
        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))
        val q3 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 30), LocalDateTime.of(2024, 1, 15, 10, 45))

        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("60.00"))
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("40.00"))
        orderBook.processOrder(q3, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))

        val plan = optimizer.optimizePlan(orderBook)

        val allocation = plan.groupAllocations[0]
        assertEquals(2, allocation.allocations.size)
        assertEquals(BigDecimal("40.00"), allocation.allocations[0].pricePerMWh)
    }

    @Test
    fun `optimize respects max power constraint`() {
        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))

        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))

        val plan = optimizer.optimizePlan(orderBook)

        val allocation = plan.groupAllocations[0]
        assertEquals(2, allocation.allocations.size)
        assertEquals(BigDecimal("0.50"), allocation.allocations[0].mwh)
        assertEquals(BigDecimal("0.50"), allocation.allocations[1].mwh)
    }

    @Test
    fun `optimize accounts for already charged energy`() {
        val group = ChargingGroup(
            id = "A",
            startTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            endTime = LocalDateTime.of(2024, 1, 15, 10, 30),
            neededChargeMWh = BigDecimal("1"),
            maxPowerMW = BigDecimal("2")
        )

        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))

        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))

        val chargedSoFar = mapOf("A" to BigDecimal("0.50"))
        val plan = optimizer.optimizePlan(orderBook, chargedSoFar)

        val allocation = plan.groupAllocations[0]
        assertEquals(BigDecimal("0.50"), allocation.totalMWh)
    }

    @Test
    fun `optimize calculates total cost correctly`() {
        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))

        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("40.00"))
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("60.00"))

        val plan = optimizer.optimizePlan(orderBook)

        // need 0.5 MWh per quarter, 1 MWh total
        // q1: 0.5 MWh @ 40 = 20 EUR
        // q2: 0.5 MWh @ 60 = 30 EUR
        // total = 50 EUR
        assertEquals(0, BigDecimal("50.00").compareTo(plan.totalCost))
    }

    @Test
    fun `emitNewSignals appends only newly seen steering signals`() {
        val testFilePath = "test_steering_signals.log"
        Files.deleteIfExists(Path.of(testFilePath))

        val signalClient = com.frankenergie.smartasset.client.SteeringSignalClient(testFilePath)
        val group = ChargingGroup(
            id = "A",
            startTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            endTime = LocalDateTime.of(2024, 1, 15, 10, 15),
            neededChargeMWh = BigDecimal("1"),
            maxPowerMW = BigDecimal("4")
        )
        val emittingOptimizer = ChargingOptimizerService(listOf(group), signalClient)
        val period = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))

        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("40.00"))

        emittingOptimizer.optimize(orderBook)

        assertEquals(1, signalClient.getAllSignals().size)

        Files.deleteIfExists(Path.of(testFilePath))
    }
}
