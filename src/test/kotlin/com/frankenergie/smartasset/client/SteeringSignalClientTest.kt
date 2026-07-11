package com.frankenergie.smartasset.client

import com.frankenergie.smartasset.model.SteeringSignal
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

class SteeringSignalClientTest {

    private val testFilePath = "./test_steering_signals.log"
    private lateinit var client: SteeringSignalClient

    @BeforeEach
    fun setUp() {
        Files.deleteIfExists(Path.of(testFilePath))
        client = SteeringSignalClient(testFilePath)
    }

    @AfterEach
    fun tearDown() {
        Files.deleteIfExists(Path.of(testFilePath))
    }

    @Test
    fun `sendSignal writes signal to file`() {
        val signal = SteeringSignal(
            groupId = "A",
            quarterStart = LocalDateTime.of(2024, 1, 15, 10, 0),
            quarterEnd = LocalDateTime.of(2024, 1, 15, 10, 15),
            chargePowerMW = BigDecimal("2.0")
        )

        client.sendSignal(signal)

        val lines = client.getAllSignals()
        assertEquals(1, lines.size)
        assertTrue(lines[0].contains("A"))
        assertTrue(lines[0].contains("2.0"))
    }

    @Test
    fun `multiple signals written on separate lines`() {
        val signal1 = SteeringSignal(
            "A",
            LocalDateTime.of(2024, 1, 15, 10, 0),
            LocalDateTime.of(2024, 1, 15, 10, 15),
            BigDecimal("2.0")
        )
        val signal2 = SteeringSignal(
            "B",
            LocalDateTime.of(2024, 1, 15, 10, 15),
            LocalDateTime.of(2024, 1, 15, 10, 30),
            BigDecimal("3.0")
        )

        client.sendSignal(signal1)
        client.sendSignal(signal2)

        val lines = client.getAllSignals()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("A"))
        assertTrue(lines[1].contains("B"))
    }

    @Test
    fun `clear removes all signals`() {
        val signal = SteeringSignal(
            "A",
            LocalDateTime.of(2024, 1, 15, 10, 0),
            LocalDateTime.of(2024, 1, 15, 10, 15),
            BigDecimal("2.0")
        )
        client.sendSignal(signal)

        client.clear()

        assertTrue(client.getAllSignals().isEmpty())
    }
}
