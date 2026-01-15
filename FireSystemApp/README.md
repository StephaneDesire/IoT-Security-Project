# Fire System Android App

Android application for controlling ESP32 Fire Detection System via BLE with ASCON-128 encryption.

## Features

- 🔍 **BLE Scanning** - Find ESP32_FIRE_SYSTEM device
- 🔐 **ASCON-128 AEAD** - Secure encrypted communication
- 🌡️ **Temperature Monitoring** - Real-time temperature display
- 🔥 **Flame Alerts** - Instant notification on flame detection
- 💡 **LED Control** - Turn LED on/off
- 🔔 **Buzzer Control** - Stop alarm buzzer

## Encryption Details

Matches ESP32 code exactly:
- **Algorithm**: ASCON-128 AEAD
- **Key**: `0x01,0x02,0x03,0x04,0x05,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F,0x10`
- **AAD**: `BLE-ASCON-V1`
- **Format**: `nonce(16 bytes) || ciphertext || tag(16 bytes)` in HEX

## BLE Service UUIDs

| Characteristic | UUID |
|----------------|------|
| Service | `12345678-1234-1234-1234-123456789000` |
| Temperature (NOTIFY) | `12345678-1234-1234-1234-123456789001` |
| Command (WRITE) | `12345678-1234-1234-1234-123456789002` |
| Alarm (NOTIFY) | `12345678-1234-1234-1234-123456789003` |

## Commands

- `LED_ON` - Turn on LED
- `LED_OFF` - Turn off LED
- `BUZZER_OFF` - Stop buzzer and reset alarm

## Build Instructions

1. Open project in Android Studio
2. Sync Gradle
3. Build and run on Android device (API 26+)

## Requirements

- Android 8.0 (API 26) or higher
- Bluetooth Low Energy support
- Location permission (required for BLE scanning)

## Project Structure

```
app/src/main/java/com/firesystem/app/
├── MainActivity.kt              # Entry point, permissions
├── BleConstants.kt              # UUIDs matching ESP32
├── ble/
│   └── BleManager.kt            # BLE operations
├── crypto/
│   ├── Ascon128.kt              # ASCON-128 implementation
│   └── AsconCipher.kt           # Encryption wrapper
├── viewmodel/
│   └── FireSystemViewModel.kt   # State management
└── ui/
    ├── FireSystemApp.kt         # Main UI
    └── theme/
        ├── Color.kt
        └── Theme.kt
```

## Security Note

⚠️ The hardcoded key is for development/demonstration only. In production:
- Use secure key exchange
- Store keys in Android Keystore
- Consider key rotation mechanisms
