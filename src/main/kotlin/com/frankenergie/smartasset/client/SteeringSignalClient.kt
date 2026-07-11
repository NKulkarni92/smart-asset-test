package com.frankenergie.smartasset.client

import com.frankenergie.smartasset.model.SteeringSignal
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Component
class SteeringSignalClient(
    @Value("\${steering.signals.file:log/steering_signals.log}") private val filePath: String
) {
    private val file: Path = Path.of(filePath)

    fun sendSignal(signal: SteeringSignal) {
        ensureFileExists()
        val line = formatSignal(signal)
        Files.writeString(file, "$line\n", StandardOpenOption.APPEND)
    }

    fun getAllSignals(): List<String> {
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

    private fun formatSignal(signal: SteeringSignal): String {
        return "${signal.timestamp}|${signalPayload(signal)}"
    }

    private fun signalPayload(signal: SteeringSignal): String {
        return "${signal.groupId}|${signal.quarterStart}|${signal.quarterEnd}|${signal.chargePowerMW}"
    }
}
