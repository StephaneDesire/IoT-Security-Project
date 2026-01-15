package com.firesystem.app.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import com.firesystem.app.BleConstants
import com.firesystem.app.crypto.AsconCipher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BLE Manager - Handles all Bluetooth Low Energy operations
 * Matches ESP32 service/characteristic UUIDs exactly
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG = "BleManager"
    }

    // Crypto
    private val asconCipher = AsconCipher()

    // Bluetooth - initialized lazily to avoid crashes
    private val bluetoothManager: BluetoothManager? by lazy {
        try {
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get BluetoothManager: ${e.message}")
            null
        }
    }
    
    private val bluetoothAdapter: BluetoothAdapter? 
        get() = bluetoothManager?.adapter
    
    private val bleScanner: BluetoothLeScanner? 
        get() = bluetoothAdapter?.bluetoothLeScanner

    private var bluetoothGatt: BluetoothGatt? = null
    private var cmdCharacteristic: BluetoothGattCharacteristic? = null

    // State flows
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _temperature = MutableStateFlow<Float?>(null)
    val temperature: StateFlow<Float?> = _temperature.asStateFlow()

    private val _flameAlarm = MutableStateFlow(false)
    val flameAlarm: StateFlow<Boolean> = _flameAlarm.asStateFlow()

    private val _scanResults = MutableStateFlow<List<ScanResult>>(emptyList())
    val scanResults: StateFlow<List<ScanResult>> = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Debug: show last received raw data
    private val _lastRawData = MutableStateFlow<String?>(null)
    val lastRawData: StateFlow<String?> = _lastRawData.asStateFlow()

    enum class ConnectionState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,
        READY
    }

    // ==================== SCANNING ====================

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            Log.d(TAG, "onScanResult called!")
            try {
                val deviceName = result.device.name
                val deviceAddress = result.device.address
                
                Log.d(TAG, "Device found: name=$deviceName, address=$deviceAddress, rssi=${result.rssi}")
                
                // Show ALL devices (with or without name) for testing
                val currentList = _scanResults.value.toMutableList()
                if (currentList.none { it.device.address == deviceAddress }) {
                    currentList.add(result)
                    _scanResults.value = currentList
                    Log.d(TAG, "Added device to list. Total: ${currentList.size}")
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission error in scan result: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "Error in scan result: ${e.message}")
            }
        }
        
        override fun onBatchScanResults(results: MutableList<ScanResult>?) {
            Log.d(TAG, "onBatchScanResults: ${results?.size} results")
            results?.forEach { result ->
                onScanResult(0, result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            val errorMsg = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "Scan already started"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "App registration failed"
                SCAN_FAILED_INTERNAL_ERROR -> "Internal error"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "Feature unsupported"
                else -> "Unknown error"
            }
            Log.e(TAG, "Scan failed! Error code: $errorCode - $errorMsg")
            _isScanning.value = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun startScan() {
        Log.d(TAG, "========== startScan() called ==========")
        
        // Check if Bluetooth is available
        val adapter = bluetoothAdapter
        if (adapter == null) {
            Log.e(TAG, "ERROR: Bluetooth adapter is null")
            return
        }
        Log.d(TAG, "Bluetooth adapter OK")
        
        // Check if Bluetooth is enabled
        if (!adapter.isEnabled) {
            Log.e(TAG, "ERROR: Bluetooth is not enabled")
            return
        }
        Log.d(TAG, "Bluetooth is enabled")
        
        // Check if scanner is available
        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "ERROR: BLE Scanner not available")
            return
        }
        Log.d(TAG, "BLE Scanner OK")

        try {
            _scanResults.value = emptyList()
            _isScanning.value = true
            _connectionState.value = ConnectionState.SCANNING

            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            Log.d(TAG, "Starting BLE scan...")
            // Scan without filter to find all devices
            scanner.startScan(null, settings, scanCallback)
            Log.d(TAG, "BLE scan started successfully!")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
            e.printStackTrace()
            _isScanning.value = false
            _connectionState.value = ConnectionState.DISCONNECTED
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            e.printStackTrace()
            _isScanning.value = false
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    fun stopScan() {
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Stop scan error: ${e.message}")
        }
        _isScanning.value = false
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
        Log.d(TAG, "Stopped BLE scan")
    }

    // ==================== CONNECTION ====================

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "Connected to GATT server")
                    _connectionState.value = ConnectionState.CONNECTED
                    // Request larger MTU for full data transfer (ESP32 sends ~72 bytes)
                    gatt.requestMtu(512)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Disconnected from GATT server")
                    _connectionState.value = ConnectionState.DISCONNECTED
                    _temperature.value = null
                    _flameAlarm.value = false
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU changed to $mtu, status: $status")
            // Now discover services after MTU is set
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered")
                setupCharacteristics(gatt)
            } else {
                Log.e(TAG, "Service discovery failed: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(TAG, "onCharacteristicChanged (new API) - ${value.size} bytes")
            handleNotification(characteristic.uuid, value)
        }

        // For older API levels (Android < 13) - THIS IS USED ON ANDROID 10
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            // IMPORTANT: Read fresh value from characteristic
            val value = characteristic.value
            Log.d(TAG, "onCharacteristicChanged (old API) - ${value?.size ?: 0} bytes")
            if (value != null && value.isNotEmpty()) {
                handleNotification(characteristic.uuid, value)
            } else {
                Log.e(TAG, "Characteristic value is null or empty!")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Command sent successfully")
            } else {
                Log.e(TAG, "Failed to send command: $status")
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Descriptor written for ${descriptor.characteristic.uuid}")
            } else {
                Log.e(TAG, "Descriptor write failed: $status")
            }
            // Write next pending descriptor
            writeNextDescriptor(gatt)
        }
    }

    fun connect(device: BluetoothDevice) {
        stopScan()
        _connectionState.value = ConnectionState.CONNECTING
        Log.d(TAG, "Connecting to ${device.address}")
        
        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        bluetoothGatt?.disconnect()
    }

    private var pendingDescriptorWrites = mutableListOf<BluetoothGattDescriptor>()

    private fun setupCharacteristics(gatt: BluetoothGatt) {
        val service = gatt.getService(BleConstants.SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Service not found!")
            return
        }
        Log.d(TAG, "Service found: ${service.uuid}")

        // Get command characteristic for writing
        cmdCharacteristic = service.getCharacteristic(BleConstants.CMD_UUID)
        Log.d(TAG, "CMD characteristic: ${cmdCharacteristic?.uuid}")
        
        // Queue notifications for temperature and alarm
        pendingDescriptorWrites.clear()
        
        val tempChar = service.getCharacteristic(BleConstants.TEMP_UUID)
        if (tempChar != null) {
            Log.d(TAG, "TEMP characteristic found: ${tempChar.uuid}")
            queueNotificationEnable(gatt, tempChar)
        } else {
            Log.e(TAG, "TEMP characteristic NOT found!")
        }

        val alarmChar = service.getCharacteristic(BleConstants.ALARM_UUID)
        if (alarmChar != null) {
            Log.d(TAG, "ALARM characteristic found: ${alarmChar.uuid}")
            queueNotificationEnable(gatt, alarmChar)
        } else {
            Log.e(TAG, "ALARM characteristic NOT found!")
        }

        // Start writing descriptors one by one
        writeNextDescriptor(gatt)
    }

    private fun queueNotificationEnable(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(BleConstants.CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            pendingDescriptorWrites.add(descriptor)
            Log.d(TAG, "Queued notification enable for ${characteristic.uuid}")
        } else {
            Log.e(TAG, "CCCD descriptor not found for ${characteristic.uuid}")
        }
    }

    private fun writeNextDescriptor(gatt: BluetoothGatt) {
        if (pendingDescriptorWrites.isNotEmpty()) {
            val descriptor = pendingDescriptorWrites.removeAt(0)
            val success = gatt.writeDescriptor(descriptor)
            Log.d(TAG, "Writing descriptor for ${descriptor.characteristic.uuid}: $success")
        } else {
            _connectionState.value = ConnectionState.READY
            Log.d(TAG, "All descriptors written - READY for notifications")
        }
    }

    private fun handleNotification(uuid: java.util.UUID, value: ByteArray) {
        // Convert raw bytes to string - ESP32 sends ASCII hex characters
        val hexData = String(value, Charsets.US_ASCII).trim()
        
        Log.d(TAG, "========== NOTIFICATION RECEIVED ==========")
        Log.d(TAG, "UUID: $uuid")
        Log.d(TAG, "Raw bytes count: ${value.size}")
        Log.d(TAG, "Hex string length: ${hexData.length}")
        Log.d(TAG, "Full data: $hexData")
        
        // Update debug display with size info
        _lastRawData.value = "Size:${value.size}bytes/${hexData.length}chars"

        // Validate data length - ESP32 sends at least 64 chars (nonce 32 + tag 32)
        if (hexData.length < 64) {
            Log.e(TAG, "Data too short! Expected >= 64 chars, got ${hexData.length}")
            _lastRawData.value = "TOO SHORT: ${value.size}bytes. Need MTU>72"
            return
        }

        when (uuid) {
            BleConstants.TEMP_UUID -> {
                Log.d(TAG, ">>> Processing TEMPERATURE")
                val decrypted = asconCipher.decrypt(hexData)
                Log.d(TAG, "Decrypted: '$decrypted'")
                
                if (decrypted != null) {
                    val temp = decrypted.toFloatOrNull()
                    if (temp != null) {
                        _temperature.value = temp
                        _lastRawData.value = "OK: $temp°C"
                        Log.d(TAG, "✓ Temperature set to: $temp°C")
                    } else {
                        Log.e(TAG, "Failed to parse float from: '$decrypted'")
                        _lastRawData.value = "Parse fail: $decrypted"
                    }
                } else {
                    Log.e(TAG, "Decryption returned null - TAG verification failed!")
                    _lastRawData.value = "Decrypt FAILED"
                }
            }
            BleConstants.ALARM_UUID -> {
                Log.d(TAG, ">>> Processing ALARM")
                val decrypted = asconCipher.decrypt(hexData)
                Log.d(TAG, "Decrypted alarm: '$decrypted'")
                
                if (decrypted == "FLAME") {
                    _flameAlarm.value = true
                    Log.w(TAG, "🔥 FLAME DETECTED!")
                }
            }
            else -> {
                Log.d(TAG, "Unknown characteristic UUID: $uuid")
            }
        }
        Log.d(TAG, "============================================")
    }

    // ==================== COMMANDS ====================

    fun sendCommand(command: String) {
        val gatt = bluetoothGatt
        val cmdChar = cmdCharacteristic

        if (gatt == null || cmdChar == null) {
            Log.e(TAG, "Not connected or characteristic not found")
            return
        }

        // Encrypt command using ASCON
        val encryptedHex = asconCipher.encrypt(command)
        Log.d(TAG, "Sending encrypted command: $command -> $encryptedHex")

        cmdChar.value = encryptedHex.toByteArray(Charsets.US_ASCII)
        cmdChar.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        gatt.writeCharacteristic(cmdChar)
    }

    fun sendLedOn() = sendCommand("LED_ON")
    fun sendLedOff() = sendCommand("LED_OFF")
    fun sendBuzzerOff() {
        sendCommand("BUZZER_OFF")
        _flameAlarm.value = false
    }

    // ==================== CLEANUP ====================

    fun release() {
        stopScan()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}
