package com.firesystem.app

import java.util.UUID

/**
 * BLE Constants - MUST match ESP32 code exactly
 */
object BleConstants {
    // Device name from ESP32: BLEDevice::init("ESP32_FIRE_SYSTEM")
    const val DEVICE_NAME = "ESP32_FIRE_SYSTEM"

    // UUIDs from ESP32 code
    val SERVICE_UUID: UUID = UUID.fromString("12345678-1234-1234-1234-123456789000")
    val TEMP_UUID: UUID    = UUID.fromString("12345678-1234-1234-1234-123456789001")
    val CMD_UUID: UUID     = UUID.fromString("12345678-1234-1234-1234-123456789002")
    val ALARM_UUID: UUID   = UUID.fromString("12345678-1234-1234-1234-123456789003")

    // Client Characteristic Configuration Descriptor (for notifications)
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}
