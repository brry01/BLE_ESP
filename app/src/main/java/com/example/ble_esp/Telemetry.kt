package com.example.ble_esp

/** Protocol: ESP32_01|temperatureC|distanceCm\n (max 64 characters). */
object Telemetry {
    data class Sample(val device: String, val temperature: Double, val distance: Double)
    fun parse(line: String): Sample {
        val fields = line.trim().split('|')
        require(fields.size == 3) { "Se esperan dispositivo|temperatura|distancia" }
        require(fields[0].matches(Regex("[A-Za-z0-9_-]{1,24}"))) { "Dispositivo inválido" }
        val t = fields[1].toDoubleOrNull() ?: error("Temperatura inválida")
        val l = fields[2].toDoubleOrNull() ?: error("Distancia inválida")
        require(t.isFinite() && t in 0.0..50.0) { "Temperatura fuera de rango DHT11" }
        require(l.isFinite() && l in 2.0..400.0) { "Distancia fuera de rango" }
        return Sample(fields[0], t, l)
    }
    class Framer {
        private val buffer = StringBuilder()
        fun clear() = buffer.setLength(0)
        fun accept(chunk: String): List<String> {
            buffer.append(chunk)
            val lines = mutableListOf<String>()
            while (true) {
                val end = buffer.indexOf("\n")
                if (end < 0) break
                require(end <= 64) { "Mensaje demasiado largo" }
                lines.add(buffer.substring(0, end))
                buffer.delete(0, end + 1)
            }
            require(buffer.length <= 64) { "Mensaje incompleto demasiado largo" }
            return lines
        }
    }
}
