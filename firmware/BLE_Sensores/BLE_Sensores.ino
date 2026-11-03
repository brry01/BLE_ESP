#include <Arduino.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <DHT.h>

// ESP32 clÃ¡sico (p. ej. ESP-WROOM-32). No sirve para ESP32-S2, que no tiene BLE.
constexpr int DHT_PIN=4;
constexpr int TRIG_PIN=5;
constexpr int ECHO_PIN=18; // ECHO 5V -> 1k ohm -> GPIO18 -> 2k ohm -> GND
constexpr uint32_t SAMPLE_MS=2000;
const char* DEVICE_ID="ESP32_01"; // distinto por dispositivo; mÃ¡ximo 24 caracteres ASCII
const char* SERVICE_UUID="6E400001-B5A3-F393-E0A9-E50E24DCCA9E";
const char* TX_UUID="6E400003-B5A3-F393-E0A9-E50E24DCCA9E";
DHT dht(DHT_PIN,DHT11);
BLECharacteristic* telemetry;
BLE2902* subscription;
volatile bool connected=false;
volatile bool restartAdvertising=false;
uint32_t lastSample=0;
class ServerCallbacks: public BLEServerCallbacks {
  void onConnect(BLEServer*) override { connected=true; }
  void onDisconnect(BLEServer*) override { connected=false; restartAdvertising=true; }
};
void setup() {
  Serial.begin(115200);
  dht.begin();
  pinMode(TRIG_PIN,OUTPUT);
  pinMode(ECHO_PIN,INPUT);
  digitalWrite(TRIG_PIN,LOW);
  BLEDevice::init("BLE-ESP32");
  BLEServer* server=BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());
  BLEService* service=server->createService(SERVICE_UUID);
  telemetry=service->createCharacteristic(TX_UUID,BLECharacteristic::PROPERTY_NOTIFY);
  subscription=new BLE2902();
  telemetry->addDescriptor(subscription);
  service->start();
  BLEAdvertising* advertising=BLEDevice::getAdvertising();
  advertising->addServiceUUID(SERVICE_UUID);
  advertising->setScanResponse(true);
  advertising->start();
  Serial.println("DHT11 GPIO4 / HC-SR04 TRIG5 ECHO18 con divisor / cada 2 s / esperando Android");
}
void loop() {
  if(restartAdvertising) {
    restartAdvertising=false;
    delay(100);
    BLEDevice::startAdvertising();
  }
  const uint32_t now=millis();
  if(now-lastSample<SAMPLE_MS) { delay(10); return; }
  lastSample=now;
  float temperature=dht.readTemperature();
  int raw=analogRead(LDR_PIN);
  float light=raw*100.0f/4095.0f; // luz relativa del ADC; NO es una mediciÃ³n en lux
  if(isnan(temperature) || temperature < -40 || temperature > 80) {
    Serial.println("ERROR DHT11: revisar cableado; no se publica una lectura inventada");
    return;
  }
  if(duration==0 || distance<2 || distance>400) {
    Serial.println("ERROR HC-SR04: sin eco o fuera de rango 2-400 cm; no se publica el par");
    return;
  }
  char message[65];
  snprintf(message,sizeof(message),"%s|%.2f|%.2f\n",DEVICE_ID,temperature,distance);
  Serial.print(message);
  if(connected && subscription->getNotifications()) {
    // Fragmentos <=20 bytes: funciona incluso con MTU BLE mÃ­nimo de 23.
    // Android recompone la lÃ­nea al recibir '\n'. No se requiere negociar MTU.
    size_t len=strlen(message);
    for(size_t i=0;i<len && connected;i+=20) {
      telemetry->setValue((uint8_t*)message+i,min((size_t)20,len-i));
      telemetry->notify();
      delay(30);
    }
  }
}
