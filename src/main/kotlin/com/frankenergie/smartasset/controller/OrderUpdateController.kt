package com.frankenergie.smartasset.controller

import com.frankenergie.smartasset.model.OrderUpdateRequest
import com.frankenergie.smartasset.model.OrderUpdateResponse
import com.frankenergie.smartasset.model.PurchaseSummary
import com.frankenergie.smartasset.service.ChargingOptimizerService
import com.frankenergie.smartasset.service.OrderBookService
import com.frankenergie.smartasset.service.PurchaseTrackerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class OrderUpdateController(
    private val orderBookService: OrderBookService,
    private val purchaseTrackerService: PurchaseTrackerService,
    private val chargingOptimizerService: ChargingOptimizerService
) {

    @PostMapping("/orderupdate")
    fun orderUpdate(@RequestBody request: OrderUpdateRequest): ResponseEntity<OrderUpdateResponse> {
        val response = orderBookService.processOrder(request)
        chargingOptimizerService.optimize(orderBookService.getOrderBook())
        return ResponseEntity.ok(response)
    }

    @GetMapping("/purchases/average")
    fun getPurchaseAverage(): ResponseEntity<PurchaseSummary> {
        val summary = purchaseTrackerService.getSummary()
        return ResponseEntity.ok(summary)
    }
}
