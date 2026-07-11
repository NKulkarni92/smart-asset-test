package com.frankenergie.smartasset.service

import com.frankenergie.smartasset.model.OrderSide
import com.frankenergie.smartasset.model.OrderUpdateRequest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class OrderBookServiceTest {

    private lateinit var service: OrderBookService
    private lateinit var purchaseTrackerService: PurchaseTrackerService
    private lateinit var marketOrderClient: com.frankenergie.smartasset.client.MarketOrderClient
    private lateinit var steeringSignalClient: com.frankenergie.smartasset.client.SteeringSignalClient
    private lateinit var chargingOptimizerService: ChargingOptimizerService

    private val testMarketOrdersPath = "test_obs_market_orders.log"
    private val testSteeringSignalsPath = "test_obs_steering_signals.log"

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path.of(testMarketOrdersPath))
        Files.deleteIfExists(Path.of(testSteeringSignalsPath))

        purchaseTrackerService = PurchaseTrackerService()
        marketOrderClient = com.frankenergie.smartasset.client.MarketOrderClient(testMarketOrdersPath)
        steeringSignalClient = com.frankenergie.smartasset.client.SteeringSignalClient(testSteeringSignalsPath)
        chargingOptimizerService = ChargingOptimizerService(
            emptyList(), steeringSignalClient, marketOrderClient, purchaseTrackerService
        )
        service = OrderBookService(chargingOptimizerService)
    }

    @Test
    fun `processOrder returns ACCEPTED when no match`() {
        val request = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            deliveryEndTime = LocalDateTime.of(2024, 1, 15, 10, 15),
            orderSide = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("50.00")
        )

        val response = service.processOrder(request)

        assertNotNull(response.orderId)
        assertEquals("ACCEPTED", response.status)
        assertNotNull(response.timestamp)
    }

    @Test
    fun `processOrder returns FILLED when fully matched`() {
        val sellRequest = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            deliveryEndTime = LocalDateTime.of(2024, 1, 15, 10, 15),
            orderSide = OrderSide.SELL,
            quantity = BigDecimal("10"),
            price = BigDecimal("50.00")
        )
        service.processOrder(sellRequest)

        val buyRequest = sellRequest.copy(orderSide = OrderSide.BUY)
        val response = service.processOrder(buyRequest)

        assertEquals("FILLED", response.status)
    }

    @Test
    fun `processOrder returns PARTIALLY_FILLED when partially matched`() {
        val sellRequest = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            deliveryEndTime = LocalDateTime.of(2024, 1, 15, 10, 15),
            orderSide = OrderSide.SELL,
            quantity = BigDecimal("5"),
            price = BigDecimal("50.00")
        )
        service.processOrder(sellRequest)

        val buyRequest = sellRequest.copy(orderSide = OrderSide.BUY, quantity = BigDecimal("10"))
        val response = service.processOrder(buyRequest)

        assertEquals("PARTIALLY_FILLED", response.status)
    }

    @Test
    fun `processOrder handles BigDecimal scale differences correctly`() {
        val sellRequest = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            deliveryEndTime = LocalDateTime.of(2024, 1, 15, 10, 15),
            orderSide = OrderSide.SELL,
            quantity = BigDecimal("10.00"),
            price = BigDecimal("50.00")
        )
        service.processOrder(sellRequest)

        val buyRequest = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            deliveryEndTime = LocalDateTime.of(2024, 1, 15, 10, 15),
            orderSide = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("50.00")
        )
        val response = service.processOrder(buyRequest)

        assertEquals("FILLED", response.status)
    }
}