package com.frankenergie.smartasset.client

import com.frankenergie.smartasset.model.MarketOrder
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Component
class MarketOrderClient(
    @Value("\${market.orders.file:log/market_orders.log}") private val filePath: String
) {
    private val file: Path = Path.of(filePath)

    fun sendOrder(order: MarketOrder) {
        ensureFileExists()
        val line = formatOrder(order)
        Files.writeString(file, "$line\n", StandardOpenOption.APPEND)
    }

    fun getAllOrders(): List<String> {
        if (!Files.exists(file)) return emptyList()
        return Files.readAllLines(file).filter { it.isNotBlank() }
    }

    fun clear() {
        if (Files.exists(file)) {
            Files.writeString(file, "")
        }
    }

    private fun ensureFileExists() {
        if (!Files.exists(file)) {
            file.parent?.let { Files.createDirectories(it) }
            Files.createFile(file)
        }
    }

    private fun formatOrder(order: MarketOrder): String {
        return "${order.timestamp}|${order.orderId}|${order.groupId}|${order.deliveryStart}|${order.deliveryEnd}|${order.side}|${order.quantity}|${order.price}"
    }
}
