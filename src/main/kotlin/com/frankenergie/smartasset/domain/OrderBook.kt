package com.frankenergie.smartasset.domain

import com.frankenergie.smartasset.model.DeliveryPeriod
import com.frankenergie.smartasset.model.OrderSide
import java.math.BigDecimal
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap

data class PriceLevel(
    val price: BigDecimal,
    val quantity: BigDecimal
)

class OrderBook {

    private val bidsPerPeriod: ConcurrentHashMap<DeliveryPeriod, TreeMap<BigDecimal, BigDecimal>> = ConcurrentHashMap()
    private val asksPerPeriod: ConcurrentHashMap<DeliveryPeriod, TreeMap<BigDecimal, BigDecimal>> = ConcurrentHashMap()

    fun processOrder(period: DeliveryPeriod, side: OrderSide, quantity: BigDecimal, price: BigDecimal): BigDecimal {
        val bids = bidsPerPeriod.computeIfAbsent(period) { TreeMap(Comparator.reverseOrder()) }
        val asks = asksPerPeriod.computeIfAbsent(period) { TreeMap() }

        return synchronized(bids) {
            synchronized(asks) {
                when (side) {
                    OrderSide.BUY -> processBuyOrder(bids, asks, quantity, price)
                    OrderSide.SELL -> processSellOrder(bids, asks, quantity, price)
                }
            }
        }
    }

    private fun processBuyOrder(
        bids: TreeMap<BigDecimal, BigDecimal>,
        asks: TreeMap<BigDecimal, BigDecimal>,
        quantity: BigDecimal,
        price: BigDecimal
    ): BigDecimal {
        var remaining = quantity

        val iterator = asks.entries.iterator()
        while (iterator.hasNext() && remaining > BigDecimal.ZERO) {
            val (askPrice, askQty) = iterator.next()
            if (askPrice > price) break

            val matched = remaining.min(askQty)
            remaining -= matched
            val newQty = askQty - matched
            if (newQty <= BigDecimal.ZERO) iterator.remove() else asks[askPrice] = newQty
        }

        if (remaining > BigDecimal.ZERO) {
            bids.merge(price, remaining) { old, new -> old + new }
        }

        return quantity - remaining
    }

    private fun processSellOrder(
        bids: TreeMap<BigDecimal, BigDecimal>,
        asks: TreeMap<BigDecimal, BigDecimal>,
        quantity: BigDecimal,
        price: BigDecimal
    ): BigDecimal {
        var remaining = quantity

        val iterator = bids.entries.iterator()
        while (iterator.hasNext() && remaining > BigDecimal.ZERO) {
            val (bidPrice, bidQty) = iterator.next()
            if (bidPrice < price) break

            val matched = remaining.min(bidQty)
            remaining -= matched
            val newQty = bidQty - matched
            if (newQty <= BigDecimal.ZERO) iterator.remove() else bids[bidPrice] = newQty
        }

        if (remaining > BigDecimal.ZERO) {
            asks.merge(price, remaining) { old, new -> old + new }
        }

        return quantity - remaining
    }

    fun getBids(period: DeliveryPeriod): Map<BigDecimal, BigDecimal> {
        val bids = bidsPerPeriod[period] ?: return emptyMap()
        return synchronized(bids) { bids.toMap() }
    }

    fun getAsks(period: DeliveryPeriod): Map<BigDecimal, BigDecimal> {
        val asks = asksPerPeriod[period] ?: return emptyMap()
        return synchronized(asks) { asks.toMap() }
    }

    fun getBestBid(period: DeliveryPeriod): PriceLevel? {
        val bids = bidsPerPeriod[period] ?: return null
        return synchronized(bids) { bids.firstEntry()?.let { PriceLevel(it.key, it.value) } }
    }

    fun getBestAsk(period: DeliveryPeriod): PriceLevel? {
        val asks = asksPerPeriod[period] ?: return null
        return synchronized(asks) { asks.firstEntry()?.let { PriceLevel(it.key, it.value) } }
    }

    fun getAvailableSupply(period: DeliveryPeriod): BigDecimal {
        val asks = asksPerPeriod[period] ?: return BigDecimal.ZERO
        return synchronized(asks) { asks.values.fold(BigDecimal.ZERO) { acc, qty -> acc + qty } }
    }

    fun getAllPeriods(): Set<DeliveryPeriod> = bidsPerPeriod.keys + asksPerPeriod.keys
}