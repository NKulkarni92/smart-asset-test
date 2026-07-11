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
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
class OrderUpdateControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Test
    fun `POST orderupdate returns ACCEPTED for new order`() {
        val request = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 1, 15, 10, 0),
            deliveryEndTime = LocalDateTime.of(2024, 1, 15, 10, 15),
            orderSide = OrderSide.BUY,
            quantity = BigDecimal("10"),
            price = BigDecimal("50.00")
        )

        mockMvc.post("/api/orderupdate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(request)
        }.andExpect {
            status { isOk() }
            jsonPath("$.order_id") { exists() }
            jsonPath("$.status") { value("ACCEPTED") }
            jsonPath("$.timestamp") { exists() }
        }
    }

    @Test
    fun `POST orderupdate returns FILLED when orders match`() {
        val sellRequest = OrderUpdateRequest(
            deliveryStartTime = LocalDateTime.of(2024, 1, 15, 11, 0),
            deliveryEndTime = LocalDateTime.of(2024, 1, 15, 11, 15),
            orderSide = OrderSide.SELL,
            quantity = BigDecimal("10"),
            price = BigDecimal("50.00")
        )

        val buyRequest = sellRequest.copy(orderSide = OrderSide.BUY)

        // First, add sell order
        mockMvc.post("/api/orderupdate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(sellRequest)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("ACCEPTED") }
        }

        // Then, add matching buy order
        mockMvc.post("/api/orderupdate") {
            contentType = MediaType.APPLICATION_JSON
            content = objectMapper.writeValueAsString(buyRequest)
        }.andExpect {
            status { isOk() }
            jsonPath("$.status") { value("FILLED") }
        }
    }
}
