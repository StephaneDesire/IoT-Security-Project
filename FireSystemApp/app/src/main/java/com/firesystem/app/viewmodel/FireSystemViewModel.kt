package com.firesystem.app.viewmodel

import android.app.Application
import android.bluetooth.le.ScanResult
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.firesystem.app.ble.BleManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for Fire System App
 * Manages BLE connection state and sensor data
 */
class FireSystemViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FireSystemViewModel"
    }

    private val bleManager: BleManager by lazy {
        BleManager(application.applicationContext)
    }

    // Expose BLE state
    val connectionState: StateFlow<BleManager.ConnectionState> = bleManager.connectionState
    val temperature: StateFlow<Float?> = bleManager.temperature
    val flameAlarm: StateFlow<Boolean> = bleManager.flameAlarm
    val scanResults: StateFlow<List<ScanResult>> = bleManager.scanResults
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val lastRawData: StateFlow<String?> = bleManager.lastRawData

    // ==================== SCANNING ====================

    fun startScan() {
        try {
            Log.d(TAG, "startScan() called from ViewModel")
            bleManager.startScan()
        } catch (e: Exception) {
            Log.e(TAG, "Error starting scan: ${e.message}")
        }
    }

    fun stopScan() {
        try {
            bleManager.stopScan()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping scan: ${e.message}")
        }
    }

    // ==================== CONNECTION ====================

    fun connect(scanResult: ScanResult) {
        try {
            bleManager.connect(scanResult.device)
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            bleManager.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting: ${e.message}")
        }
    }

    // ==================== COMMANDS ====================

    fun ledOn() {
        viewModelScope.launch {
            try {
                bleManager.sendLedOn()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending LED_ON: ${e.message}")
            }
        }
    }

    fun ledOff() {
        viewModelScope.launch {
            try {
                bleManager.sendLedOff()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending LED_OFF: ${e.message}")
            }
        }
    }

    fun buzzerOff() {
        viewModelScope.launch {
            try {
                bleManager.sendBuzzerOff()
            } catch (e: Exception) {
                Log.e(TAG, "Error sending BUZZER_OFF: ${e.message}")
            }
        }
    }

    // ==================== CLEANUP ====================

    override fun onCleared() {
        super.onCleared()
        try {
            bleManager.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing BleManager: ${e.message}")
        }
    }
}
