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

        return when (side) {
            OrderSide.BUY -> processBuyOrder(bids, asks, quantity, price)
            OrderSide.SELL -> processSellOrder(bids, asks, quantity, price)
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

    fun getBids(period: DeliveryPeriod): Map<BigDecimal, BigDecimal> = bidsPerPeriod[period]?.toMap() ?: emptyMap()

    fun getAsks(period: DeliveryPeriod): Map<BigDecimal, BigDecimal> = asksPerPeriod[period]?.toMap() ?: emptyMap()

    fun getBestBid(period: DeliveryPeriod): PriceLevel? =
        bidsPerPeriod[period]?.firstEntry()?.let { PriceLevel(it.key, it.value) }

    fun getBestAsk(period: DeliveryPeriod): PriceLevel? =
        asksPerPeriod[period]?.firstEntry()?.let { PriceLevel(it.key, it.value) }

    fun getAllPeriods(): Set<DeliveryPeriod> = bidsPerPeriod.keys + asksPerPeriod.keys
}
