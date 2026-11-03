package com.example.ble_esp
import org.junit.Assert.*
import org.junit.Test

class TelemetryTest {
    @Test fun fragmentedAndCombinedNotifications() {
        val f=Telemetry.Framer()
        assertTrue(f.accept("ESP32_01|25.").isEmpty())
        val lines=f.accept("40|80.10\nESP32_02|0|2\n")
        assertEquals(2,lines.size)
        assertEquals(25.4,Telemetry.parse(lines[0]).temperature,0.0001)
        assertEquals("ESP32_02",Telemetry.parse(lines[1]).device)
    }
    @Test fun invalidDataRejected() {
        listOf("ON","ESP|NaN|50","ESP|25|401","../device|25|50","ESP|90|20").forEach {
            try { Telemetry.parse(it); fail("Accepted $it") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {}
        }
    }
    @Test fun reconnectDiscardsPartialFrame() {
        val f=Telemetry.Framer(); f.accept("broken"); f.clear()
        assertEquals("ESP|20|30",f.accept("ESP|20|30\n").single())
    }
    @Test(expected=IllegalArgumentException::class) fun oversizedFrameRejected() { Telemetry.Framer().accept("x".repeat(65)) }
}
