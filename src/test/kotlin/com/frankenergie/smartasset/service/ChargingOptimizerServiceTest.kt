package com.frankenergie.smartasset.service

import com.frankenergie.smartasset.client.MarketOrderClient
import com.frankenergie.smartasset.client.SteeringSignalClient
import com.frankenergie.smartasset.domain.OrderBook
import com.frankenergie.smartasset.model.ChargingGroup
import com.frankenergie.smartasset.model.DeliveryPeriod
import com.frankenergie.smartasset.model.OrderSide
import org.junit.jupiter.api.AfterEach
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
    private lateinit var steeringSignalClient: SteeringSignalClient
    private lateinit var marketOrderClient: MarketOrderClient
    private lateinit var purchaseTracker: PurchaseTrackerService

    private val steeringFilePath = "test_opt_steering_signals.log"
    private val marketOrderFilePath = "test_opt_market_orders.log"

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path.of(steeringFilePath))
        Files.deleteIfExists(Path.of(marketOrderFilePath))
        steeringSignalClient = SteeringSignalClient(steeringFilePath)
        marketOrderClient = MarketOrderClient(marketOrderFilePath)
        purchaseTracker = PurchaseTrackerService()
        val group = ChargingGroup(id = "A", startTime = LocalDateTime.of(2024, 1, 15, 10, 0), endTime = LocalDateTime.of(2024, 1, 15, 11, 0), neededChargeMWh = BigDecimal("1"), maxPowerMW = BigDecimal("2"))
        optimizer = ChargingOptimizerService(listOf(group), steeringSignalClient, marketOrderClient, purchaseTracker)
        orderBook = OrderBook()
    }

    @AfterEach
    fun tearDown() {
        Files.deleteIfExists(Path.of(steeringFilePath))
        Files.deleteIfExists(Path.of(marketOrderFilePath))
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
        val group = ChargingGroup(id = "A", startTime = LocalDateTime.of(2024, 1, 15, 10, 0), endTime = LocalDateTime.of(2024, 1, 15, 10, 30), neededChargeMWh = BigDecimal("1"), maxPowerMW = BigDecimal("2"))
        val localOptimizer = ChargingOptimizerService(listOf(group), steeringSignalClient, marketOrderClient, purchaseTracker)
        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))
        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))
        val chargedSoFar = mapOf("A" to BigDecimal("0.50"))
        val plan = localOptimizer.optimizePlan(orderBook, chargedSoFar)
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
        assertEquals(0, BigDecimal("50.00").compareTo(plan.totalCost))
    }

    @Test
    fun `optimize places buy orders and records purchases`() {
        val period = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("40.00"))
        optimizer.optimize(orderBook)
        val orders = marketOrderClient.getAllOrders()
        assertTrue(orders.isNotEmpty())
        assertTrue(orders.any { it.contains("BUY") })
        val summary = purchaseTracker.getSummary()
        assertTrue(summary.totalMWh > BigDecimal.ZERO)
    }

    @Test
    fun `optimize emits sell orders when allocation is dropped`() {
        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))
        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("60.00"))
        optimizer.optimize(orderBook)
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("30.00"))
        optimizer.optimize(orderBook)
        val orders = marketOrderClient.getAllOrders()
        assertTrue(orders.any { it.contains("SELL") })
    }

    @Test
    fun `optimize emits steering signals`() {
        val period = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("40.00"))
        optimizer.optimize(orderBook)
        val signals = steeringSignalClient.getAllSignals()
        assertEquals(1, signals.size)
        assertTrue(signals[0].contains("A"))
    }

    @Test
    fun `optimize uses chargedSoFar from purchase tracker`() {
        val group = ChargingGroup(id = "A", startTime = LocalDateTime.of(2024, 1, 15, 10, 0), endTime = LocalDateTime.of(2024, 1, 15, 10, 30), neededChargeMWh = BigDecimal("1"), maxPowerMW = BigDecimal("4"))
        val localOptimizer = ChargingOptimizerService(listOf(group), steeringSignalClient, marketOrderClient, purchaseTracker)
        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))
        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("40.00"))
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))
        localOptimizer.optimize(orderBook)
        val firstSummary = purchaseTracker.getSummary()
        assertEquals(0, BigDecimal("1").compareTo(firstSummary.totalMWh))
    }

    @Test
    fun `optimize respects available supply across groups`() {
        val group1 = ChargingGroup("X", LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15), BigDecimal("5"), BigDecimal("20"))
        val group2 = ChargingGroup("Y", LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15), BigDecimal("5"), BigDecimal("20"))
        val sharedOptimizer = ChargingOptimizerService(listOf(group1, group2), steeringSignalClient, marketOrderClient, purchaseTracker)
        val period = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("3"), BigDecimal("40.00"))
        val plan = sharedOptimizer.optimizePlan(orderBook)
        val totalAllocated = plan.groupAllocations.sumOf { it.totalMWh }
        assertTrue(totalAllocated.compareTo(BigDecimal("3")) <= 0)
    }

    @Test
    fun `optimize only emits delta steering signals`() {
        val period = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("40.00"))
        optimizer.optimize(orderBook)
        val signalsAfterFirst = steeringSignalClient.getAllSignals().size
        optimizer.optimize(orderBook)
        val signalsAfterSecond = steeringSignalClient.getAllSignals().size
        assertEquals(signalsAfterFirst, signalsAfterSecond)
    }

    @Test
    fun `optimize reverses purchase when allocation is dropped`() {
        val q1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val q2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))
        orderBook.processOrder(q1, OrderSide.SELL, BigDecimal("10"), BigDecimal("60.00"))
        optimizer.optimize(orderBook)
        val mwhAfterFirst = purchaseTracker.getSummary().totalMWh
        orderBook.processOrder(q2, OrderSide.SELL, BigDecimal("10"), BigDecimal("30.00"))
        optimizer.optimize(orderBook)
        val mwhAfterSecond = purchaseTracker.getSummary().totalMWh
        assertTrue(mwhAfterSecond.compareTo(mwhAfterFirst) <= 0)
    }
}