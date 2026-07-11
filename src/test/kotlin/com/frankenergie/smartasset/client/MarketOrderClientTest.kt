package com.frankenergie.smartasset.client

import com.frankenergie.smartasset.model.MarketOrder
import com.frankenergie.smartasset.model.OrderSide
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class MarketOrderClientTest {

    private val testFilePath = "./test_market_orders.log"
    private lateinit var client: MarketOrderClient

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path.of(testFilePath))
        client = MarketOrderClient(testFilePath)
    }

    @AfterEach
    fun tearDown() {
        Files.deleteIfExists(Path.of(testFilePath))
    }

    @Test
    fun `sendOrder writes order to file`() {
        val order = MarketOrder(
            orderId = "order-123",
            groupId = "A",
            deliveryStart = LocalDateTime.of(2024, 1, 15, 10, 0),
            deliveryEnd = LocalDateTime.of(2024, 1, 15, 10, 15),
            side = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("50.00")
        )

        client.sendOrder(order)

        val lines = client.getAllOrders()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("order-123"))
        assertTrue(lines[0].contains("A"))
        assertTrue(lines[0].contains("BUY"))
    }

    @Test
    fun `multiple orders written on separate lines`() {
        val order1 = MarketOrder(
            "order-1",
            "A",
            LocalDateTime.of(2024, 1, 15, 10, 0),
            LocalDateTime.of(2024, 1, 15, 10, 15),
            OrderSide.BUY,
            BigDecimal("10"),
            BigDecimal("50.00")
        )
        val order2 = MarketOrder(
            "order-2",
            "B",
            LocalDateTime.of(2024, 1, 15, 10, 15),
            LocalDateTime.of(2024, 1, 15, 10, 30),
            OrderSide.SELL,
            BigDecimal("5"),
            BigDecimal("55.00")
        )

        client.sendOrder(order1)
        client.sendOrder(order2)

        val lines = client.getAllOrders()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("order-1"))
        assertTrue(lines[0].contains("BUY"))
        assertTrue(lines[1].contains("order-2"))
        assertTrue(lines[1].contains("SELL"))
    }

    @Test
    fun `clear removes all orders`() {
        val order = MarketOrder(
            "order-1",
            "A",
            LocalDateTime.of(2024, 1, 15, 10, 0),
            LocalDateTime.of(2024, 1, 15, 10, 15),
            OrderSide.BUY,
            BigDecimal("10"),
            BigDecimal("50.00")
        )
        client.sendOrder(order)

        client.clear()

        assertTrue(client.getAllOrders().isEmpty())
    }

    @Test
    fun `tracks both buy and sell orders`() {
        val buyOrder = MarketOrder(
            "buy-1",
            "A",
            LocalDateTime.of(2024, 1, 15, 10, 0),
            LocalDateTime.of(2024, 1, 15, 10, 15),
            OrderSide.BUY,
            BigDecimal("10"),
            BigDecimal("50.00")
        )
        val sellOrder = MarketOrder(
            "sell-1",
            "A",
            LocalDateTime.of(2024, 1, 15, 10, 0),
            LocalDateTime.of(2024, 1, 15, 10, 15),
            OrderSide.SELL,
            BigDecimal("10"),
            BigDecimal("55.00")
        )

        client.sendOrder(buyOrder)
        client.sendOrder(sellOrder)

        val lines = client.getAllOrders()
        assertEquals(2, lines.size)
        assertTrue(lines.any { it.contains("BUY") })
        assertTrue(lines.any { it.contains("SELL") })
    }
}
