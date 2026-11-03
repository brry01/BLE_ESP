package com.example.ble_esp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.util.UUID


@SuppressLint("MissingPermission")
class MainActivity : AppCompatActivity(), android.hardware.SensorEventListener {

    private lateinit var store: SensorStore
    private lateinit var tvSync: TextView
    private lateinit var tvHistory: TextView
    private lateinit var tvLatest: TextView
    private lateinit var sensors: android.hardware.SensorManager
    private var recording=false
    private var lastPhoneSample=0L
    private val frames=Telemetry.Framer()
    private var localRows: List<Measurement> = emptyList()
    private val connectionTimeout=Runnable { cerrarConexion(); actualizarEstado("Tiempo de conexión agotado. Puedes buscar de nuevo.") }
    private val SERVICE_UUID = UUID.fromString(
        "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
    )
    private val RX_UUID = UUID.fromString(
        "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
    )
    private val TX_UUID = UUID.fromString(
        "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"
    )
    private val CCCD_UUID = UUID.fromString(
        "00002902-0000-1000-8000-00805f9b34fb"
    )

    private lateinit var tvEstado: TextView
    private lateinit var tvDatos: TextView
    private lateinit var btnBuscar: Button
    private lateinit var btnEncender: Button
    private lateinit var btnApagar: Button
    private lateinit var btnDesconectar: Button

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val manager = getSystemService(
            BLUETOOTH_SERVICE
        ) as BluetoothManager
        manager.adapter
    }

    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic:
            BluetoothGattCharacteristic? = null
    private var isScanning = false
    private var isReady = false
    private val handler = Handler(Looper.getMainLooper())
    private val scanTimeout = Runnable {

        if (isScanning) {
            detenerBusqueda()
            actualizarEstado(
                "No se encontró la ESP32"
            )
        }
    }

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                verificarBluetooth()
            } else {
                actualizarEstado(
                    "Permisos Bluetooth denegados"
                )
            }
        }

    private val enableBluetoothLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) {
            if (bluetoothAdapter?.isEnabled == true) {
                buscarESP32()
            } else {
                actualizarEstado(
                    "Bluetooth desactivado"
                )
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        tvEstado = findViewById(R.id.tvEstado)
        tvDatos = findViewById(R.id.tvDatos)
        btnBuscar = findViewById(R.id.btnBuscar)
        btnEncender = findViewById(R.id.btnEncender)
        btnApagar = findViewById(R.id.btnApagar)
        btnDesconectar = findViewById(R.id.btnDesconectar)


        store=SensorStore.get(this)
        sensors=getSystemService(SENSOR_SERVICE) as android.hardware.SensorManager
        tvSync=findViewById(R.id.tvSync)
        tvHistory=findViewById(R.id.tvHistory)
        tvLatest=findViewById(R.id.tvLatest)
        findViewById<Button>(R.id.btnSync).setOnClickListener { tvSync.text="Sincronizando hasta 100 pendientes…"; store.sync { msg -> runOnUiThread { tvSync.text=msg } } }
        findViewById<Button>(R.id.btnCloud).setOnClickListener { tvHistory.text="Consultando servidor…"; store.readCloud { msg -> runOnUiThread { tvHistory.text=msg } } }
        findViewById<Button>(R.id.btnLocal).setOnClickListener { renderLocal() }
        findViewById<Button>(R.id.btnSettings).setOnClickListener { configure() }
        observeRoom()
        tvSync.text="Room disponible sin Internet. Configura Firebase e inicia sesión para sincronizar."
        habilitarComandos(false)
        btnBuscar.setOnClickListener {
            iniciarProcesoConexion()
        }
        btnEncender.setOnClickListener {
            startPhone()
        }
        btnApagar.setOnClickListener {
            stopPhone()
        }
        btnDesconectar.setOnClickListener {
            detenerBusqueda()
            cerrarConexion()
            actualizarEstado("Desconectado")
        }
    }

    private fun tienePermisos(): Boolean {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            checkSelfPermission(
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) == PackageManager.PERMISSION_GRANTED
        } else {
            checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun iniciarProcesoConexion() {
        if (!tienePermisos()) {
            val permisos = if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            ) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            permissionLauncher.launch(permisos)
        } else {
            verificarBluetooth()
        }
    }

    private fun verificarBluetooth() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            actualizarEstado(
                "Bluetooth no disponible"
            )
            return
        }
        if (!adapter.isEnabled) {
            val intent = Intent(
                BluetoothAdapter.ACTION_REQUEST_ENABLE
            )
            enableBluetoothLauncher.launch(intent)
        } else {
            buscarESP32()
        }
    }

    private fun buscarESP32() {
        if (!tienePermisos()) return
        if (isScanning) return
        cerrarConexion()
        val scanner =
            bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            actualizarEstado(
                "No se puede iniciar el escaneo BLE"
            )
            return
        }
        actualizarEstado("Buscando ESP32...")
        isScanning = true
        handler.postDelayed(scanTimeout, 10000)
        scanner.startScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(
            callbackType: Int,
            result: ScanResult
        ) {
            val nombre =
                result.scanRecord?.deviceName
                    ?: result.device.name
            val servicioEncontrado =
                result.scanRecord
                    ?.serviceUuids
                    ?.contains(ParcelUuid(SERVICE_UUID))
                    ?: false
            if (
                nombre == "BLE-ESP32" ||
                servicioEncontrado
            ) {
                runOnUiThread {
                    if (!isScanning) return@runOnUiThread
                    detenerBusqueda()
                    actualizarEstado(
                        "Conectando con ESP32..."
                    )
                    handler.postDelayed(connectionTimeout, 20000)
                    bluetoothGatt =
                        result.device.connectGatt(
                            this@MainActivity,
                            false,
                            gattCallback
                        )
                }
            }
        }


        override fun onScanFailed(errorCode: Int) {
            runOnUiThread {
                detenerBusqueda()
                actualizarEstado(
                    "Error de escaneo: $errorCode"
                )
            }
        }
    }

    private fun detenerBusqueda() {
        handler.removeCallbacks(scanTimeout)
        if (isScanning) {
            if (tienePermisos()) {
                bluetoothAdapter
                    ?.bluetoothLeScanner
                    ?.stopScan(scanCallback)
            }
            isScanning = false
        }
    }

    private val gattCallback =
        object : BluetoothGattCallback() {
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {
                runOnUiThread {
                if (gatt !== bluetoothGatt) { gatt.close(); return@runOnUiThread }
                if (
                    status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED
                ) {
                    actualizarEstado(
                        "Conectado. Buscando servicios..."
                    )
                    gatt.discoverServices()
                } else {
                    runOnUiThread {
                        isReady = false
                        habilitarComandos(false)
                        actualizarEstado(
                            "ESP32 desconectada. Código: $status"
                        )
                    }
                    handler.removeCallbacks(connectionTimeout)
                    gatt.close()
                    bluetoothGatt=null
                    frames.clear()
                }
                }
            }
            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {
                runOnUiThread {
                if (gatt !== bluetoothGatt) return@runOnUiThread
                if (
                    status != BluetoothGatt.GATT_SUCCESS
                ) {
                    actualizarEstado(
                        "Error al descubrir servicios"
                    )
                    return@runOnUiThread
                }
                val service =
                    gatt.getService(SERVICE_UUID)
                if (service == null) {
                    actualizarEstado(
                        "Servicio BLE no encontrado"
                    )
                    return@runOnUiThread
                }
                rxCharacteristic =
                    service.getCharacteristic(RX_UUID)
                val txCharacteristic =
                    service.getCharacteristic(TX_UUID)
                if (txCharacteristic == null ||
                    txCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0
                ) {
                    actualizarEstado(
                        "Características BLE no encontradas"
                    )
                    return@runOnUiThread
                }
                val enabled =
                    gatt.setCharacteristicNotification(
                        txCharacteristic,
                        true
                    )
                val descriptor =
                    txCharacteristic.getDescriptor(CCCD_UUID)
                if (!enabled || descriptor == null) {
                    actualizarEstado(
                        "No se pudieron habilitar notificaciones"
                    )
                    return@runOnUiThread
                }
                val value =
                    BluetoothGattDescriptor
                        .ENABLE_NOTIFICATION_VALUE
                val started = if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    gatt.writeDescriptor(
                        descriptor,
                        value
                    ) == BluetoothStatusCodes.SUCCESS
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = value
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
                if (!started) {
                    actualizarEstado(
                        "Error al configurar notificaciones"
                    )
                }
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: BluetoothGattDescriptor,
                status: Int
            ) {
                if (descriptor.uuid != CCCD_UUID) return
                runOnUiThread {
                    if (gatt !== bluetoothGatt) return@runOnUiThread
                    handler.removeCallbacks(connectionTimeout)
                    isReady =
                        status == BluetoothGatt.GATT_SUCCESS
                    habilitarComandos(isReady)
                    if (isReady) {
                        actualizarEstado(
                            "ESP32 conectada y lista"
                        )
                    } else {
                        actualizarEstado(
                            "Error al habilitar notificaciones"
                        )
                    }
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray
            ) {
                runOnUiThread { if (gatt === bluetoothGatt && isReady) procesarDato(characteristic, value) }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                if (
                    Build.VERSION.SDK_INT <
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    val bytes=characteristic.value?.clone() ?: byteArrayOf()
                    runOnUiThread { if (gatt === bluetoothGatt && isReady) procesarDato(characteristic, bytes) }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    actualizarEstado(
                        "Comando enviado correctamente"
                    )
                } else {
                    actualizarEstado(
                        "Error al enviar comando: $status"
                    )
                }
            }
        }

    private fun procesarDato(
        characteristic: BluetoothGattCharacteristic,
        data: ByteArray
    ) {
        if (characteristic.uuid != TX_UUID) return

        try {
            frames.accept(String(data, Charsets.UTF_8)).forEach { line ->
                val value=Telemetry.parse(line)
                store.save(value.device,"ESP32",listOf(
                    Triple("temperatura",value.temperature,"°C"),
                    Triple("distancia",value.distance,"cm"))) { msg -> runOnUiThread { tvSync.text=msg } }
                tvDatos.text="ESP32 · ${value.device}\nTemperatura: ${value.temperature} °C\nDistancia: ${value.distance} cm\n${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())} · recepción Android"
            }
        } catch (e: Exception) { frames.clear(); tvDatos.text="Mensaje BLE inválido: ${e.message}" }
    }

    private fun actualizarEstado(mensaje: String) {
        runOnUiThread {
            tvEstado.text = mensaje
        }
    }

    private fun habilitarComandos(habilitar: Boolean) {
        btnEncender.isEnabled = !recording
        btnApagar.isEnabled = recording
    }

    private fun cerrarConexion() {
        handler.removeCallbacks(connectionTimeout)
        frames.clear()
        isReady = false
        habilitarComandos(false)
        try {
            bluetoothGatt?.disconnect()
        } catch (_: SecurityException) {
        }
        bluetoothGatt?.close()
        bluetoothGatt = null
        rxCharacteristic = null
    }


    private fun observeRoom() {
        store.dao.recent(store.team).observe(this) { rows ->
            localRows=rows
            val latest=rows.distinctBy { "${it.dispositivoId}:${it.variable}" }
            tvLatest.text="ÚLTIMAS LECTURAS · ROOM · ${store.team}\n" + latest.joinToString("\n\n") { formatRow(it) }
            // Preserve the explicitly selected remote view until Local is pressed.
            if (!tvHistory.text.startsWith("FIRESTORE") && !tvHistory.text.startsWith("Consultando")) renderLocal()
        }
        store.dao.pendingCount(store.team).observe(this) { count ->
            findViewById<TextView>(R.id.tvPending).text="Room · $count pendientes de sincronizar"
        }
    }
    private fun formatRow(m: Measurement): String =
        "${m.origen} · ${m.dispositivoId}\n${m.variable}: ${String.format(java.util.Locale.US,"%.2f",m.valor)} ${m.unidad}\n${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.fechaHora))} · ${if(m.sincronizado) "enviado" else "pendiente"}"
    private fun renderLocal() {
        tvHistory.text="ROOM · historial local · últimas 100 lecturas\n\n" + localRows.joinToString("\n\n") { formatRow(it) }
    }
    private fun startPhone() {
        if (recording) return
        val sensor=sensors.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        if (sensor==null) { tvSync.text="Este teléfono no tiene acelerómetro"; return }
        lastPhoneSample=0
        recording=sensors.registerListener(this,sensor,android.hardware.SensorManager.SENSOR_DELAY_NORMAL)
        tvSync.text=if(recording) "Acelerómetro activo · una lectura xyz cada 2 s" else "No se pudo activar el acelerómetro"
        habilitarComandos(false)
    }
    private fun stopPhone() { sensors.unregisterListener(this); recording=false; habilitarComandos(false) }
    override fun onSensorChanged(event: android.hardware.SensorEvent) {
        if (!recording || event.sensor.type!=android.hardware.Sensor.TYPE_ACCELEROMETER) return
        if (lastPhoneSample!=0L && event.timestamp-lastPhoneSample<2_000_000_000L) return
        lastPhoneSample=event.timestamp
        store.save(store.mobileId,"ANDROID",listOf(
            Triple("aceleracion_x",event.values[0].toDouble(),"m/s²"),
            Triple("aceleracion_y",event.values[1].toDouble(),"m/s²"),
            Triple("aceleracion_z",event.values[2].toDouble(),"m/s²"))) { msg -> runOnUiThread { tvSync.text=msg } }
    }
    override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
    private fun configure() {
        val box=android.widget.LinearLayout(this).apply { orientation=android.widget.LinearLayout.VERTICAL; setPadding(40,20,40,10) }
        val team=android.widget.EditText(this).apply { hint="Equipo del grupo"; setText(store.team) }
        val email=android.widget.EditText(this).apply { hint="Correo autorizado por docente"; inputType=android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS }
        val pass=android.widget.EditText(this).apply { hint="Contraseña (no se guarda)"; inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD }
        box.addView(team); box.addView(email); box.addView(pass)
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Equipo y Firebase").setView(box)
            .setPositiveButton("Guardar / ingresar") { _,_ ->
                val id=team.text.toString().trim()
                if (!id.matches(Regex("[A-Za-z0-9_-]{1,40}"))) { tvSync.text="Equipo inválido: usa letras, números, _ o -"; return@setPositiveButton }
                store.team=id
                if(email.text.isNotBlank()) store.login(email.text.toString().trim(),pass.text.toString()) { msg -> android.widget.Toast.makeText(applicationContext,msg,android.widget.Toast.LENGTH_LONG).show() }
                recreate()
            }.setNeutralButton("Cerrar sesión") { _,_ -> store.logout(); tvSync.text="Sesión cerrada; Room permanece disponible" }
            .setNegativeButton("Cancelar",null).show()
    }
    override fun onStop() {
        stopPhone(); detenerBusqueda(); cerrarConexion()
        actualizarEstado("BLE detenida al salir. Pulsa Buscar para reconectar.")
        super.onStop()
    }
    override fun onDestroy() {
        detenerBusqueda()
        cerrarConexion()
        super.onDestroy()
    }
}