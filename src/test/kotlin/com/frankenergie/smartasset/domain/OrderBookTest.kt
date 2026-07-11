package com.frankenergie.smartasset.domain

import com.frankenergie.smartasset.model.DeliveryPeriod
import com.frankenergie.smartasset.model.OrderSide
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class OrderBookTest {

    private lateinit var orderBook: OrderBook
    private lateinit var period: DeliveryPeriod

    @BeforeEach
    fun setUp() {
        orderBook = OrderBook()
        period = DeliveryPeriod(
            LocalDateTime.of(2024, 1, 15, 10, 0),
            LocalDateTime.of(2024, 1, 15, 10, 15)
        )
    }

    @Test
    fun `buy order added to empty book`() {
        val matched = orderBook.processOrder(period, OrderSide.BUY, BigDecimal("10"), BigDecimal("50.00"))

        assertEquals(BigDecimal.ZERO, matched)
        assertEquals(BigDecimal("10"), orderBook.getBids(period)[BigDecimal("50.00")])
        assertTrue(orderBook.getAsks(period).isEmpty())
    }

    @Test
    fun `sell order added to empty book`() {
        val matched = orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("55.00"))

        assertEquals(BigDecimal.ZERO, matched)
        assertEquals(BigDecimal("10"), orderBook.getAsks(period)[BigDecimal("55.00")])
        assertTrue(orderBook.getBids(period).isEmpty())
    }

    @Test
    fun `buy order does not match higher priced sell order`() {
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("55.00"))
        val matched = orderBook.processOrder(period, OrderSide.BUY, BigDecimal("5"), BigDecimal("50.00"))

        assertEquals(BigDecimal.ZERO, matched)
        assertEquals(BigDecimal("10"), orderBook.getAsks(period)[BigDecimal("55.00")])
        assertEquals(BigDecimal("5"), orderBook.getBids(period)[BigDecimal("50.00")])
    }

    @Test
    fun `buy order fully matches equal priced sell order`() {
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))
        val matched = orderBook.processOrder(period, OrderSide.BUY, BigDecimal("10"), BigDecimal("50.00"))

        assertEquals(BigDecimal("10"), matched)
        assertTrue(orderBook.getAsks(period).isEmpty())
        assertTrue(orderBook.getBids(period).isEmpty())
    }

    @Test
    fun `buy order fully matches lower priced sell order`() {
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("48.00"))
        val matched = orderBook.processOrder(period, OrderSide.BUY, BigDecimal("10"), BigDecimal("50.00"))

        assertEquals(BigDecimal("10"), matched)
        assertTrue(orderBook.getAsks(period).isEmpty())
        assertTrue(orderBook.getBids(period).isEmpty())
    }

    @Test
    fun `buy order partially matches sell order - remaining added to bids`() {
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))
        val matched = orderBook.processOrder(period, OrderSide.BUY, BigDecimal("15"), BigDecimal("50.00"))

        assertEquals(BigDecimal("10"), matched)
        assertTrue(orderBook.getAsks(period).isEmpty())
        assertEquals(BigDecimal("5"), orderBook.getBids(period)[BigDecimal("50.00")])
    }

    @Test
    fun `buy order partially fills sell order - remaining stays in asks`() {
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("15"), BigDecimal("50.00"))
        val matched = orderBook.processOrder(period, OrderSide.BUY, BigDecimal("10"), BigDecimal("50.00"))

        assertEquals(BigDecimal("10"), matched)
        assertEquals(BigDecimal("5"), orderBook.getAsks(period)[BigDecimal("50.00")])
        assertTrue(orderBook.getBids(period).isEmpty())
    }

    @Test
    fun `sell order fully matches higher priced buy order`() {
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("10"), BigDecimal("55.00"))
        val matched = orderBook.processOrder(period, OrderSide.SELL, BigDecimal("10"), BigDecimal("50.00"))

        assertEquals(BigDecimal("10"), matched)
        assertTrue(orderBook.getBids(period).isEmpty())
        assertTrue(orderBook.getAsks(period).isEmpty())
    }

    @Test
    fun `sell order does not match lower priced buy order`() {
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("10"), BigDecimal("50.00"))
        val matched = orderBook.processOrder(period, OrderSide.SELL, BigDecimal("5"), BigDecimal("55.00"))

        assertEquals(BigDecimal.ZERO, matched)
        assertEquals(BigDecimal("10"), orderBook.getBids(period)[BigDecimal("50.00")])
        assertEquals(BigDecimal("5"), orderBook.getAsks(period)[BigDecimal("55.00")])
    }

    @Test
    fun `buy order matches multiple price levels`() {
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("5"), BigDecimal("48.00"))
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("5"), BigDecimal("49.00"))
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("5"), BigDecimal("52.00"))

        val matched = orderBook.processOrder(period, OrderSide.BUY, BigDecimal("12"), BigDecimal("50.00"))

        assertEquals(BigDecimal("10"), matched)
        assertEquals(BigDecimal("2"), orderBook.getBids(period)[BigDecimal("50.00")])
        assertEquals(BigDecimal("5"), orderBook.getAsks(period)[BigDecimal("52.00")])
    }

    @Test
    fun `sell order matches multiple price levels`() {
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("5"), BigDecimal("52.00"))
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("5"), BigDecimal("51.00"))
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("5"), BigDecimal("48.00"))

        val matched = orderBook.processOrder(period, OrderSide.SELL, BigDecimal("12"), BigDecimal("50.00"))

        assertEquals(BigDecimal("10"), matched)
        assertEquals(BigDecimal("2"), orderBook.getAsks(period)[BigDecimal("50.00")])
        assertEquals(BigDecimal("5"), orderBook.getBids(period)[BigDecimal("48.00")])
    }

    @Test
    fun `orders aggregate at same price level`() {
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("5"), BigDecimal("50.00"))
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("3"), BigDecimal("50.00"))

        assertEquals(BigDecimal("8"), orderBook.getBids(period)[BigDecimal("50.00")])
    }

    @Test
    fun `separate order books per delivery period`() {
        val period1 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 0), LocalDateTime.of(2024, 1, 15, 10, 15))
        val period2 = DeliveryPeriod(LocalDateTime.of(2024, 1, 15, 10, 15), LocalDateTime.of(2024, 1, 15, 10, 30))

        orderBook.processOrder(period1, OrderSide.BUY, BigDecimal("10"), BigDecimal("50.00"))
        orderBook.processOrder(period2, OrderSide.BUY, BigDecimal("20"), BigDecimal("55.00"))

        assertEquals(BigDecimal("10"), orderBook.getBids(period1)[BigDecimal("50.00")])
        assertEquals(BigDecimal("20"), orderBook.getBids(period2)[BigDecimal("55.00")])
    }

    @Test
    fun `getBestBid returns highest bid`() {
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("5"), BigDecimal("50.00"))
        orderBook.processOrder(period, OrderSide.BUY, BigDecimal("3"), BigDecimal("52.00"))

        val best = orderBook.getBestBid(period)!!
        assertEquals(BigDecimal("52.00"), best.price)
        assertEquals(BigDecimal("3"), best.quantity)
    }

    @Test
    fun `getBestAsk returns lowest ask`() {
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("5"), BigDecimal("55.00"))
        orderBook.processOrder(period, OrderSide.SELL, BigDecimal("3"), BigDecimal("53.00"))

        val best = orderBook.getBestAsk(period)!!
        assertEquals(BigDecimal("53.00"), best.price)
        assertEquals(BigDecimal("3"), best.quantity)
    }

    @Test
    fun `getBestBid returns null for empty book`() {
        assertNull(orderBook.getBestBid(period))
    }

    @Test
    fun `getBestAsk returns null for empty book`() {
        assertNull(orderBook.getBestAsk(period))
    }
}
