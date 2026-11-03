# Estado de verificación

Fecha: 2 de noviembre de 2026.

## Comprobado en esta sesión

- Compilación directa de los modelos Java con `javac`: correcta.
- Procesamiento real de las anotaciones de Room con `androidx.room.RoomProcessor`: correcto; genera `MeasurementDatabase_Impl` y `MeasurementDao_Impl`.
- Comprobación de tipos y compilación Kotlin del código Android usando el SDK 37 y dependencias disponibles: correcta.
- JUnit: **5 pruebas aprobadas** (4 del protocolo y 1 aritmética de la plantilla original). Las pruebas del protocolo verifican recomposición de notificaciones fragmentadas, rechazo de valores inválidos, limpieza de una trama incompleta al reconectar y límite de longitud.

Esta comprobación directa usa un `R` auxiliar para resolver los nombres de recursos; **no sustituye el empaquetado y enlace de recursos de un build Gradle completo**. El auxiliar no forma parte del proyecto entregado. Se conservan las pruebas dentro de `app/src/test` para ejecutarlas normalmente en Android Studio.

## Pendiente de confirmar

- **APK completo:** Gradle no pudo conectarse a su proceso daemon en el entorno restringido. El intento de descargar su distribución también falló por resolución de red. Ejecuta `assembleDebug testDebugUnitTest` en Android Studio/consola con acceso normal. No se entrega un APK como si hubiera sido compilado con estos cambios.
- **Firmware:** no se compiló con toolchain Arduino/ESP32 en esta sesión; compilar/cargar con las bibliotecas descritas en README.
- **Pruebas físicas:** DHT11, HC-SR04, divisor de ECHO, reconexión BLE y acelerómetro deben verificarse con los dispositivos reales.
- **Firebase:** faltan configuración y autorización del proyecto del grupo. No se desplegaron ni verificaron reglas en una base remota. El archivo de reglas incluido es una propuesta para revisar.
- **Capturas, video y publicación GitHub:** requieren el funcionamiento real y un destino de repositorio. No se inventaron evidencias ni se publicó a una cuenta supuesta.

La revisión de código cubre permisos BLE según versión, confirmación CCCD antes de estado listo, límite de búsqueda de 10 s, timeout de conexión de 20 s, lectura cada 2 s, separación por equipo, UUID estable para reintentar, Room como fuente local, consulta remota explícita y detención al salir de pantalla.
