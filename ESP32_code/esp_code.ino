// Import des bibliothèques
#include <Arduino.h>
#include <DHT.h>

#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include <Crypto.h>
#include <Ascon128.h>

//Définition des pins
#define LED_PIN       25
#define DHT_PIN       27
#define FLAME_PIN     26
#define BUZZER_PIN    33

//  Capteur DHT
#define DHTTYPE DHT11
DHT dht(DHT_PIN, DHTTYPE);

//Définition des variables globales
volatile bool flameDetected = false;
bool buzzerActive = false;
float temperature = 0.0;

//Définition des variables BLE
BLECharacteristic *tempChar;
BLECharacteristic *cmdChar;
BLECharacteristic *alarmChar;

bool deviceConnected = false;

// BLE UUIDs
#define SERVICE_UUID  "12345678-1234-1234-1234-123456789000"
#define TEMP_UUID     "12345678-1234-1234-1234-123456789001"
#define CMD_UUID      "12345678-1234-1234-1234-123456789002"
#define ALARM_UUID    "12345678-1234-1234-1234-123456789003"

//Paramètres
Ascon128 ascon;

// Clé secrète 128 bits (DOIT être identique côté client)
uint8_t ASCON_KEY[16] = {
  0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,
  0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10
};

// Compteur monotone pour nonce (anti-rejeu)
static uint32_t nonceCounter = 0;

// AAD : authentifiée mais non chiffrée
const char *AAD = "BLE-ASCON-V1";

//       INTERRUPTION CAPTEUR FLAMME
void IRAM_ATTR flameISR() {
  flameDetected = true;
}

//vGENERATION de NONCE 
void generateNonce(uint8_t nonce[16]) {
  memset(nonce, 0, 16);
  nonce[12] = (nonceCounter >> 24) & 0xFF;
  nonce[13] = (nonceCounter >> 16) & 0xFF;
  nonce[14] = (nonceCounter >> 8)  & 0xFF;
  nonce[15] = nonceCounter & 0xFF;
  nonceCounter++;
}

// ASCON AEAD ENCRYPTION (HEX)
String asconEncryptAEAD(String plaintext) {

  uint8_t nonce[16];
  generateNonce(nonce);

  uint8_t plain[32]  = {0};
  uint8_t cipher[32] = {0};
  uint8_t tag[16];

  int len = plaintext.length();
  plaintext.getBytes(plain, len + 1);

  // --- INITIALISATION AEAD ---
  ascon.clear();
  ascon.setKey(ASCON_KEY, 16);
  ascon.setIV(nonce, 16);

  // --- AAD ---
  ascon.addAuthData(AAD, strlen(AAD));

  // --- CHIFFREMENT ---
  ascon.encrypt(cipher, plain, len);

  // --- TAG ---
  ascon.computeTag(tag, 16);

  // --- FORMAT : nonce | ciphertext | tag ---
  String out = "";
  auto toHex = [&](uint8_t *buf, int size) {
    for (int i = 0; i < size; i++) {
      if (buf[i] < 16) out += "0";
      out += String(buf[i], HEX);
    }
  };

  toHex(nonce, 16);
  toHex(cipher, len);
  toHex(tag, 16);

  return out;
}

//  ASCON AEAD DECRYPT + AUTH CHECK
bool asconDecryptAEAD(String hex, String &output) {

  int totalLen = hex.length() / 2;
  if (totalLen < 32) return false;

  uint8_t raw[64] = {0};
  for (int i = 0; i < totalLen; i++) {
    raw[i] = strtol(hex.substring(i * 2, i * 2 + 2).c_str(), NULL, 16);
  }

  uint8_t *nonce  = raw;
  uint8_t *cipher = raw + 16;
  int cipherLen   = totalLen - 32;
  uint8_t *tag    = raw + 16 + cipherLen;

  uint8_t plain[32] = {0};

  // --- INITIALISATION ---
  ascon.clear();
  ascon.setKey(ASCON_KEY, 16);
  ascon.setIV(nonce, 16);

  // --- AAD (doit être identique) ---
  ascon.addAuthData(AAD, strlen(AAD));

  // --- DECHIFFREMENT ---
  ascon.decrypt(plain, cipher, cipherLen);

  // --- AUTHENTIFICATION ---
  if (!ascon.checkTag(tag, 16)) {
    return false;
  }

  output = String((char*)plain);
  return true;
}

//        BLE COMMAND CALLBACK
class CommandCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) {
    String rxValue = pChar->getValue();
    String cmd;
     
    Serial.println("valeur reçue ");
    Serial.println(rxValue);
    if (!asconDecryptAEAD(rxValue, cmd)) {
      Serial.println(" Message BLE invalide !");
      return;
    }

    Serial.print("Commande reçue : ");
    Serial.println(cmd);

    if (cmd == "LED_ON")  digitalWrite(LED_PIN, HIGH);
    if (cmd == "LED_OFF") digitalWrite(LED_PIN, LOW);

    if (cmd == "BUZZER_OFF") {
      digitalWrite(BUZZER_PIN, LOW);
      buzzerActive = false;
      flameDetected = false;
    }
  }
};

//  BLE SERVER CALLBACK 
class ServerCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer* pServer) {
    deviceConnected = true;
    Serial.println("BLE connecté");
  }

  void onDisconnect(BLEServer* pServer) {
    deviceConnected = false;
    Serial.println("BLE déconnecté");
    delay(200);
    BLEDevice::getAdvertising()->start(); 
    Serial.println("Advertising relancé");
  }
};

//                   SETUP
void setup() {
  Serial.begin(115200);

  pinMode(LED_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(FLAME_PIN, INPUT);

  dht.begin();

  attachInterrupt(digitalPinToInterrupt(FLAME_PIN), flameISR, FALLING);

  // --- BLE INITIALISATION ---
  BLEDevice::init("ESP32_FIRE_SYSTEM");
  BLEDevice::setMTU(128); // optionnel mais stable

  BLEServer *server = BLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  BLEService *service = server->createService(SERVICE_UUID);

  tempChar = service->createCharacteristic(
    TEMP_UUID, BLECharacteristic::PROPERTY_NOTIFY
  );
  tempChar->addDescriptor(new BLE2902());

  cmdChar = service->createCharacteristic(
    CMD_UUID, BLECharacteristic::PROPERTY_WRITE
  );
  cmdChar->setCallbacks(new CommandCallback());

  alarmChar = service->createCharacteristic(
    ALARM_UUID, BLECharacteristic::PROPERTY_NOTIFY
  );
  alarmChar->addDescriptor(new BLE2902());

  service->start();
  BLEDevice::getAdvertising()->start();

  Serial.println("BLE + ASCON AEAD prêt");
}

//Boucle
void loop() {

  static unsigned long lastRead = 0;

  // ---- TEMPERATURE ----
  if (deviceConnected && millis() - lastRead > 2000) {
    lastRead = millis();

    temperature = dht.readTemperature();
    if (!isnan(temperature)) {
      String payload = asconEncryptAEAD(String(temperature, 1));
      Serial.println(payload);
      tempChar->setValue(payload.c_str());
      tempChar->notify();
    }
  }

  // ---- FLAME ALARM ----
  if (deviceConnected && flameDetected && !buzzerActive) {
    
    digitalWrite(BUZZER_PIN, HIGH);
    buzzerActive = true;

    String alarm = asconEncryptAEAD("FLAME");
    alarmChar->setValue(alarm.c_str());
    alarmChar->notify();
  }
 /* if(!flameDetected){
    digitalWrite(BUZZER_PIN, LOW);
  }*/
  
} 

	
