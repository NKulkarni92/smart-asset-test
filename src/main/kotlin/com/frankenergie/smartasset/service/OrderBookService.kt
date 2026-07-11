package com.frankenergie.smartasset.service

import com.frankenergie.smartasset.domain.OrderBook
import com.frankenergie.smartasset.model.DeliveryPeriod
import com.frankenergie.smartasset.model.MarketOverviewResponse
import com.frankenergie.smartasset.model.OrderUpdateRequest
import com.frankenergie.smartasset.model.OrderUpdateResponse
import com.frankenergie.smartasset.model.QuarterOverview
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class OrderBookService(
    private val chargingOptimizerService: ChargingOptimizerService
) {

    private val orderBook = OrderBook()

    fun processOrder(request: OrderUpdateRequest): OrderUpdateResponse {
        val orderId = UUID.randomUUID().toString()

        val period = DeliveryPeriod(request.deliveryStartTime, request.deliveryEndTime)
        val matchedQuantity = orderBook.processOrder(period, request.orderSide, request.quantity, request.price)

        val status = when {
            matchedQuantity.compareTo(request.quantity) == 0 -> "FILLED"
            matchedQuantity > BigDecimal.ZERO -> "PARTIALLY_FILLED"
            else -> "ACCEPTED"
        }

        chargingOptimizerService.optimize(orderBook)

        return OrderUpdateResponse(orderId = orderId, status = status, timestamp = Instant.now())
    }

    fun getMarketOverview(): MarketOverviewResponse {
        val quarters = orderBook.getAllPeriods()
            .sortedBy { it.startTime }
            .map { period ->
                QuarterOverview(
                    deliveryStartTime = period.startTime,
                    deliveryEndTime = period.endTime,
                    highestBuyPrice = orderBook.getBestBid(period)?.price,
                    lowestSellPrice = orderBook.getBestAsk(period)?.price
                )
            }
        return MarketOverviewResponse(quarters)
    }

    fun getOrderBook(): OrderBook = orderBook
}