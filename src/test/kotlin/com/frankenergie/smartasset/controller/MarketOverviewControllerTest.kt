package com.frankenergie.smartasset.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.frankenergie.smartasset.model.OrderSide
import com.frankenergie.smartasset.model.OrderUpdateRequest
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class MarketOverviewControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `GET market overview returns empty list when no orders`() {
        mockMvc.get("/api/market/overview")
            .andExpect {
                status { isOk() }
                jsonPath("$.quarters") { isArray() }
            }
    }

    @Test
    fun `GET market overview returns quarters with best prices`() {
        // Add a buy order
        val buyRequest = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 6, 15, 10, 0),
            deliveryEndTime = LocalDateTime.of(2024, 6, 15, 10, 15),
            orderSide = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("50.00")
        )
        mockMvc.post("/api/orderupdate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(buyRequest)
        }

        // Add a higher buy order for same quarter
        val higherBuyRequest = buyRequest.copy(price = BigDecimal("52.00"))
        mockMvc.post("/api/orderupdate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(higherBuyRequest)
        }

        // Add a sell order
        val sellRequest = buyRequest.copy(orderSide = OrderSide.SELL, price = BigDecimal("55.00"))
        mockMvc.post("/api/orderupdate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(sellRequest)
        }

        // Add a lower sell order
        val lowerSellRequest = buyRequest.copy(orderSide = OrderSide.SELL, price = BigDecimal("53.00"))
        mockMvc.post("/api/orderupdate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(lowerSellRequest)
        }

        mockMvc.get("/api/market/overview")
            .andExpect {
                status { isOk() }
                jsonPath("$.quarters.length()") { value(1) }
                jsonPath("$.quarters[0].delivery_start_time") { exists() }
                jsonPath("$.quarters[0].delivery_end_time") { exists() }
                jsonPath("$.quarters[0].highest_buy_price") { value(52.00) }
                jsonPath("$.quarters[0].lowest_sell_price") { value(53.00) }
            }
    }
}
