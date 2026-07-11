package com.frankenergie.smartasset.controller

import com.frankenergie.smartasset.model.MarketOverviewResponse
import com.frankenergie.smartasset.model.OrderUpdateRequest
import com.frankenergie.smartasset.model.OrderUpdateResponse
import com.frankenergie.smartasset.service.OrderBookService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class MarketOverviewController(private val orderBookService: OrderBookService) {
    @GetMapping("/market/overview")
    fun getMarketOverview(): ResponseEntity<MarketOverviewResponse> {
        val response = orderBookService.getMarketOverview()
        return ResponseEntity.ok(response)
    }
}
