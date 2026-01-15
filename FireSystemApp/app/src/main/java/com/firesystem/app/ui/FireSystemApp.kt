package com.firesystem.app.ui

import android.annotation.SuppressLint
import android.bluetooth.le.ScanResult
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.firesystem.app.ble.BleManager
import com.firesystem.app.viewmodel.FireSystemViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FireSystemApp(viewModel: FireSystemViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val temperature by viewModel.temperature.collectAsState()
    val flameAlarm by viewModel.flameAlarm.collectAsState()
    val scanResults by viewModel.scanResults.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val lastRawData by viewModel.lastRawData.collectAsState()

    // Alarm background color animation
    val backgroundColor by animateColorAsState(
        targetValue = if (flameAlarm) Color(0xFFFFCDD2) else MaterialTheme.colorScheme.background,
        label = "background"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🔥 Fire System") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (flameAlarm) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                ),
                actions = {
                    // Connection status indicator
                    ConnectionIndicator(connectionState)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundColor)
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (connectionState) {
                BleManager.ConnectionState.DISCONNECTED,
                BleManager.ConnectionState.SCANNING -> {
                    ScanScreen(
                        isScanning = isScanning,
                        scanResults = scanResults,
                        onStartScan = { viewModel.startScan() },
                        onStopScan = { viewModel.stopScan() },
                        onConnect = { viewModel.connect(it) }
                    )
                }
                BleManager.ConnectionState.CONNECTING -> {
                    ConnectingScreen()
                }
                BleManager.ConnectionState.CONNECTED,
                BleManager.ConnectionState.READY -> {
                    DashboardScreen(
                        temperature = temperature,
                        flameAlarm = flameAlarm,
                        isReady = connectionState == BleManager.ConnectionState.READY,
                        lastRawData = lastRawData,
                        onLedOn = { viewModel.ledOn() },
                        onLedOff = { viewModel.ledOff() },
                        onBuzzerOff = { viewModel.buzzerOff() },
                        onDisconnect = { viewModel.disconnect() }
                    )
                }
            }
        }
    }
}

@Composable
fun ConnectionIndicator(state: BleManager.ConnectionState) {
    val color = when (state) {
        BleManager.ConnectionState.DISCONNECTED -> Color.Red
        BleManager.ConnectionState.SCANNING -> Color.Yellow
        BleManager.ConnectionState.CONNECTING -> Color.Yellow
        BleManager.ConnectionState.CONNECTED -> Color.Green
        BleManager.ConnectionState.READY -> Color.Green
    }
    
    Box(
        modifier = Modifier
            .padding(end = 16.dp)
            .size(12.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(
    isScanning: Boolean,
    scanResults: List<ScanResult>,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (ScanResult) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Find ESP32 Fire System",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { if (isScanning) onStopScan() else onStartScan() },
            modifier = Modifier.fillMaxWidth(0.6f)
        ) {
            Icon(
                imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (isScanning) "Stop Scan" else "Start Scan")
        }

        if (isScanning) {
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Device list
        if (scanResults.isNotEmpty()) {
            Text(
                text = "Found Devices:",
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn {
                items(scanResults.size) { index ->
                    val result = scanResults[index]
                    val deviceName = try { result.device.name } catch (e: SecurityException) { null }
                    val deviceAddress = try { result.device.address } catch (e: SecurityException) { "Unknown" }
                    DeviceCard(
                        name = deviceName ?: "Unknown",
                        address = deviceAddress,
                        rssi = result.rssi,
                        onClick = { onConnect(result) }
                    )
                }
            }
        }
    }
}

@Composable
fun DeviceCard(
    name: String,
    address: String,
    rssi: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Bluetooth,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = name, fontWeight = FontWeight.Bold)
                Text(text = address, fontSize = 12.sp, color = Color.Gray)
            }
            Text(text = "$rssi dBm", fontSize = 12.sp, color = Color.Gray)
        }
    }
}

@Composable
fun ConnectingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(64.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text("Connecting to ESP32...", fontSize = 18.sp)
    }
}

@Composable
fun DashboardScreen(
    temperature: Float?,
    flameAlarm: Boolean,
    isReady: Boolean,
    lastRawData: String?,
    onLedOn: () -> Unit,
    onLedOff: () -> Unit,
    onBuzzerOff: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Flame Alarm Alert
        if (flameAlarm) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFD32F2F))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "🔥 FLAME DETECTED!",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onBuzzerOff,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("STOP ALARM", color = Color(0xFFD32F2F))
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Temperature Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Temperature", fontSize = 16.sp, color = Color.Gray)
                Text(
                    text = temperature?.let { "%.1f°C".format(it) } ?: "--°C",
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (temperature != null && temperature > 35) Color.Red else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Debug Card - shows raw received data
        if (lastRawData != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text("Debug - Last Received:", fontSize = 12.sp, color = Color.Gray)
                    Text(
                        text = lastRawData,
                        fontSize = 10.sp,
                        color = Color.Black,
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // LED Controls
        Text(
            "LED Control",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = onLedOn,
                modifier = Modifier.weight(1f),
                enabled = isReady,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Default.LightMode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("LED ON")
            }

            Button(
                onClick = onLedOff,
                modifier = Modifier.weight(1f),
                enabled = isReady,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF757575))
            ) {
                Icon(Icons.Default.LightMode, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("LED OFF")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Buzzer Control
        if (!flameAlarm) {
            OutlinedButton(
                onClick = onBuzzerOff,
                modifier = Modifier.fillMaxWidth(),
                enabled = isReady
            ) {
                Icon(Icons.Default.VolumeOff, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Buzzer")
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Disconnect button
        OutlinedButton(
            onClick = onDisconnect,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
        ) {
            Icon(Icons.Default.BluetoothDisabled, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Disconnect")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status
        Text(
            text = if (isReady) "✓ Connected & Ready" else "Configuring...",
            color = if (isReady) Color(0xFF4CAF50) else Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}
