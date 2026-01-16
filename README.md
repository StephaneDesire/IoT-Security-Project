# 🔥 IoT Fire Detection System with Secure BLE Communication

A complete IoT fire detection system featuring an ESP32 microcontroller and an Android companion app, secured with **ASCON-128 authenticated encryption**.

## 📋 Overview

This project implements a secure fire detection and monitoring system consisting of:

- **ESP32 Hardware** - Monitors temperature and flame sensors, controls LED and buzzer alerts
- **Android App** - Remote monitoring and control via Bluetooth Low Energy (BLE)
- **ASCON-128 Encryption** - Lightweight authenticated encryption for secure communication

## 🏗️ Project Structure

```
IoT-Security-Project/
├── Ascon/                  # ASCON-128 cryptographic library
│   ├── Ascon128.cpp
│   └── Ascon128.h
├── ESP32_code/             # ESP32 firmware
│   └── esp_code.ino
└── FireSystemApp/          # Android companion app
    └── app/src/main/java/com/firesystem/app/
```

## 🔧 Hardware Components

| Component | Pin | Description |
|-----------|-----|-------------|
| DHT11 Sensor | GPIO 27 | Temperature monitoring |
| Flame Sensor | GPIO 26 | Fire/flame detection |
| LED | GPIO 25 | Visual indicator |
| Buzzer | GPIO 33 | Audible alarm |

## 📡 BLE Communication

### Service UUIDs

| Characteristic | UUID | Type |
|----------------|------|------|
| Service | `12345678-1234-1234-1234-123456789000` | - |
| Temperature | `12345678-1234-1234-1234-123456789001` | NOTIFY |
| Command | `12345678-1234-1234-1234-123456789002` | WRITE |
| Alarm | `12345678-1234-1234-1234-123456789003` | NOTIFY |

### Commands

| Command | Action |
|---------|--------|
| `LED_ON` | Turn on LED |
| `LED_OFF` | Turn off LED |
| `BUZZER_OFF` | Stop buzzer and reset alarm |

## 🔐 Security: ASCON-128 AEAD

All BLE communication is encrypted using **ASCON-128** authenticated encryption:

- **Key Size**: 128-bit
- **IV/Nonce Size**: 128-bit (counter-based for anti-replay)
- **Tag Size**: 128-bit
- **AAD**: `BLE-ASCON-V1`
- **Message Format**: `nonce (16 bytes) || ciphertext || tag (16 bytes)` in HEX

> ASCON is a lightweight authenticated cipher, finalist in the CAESAR competition, optimized for constrained IoT devices.

## 🚀 Getting Started

### ESP32 Setup

1. **Install Arduino IDE** with ESP32 board support
2. **Install Libraries**:
   - DHT sensor library
   - Crypto library (includes ASCON)
   - ESP32 BLE Arduino
3. **Copy** the `Ascon/` folder to your Arduino libraries folder
4. **Open** `ESP32_code/esp_code.ino`
5. **Upload** to your ESP32 board

### Android App Setup

1. **Open** `FireSystemApp/` in Android Studio
2. **Sync** Gradle dependencies
3. **Build and run** on an Android device (API 26+)
4. **Grant** Bluetooth and Location permissions

## 📱 App Features

- 🔍 **BLE Scanning** - Automatically find ESP32_FIRE_SYSTEM device
- 🌡️ **Temperature Monitoring** - Real-time encrypted temperature readings
- 🔥 **Flame Alerts** - Instant notification on flame detection
- 💡 **LED Control** - Remote LED on/off control
- 🔔 **Buzzer Control** - Stop alarm remotely

## ⚠️ Important Notes

- The **encryption key must match** on both ESP32 and Android app
- Default key: `0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F, 0x10`
- **Change the key** before deploying in production!
- Android requires **Location permission** for BLE scanning

## 📄 License

ASCON-128 implementation: MIT License (Copyright © 2018 Southern Storm Software)

---

**Built with ❤️ for IoT Security**
