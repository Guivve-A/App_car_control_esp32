package com.example.bluex.bluetooth

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.*

sealed class ConnectionState {
    data object Disconnected : ConnectionState()
    data object Connecting : ConnectionState()
    data object Connected : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class BluetoothRepository(private val context: Context) {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val deviceName = "Car-ESP32"

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages = _incomingMessages.asSharedFlow()

    private var socket: BluetoothSocket? = null
    private var listenJob: Job? = null
    private var reconnectJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var autoReconnect = true
    private val maxReconnectAttempts = 5
    private val reconnectDelayMs = 3000L

    fun connect(): Boolean {
        val bluetoothManager =
            context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = bluetoothManager?.adapter

        if (adapter == null || !adapter.isEnabled) {
            _connectionState.value = ConnectionState.Error("Bluetooth no disponible")
            return false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    context, Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                _connectionState.value = ConnectionState.Error("Permiso Bluetooth denegado")
                return false
            }
        }

        val pairedDevices: Set<BluetoothDevice>? = adapter.bondedDevices
        val device = pairedDevices?.find { it.name == deviceName }

        if (device == null) {
            _connectionState.value = ConnectionState.Error("$deviceName no encontrado")
            return false
        }

        _connectionState.value = ConnectionState.Connecting

        return try {
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            socket?.connect()
            _connectionState.value = ConnectionState.Connected
            autoReconnect = true
            startListening()
            true
        } catch (e: IOException) {
            Log.e(TAG, "Error al conectar", e)
            try { socket?.close() } catch (_: IOException) {}
            socket = null
            _connectionState.value = ConnectionState.Error("No se pudo conectar")
            false
        }
    }

    fun sendCommand(command: String) {
        if (_connectionState.value !is ConnectionState.Connected) return
        try {
            val payload = "$command\n"
            socket?.outputStream?.let { out ->
                out.write(payload.toByteArray(Charsets.UTF_8))
                out.flush()
            }
            Log.d(TAG, "Enviado: $command")
        } catch (e: IOException) {
            Log.e(TAG, "Error al enviar", e)
            handleConnectionLost()
        }
    }

    fun disconnect() {
        autoReconnect = false
        reconnectJob?.cancel()
        listenJob?.cancel()
        try { socket?.close() } catch (e: IOException) {
            Log.e(TAG, "Error al cerrar", e)
        }
        socket = null
        _connectionState.value = ConnectionState.Disconnected
    }

    fun release() {
        disconnect()
        scope.cancel()
    }

    private fun startListening() {
        listenJob?.cancel()
        listenJob = scope.launch {
            val inputStream = socket?.inputStream
            val buffer = ByteArray(1024)

            while (isActive && socket?.isConnected == true) {
                try {
                    val bytes = inputStream?.read(buffer) ?: -1
                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes).trim()
                        if (message.isNotEmpty()) {
                            _incomingMessages.emit(message)
                        }
                    }
                } catch (_: IOException) {
                    break
                }
            }

            if (isActive) {
                handleConnectionLost()
            }
        }
    }

    private fun handleConnectionLost() {
        _connectionState.value = ConnectionState.Disconnected
        listenJob?.cancel()
        try { socket?.close() } catch (_: IOException) {}
        socket = null

        if (autoReconnect) {
            attemptReconnect()
        }
    }

    private fun attemptReconnect() {
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            for (attempt in 1..maxReconnectAttempts) {
                if (!isActive || !autoReconnect) return@launch

                _connectionState.value = ConnectionState.Reconnecting(attempt)
                Log.i(TAG, "Intento de reconexion $attempt/$maxReconnectAttempts")

                delay(reconnectDelayMs)

                if (connect()) {
                    Log.i(TAG, "Reconexion exitosa en intento $attempt")
                    return@launch
                }
            }

            _connectionState.value = ConnectionState.Error("Reconexion fallida")
        }
    }

    companion object {
        private const val TAG = "BluetoothRepository"
    }
}
