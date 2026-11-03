# Pruebas de aceptación y entregables reales

Registra fecha, teléfono/versión Android, placa, versión del paquete ESP32, bibliotecas DHT, equipo y resultado. No marques una prueba como aprobada antes de ejecutarla con hardware.

| Prueba | Pasos | Resultado esperado |
| --- | --- | --- |
| Temperatura | Lee Monitor Serie; aproxima aire templado y espera varias muestras | Temperatura cambia; unidad °C; sin valores inventados |
| Distancia | Coloca superficie plana a 10, 20, 40 cm | Cambia la distancia en cm; compara error con regla |
| Sensor defectuoso | Desconecta DATA DHT11; después prueba sin eco ultrasónico | Error por serie y par omitido; no se almacena 0 como dato válido |
| BLE | Buscar, conceder permiso, conectar | Conectada y lista solo tras confirmar escritura CCCD |
| Permiso denegado | Deniega dispositivos cercanos | Estado de permiso denegado; volver a intentar desde el botón |
| Ausencia ESP32 | Apaga ESP32 e inicia búsqueda | Escaneo termina tras 10 s y permite repetir |
| Reconexión | Conecta, apaga ESP32, vuelve a encender y pulsa buscar | Informa desconexión y se reconecta sin reiniciar app |
| Android | Inicia acelerómetro, gira el teléfono, detén | Filas ANDROID x/y/z con m/s² y fecha; dejan de aumentar al detener |
| Room sin Internet | Desactiva Wi-Fi/datos manteniendo Bluetooth; captura; cierra y abre app | Historial persiste y pendientes siguen sin enviar |
| Firestore | Reactiva Internet; configura acceso; sincroniza por lotes | Documentos bajo equipo correcto; contador disminuye tras confirmación |
| Idempotencia | Detén adquisición; sincroniza hasta cero; pulsa otra vez | Sin nuevos documentos ni duplicados; mismos medicionId |
| Error de permisos nube | Usa un equipo no autorizado | Error visible; Room conserva filas pendientes |
| Fuente de datos | Consulta Firestore; luego Historial local | Encabezados FIRESTORE servidor / ROOM historial local inequívocos |
| Consulta nube offline | Sin Internet, pulsa Consultar Firestore | Error de servidor; no se presenta caché como dato remoto confirmado |
| Ciclo de vida | Inicia adquisición; abre otra app; regresa | Muestreo detenido; se reinicia manualmente y BLE permite buscar |

## Capturas a entregar

1. Pantalla principal: estado BLE listo, temperatura/distancia reales, últimas lecturas con origen/unidad/fecha y contador de pendientes.
2. Historial local: filas de ESP32 y ANDROID con estado pendiente/enviado. Una captura offline resulta útil.
3. Consola Firestore: ruta del equipo, dos documentos ESP32 y uno ANDROID; abre un documento para mostrar el esquema. Oculta datos personales de la cuenta.

Guarda las imágenes reales en una carpeta `evidencias/` y enlázalas en README. No uses valores ficticios como evidencia.

## Guion para video de 3 minutos

- **0:00–0:25:** muestra ESP32, DHT11 y HC-SR04, sus conexiones y divisor ECHO.
- **0:25–1:05:** conecta desde Android y mueve un objeto; muestra cambio de distancia y temperatura si ya preparaste un estímulo lento seguro.
- **1:05–1:30:** inicia acelerómetro y gira el celular; muestra origen ANDROID y detén captura.
- **1:30–2:00:** sin Internet, muestra nuevas lecturas en Room pendientes. Apaga/enciende ESP32 y demuestra reconexión.
- **2:00–2:40:** reactiva Internet, sincroniza, muestra documentos Firestore y repite sincronización para comprobar ausencia de duplicados.
- **2:40–3:00:** alterna consulta del servidor e historial local y cierra mostrando el diagrama.

Antes de grabar, comprueba que Firebase ya está configurado. Si el equipo genera más de 100 filas, pulsa sincronizar varias veces. Detén la adquisición al demostrar que los pendientes llegaron a cero.
