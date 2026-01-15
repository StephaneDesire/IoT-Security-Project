package com.firesystem.app

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.firesystem.app.ui.FireSystemApp
import com.firesystem.app.ui.theme.FireSystemTheme
import com.firesystem.app.viewmodel.FireSystemViewModel

/**
 * Main Activity - Entry point for Fire System App
 * Handles BLE permissions and Bluetooth enable requests
 */
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private val viewModel: FireSystemViewModel by viewModels()

    // Required permissions based on Android version
    private val requiredPermissions: Array<String>
        get() {
            val permissions = mutableListOf<String>()
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ (API 31+)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            
            // Location is always needed for BLE scanning
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            
            return permissions.toTypedArray()
        }

    // Permission request launcher
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        Log.d(TAG, "Permissions result: $permissions")
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All permissions granted")
            checkBluetoothEnabled()
        } else {
            Log.e(TAG, "Some permissions denied")
            Toast.makeText(this, "BLE permissions required! Please grant in Settings.", Toast.LENGTH_LONG).show()
        }
    }

    // Bluetooth enable request launcher
    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            Toast.makeText(this, "Bluetooth must be enabled", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate - Android SDK: ${Build.VERSION.SDK_INT}")

        setContent {
            FireSystemTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    FireSystemApp(viewModel)
                }
            }
        }

        // Check permissions on start
        checkAndRequestPermissions()
    }

    private fun checkAndRequestPermissions() {
        Log.d(TAG, "Checking permissions...")
        
        val missingPermissions = requiredPermissions.filter {
            val granted = ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $it: ${if (granted) "GRANTED" else "DENIED"}")
            !granted
        }

        if (missingPermissions.isEmpty()) {
            Log.d(TAG, "All permissions already granted")
            checkBluetoothEnabled()
        } else {
            Log.d(TAG, "Requesting permissions: $missingPermissions")
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun checkBluetoothEnabled() {
        try {
            val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as? BluetoothManager
            val bluetoothAdapter = bluetoothManager?.adapter

            if (bluetoothAdapter == null) {
                Log.e(TAG, "Bluetooth adapter is null")
                Toast.makeText(this, "Bluetooth not supported on this device", Toast.LENGTH_LONG).show()
                return
            }

            if (!bluetoothAdapter.isEnabled) {
                Log.d(TAG, "Bluetooth is disabled, requesting enable")
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothEnableLauncher.launch(enableBtIntent)
            } else {
                Log.d(TAG, "Bluetooth is enabled and ready")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Bluetooth: ${e.message}")
        }
    }
}
