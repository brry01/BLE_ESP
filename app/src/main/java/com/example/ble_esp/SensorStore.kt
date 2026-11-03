package com.example.ble_esp

import android.content.Context
import androidx.room.Room
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class SensorStore private constructor(context: Context) {
    val dao = Room.databaseBuilder(context, MeasurementDatabase::class.java, "telemetry.db")
        .build().measurements()
    private val disk = Executors.newSingleThreadExecutor()
    private val network = Executors.newSingleThreadExecutor()
    private val syncing = AtomicBoolean(false)
    private val prefs = context.getSharedPreferences("telemetry", Context.MODE_PRIVATE)
    var team: String
        get() = prefs.getString("team", "EQUIPO_01")!!
        set(value) { require(value.matches(Regex("[A-Za-z0-9_-]{1,40}"))); prefs.edit().putString("team",value).apply() }
    val mobileId: String = prefs.getString("mobile", null) ?: ("MOVIL_" + UUID.randomUUID()).also {
        prefs.edit().putString("mobile",it).apply()
    }
    init { FirebaseApp.initializeApp(context) }
    private fun firestore(): FirebaseFirestore {
        check(FirebaseApp.getApps(appContext!!).isNotEmpty()) { "Falta app/google-services.json del grupo; Room sigue disponible" }
        check(FirebaseAuth.getInstance().currentUser != null) { "Inicia sesión con una cuenta autorizada por el docente" }
        return FirebaseFirestore.getInstance()
    }
    fun save(device: String, origin: String, values: List<Triple<String, Double, String>>, onError: (String)->Unit) {
        val teamAtCapture = team
        val time = System.currentTimeMillis()
        val rows = values.map { (variable, value, unit) ->
            Measurement().apply {
                medicionId=UUID.randomUUID().toString(); equipoId=teamAtCapture; dispositivoId=device
                origen=origin; this.variable=variable; valor=value; unidad=unit; fechaHora=time
            }
        }
        disk.execute { try { dao.insertAll(rows) } catch (e: Exception) { onError("Error guardando Room: ${e.message}") } }
    }
    fun login(email: String, password: String, done: (String)->Unit) {
        if (FirebaseApp.getApps(appContext!!).isEmpty()) { done("Falta app/google-services.json"); return }
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { done("Sesión iniciada: ${it.user?.email}") }
            .addOnFailureListener { done("Acceso: ${it.message}") }
    }
    fun logout() { if (FirebaseApp.getApps(appContext!!).isNotEmpty()) FirebaseAuth.getInstance().signOut() }
    private fun collection(db: FirebaseFirestore, team: String) = db.collection("equipos").document(team).collection("mediciones")
    fun sync(done: (String)->Unit) {
        if (!syncing.compareAndSet(false,true)) { done("Ya hay una sincronización en curso"); return }
        val selectedTeam=team
        network.execute {
            try {
                val db=firestore()
                val ownerUid = FirebaseAuth.getInstance().currentUser?.uid
                    ?: error("Debes iniciar sesión antes de sincronizar")
                val rows=disk.submit<List<Measurement>> { dao.pending(selectedTeam) }.get()
                if (rows.isEmpty()) { done("No hay lecturas pendientes"); return@execute }
                val batch=db.batch()
                rows.forEach { m ->
                    batch.set(
                        collection(db, selectedTeam).document(m.medicionId),
                        mapOf(
                            "medicionId" to m.medicionId,
                            "equipoId" to m.equipoId,
                            "dispositivoId" to m.dispositivoId,
                            "ownerUid" to ownerUid,
                            "origen" to m.origen,
                            "variable" to m.variable,
                            "valor" to m.valor,
                            "unidad" to m.unidad,
                            "fechaHora" to m.fechaHora
                        )
                    )
                }
                Tasks.await(batch.commit(), 20, TimeUnit.SECONDS)
                disk.submit { dao.markSynced(rows.map { it.medicionId }) }.get()
                done("${rows.size} lecturas confirmadas por Firestore. Repite si quedan pendientes.")
            } catch (e: Exception) {
                done("Sin confirmación: ${e.cause?.message ?: e.message ?: "tiempo agotado"}. Room conserva los pendientes; puedes reintentar.")
            } finally { syncing.set(false) }
        }
    }
    fun readCloud(done: (String)->Unit) {
        val selectedTeam=team
        network.execute {
            try {
                val query=collection(firestore(),selectedTeam).orderBy("fechaHora", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100)
                val snapshot=Tasks.await(query.get(Source.SERVER),20,TimeUnit.SECONDS)
                done("FIRESTORE · servidor · $selectedTeam · ${snapshot.size()} registros\n" + snapshot.documents.joinToString("\n\n") {
                    val date=java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it.getLong("fechaHora") ?: 0))
                    "${it.getString("origen")} · ${it.getString("dispositivoId")}\n${it.getString("variable")}: ${it.getDouble("valor")} ${it.getString("unidad")}\n$date"
                })
            } catch (e: Exception) { done("FIRESTORE · no se pudo consultar el servidor: ${e.cause?.message ?: e.message}. Usa Historial local.") }
        }
    }
    companion object {
        @Volatile private var instance: SensorStore?=null
        private var appContext: Context?=null
        fun get(context: Context): SensorStore = instance ?: synchronized(this) {
            instance ?: run { appContext=context.applicationContext; SensorStore(context.applicationContext).also { instance=it } }
        }
    }
}
