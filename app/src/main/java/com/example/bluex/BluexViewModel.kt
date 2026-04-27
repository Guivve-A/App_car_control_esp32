package com.example.bluex

import android.app.Application
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluex.bluetooth.BluetoothRepository
import com.example.bluex.bluetooth.ConnectionState
import com.example.bluex.tts.*
import com.example.bluex.voice.VoiceControlManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*

enum class HeadPosition { CENTER, LEFT, RIGHT }

enum class AppMode(val displayName: String, val espCommand: String) {
    STANDBY("Inicio", "STANDBY"),
    DRIVING("Control", "DRIVING"),
    MODE1("Saludos", "MODE1"),
    MODE2("Telemetria", "MODE2"),
    MOVIMIENTOS("Movimientos", "MOVIMIENTOS");
}

class BluexViewModel(application: Application) : AndroidViewModel(application) {

    val bluetoothRepo = BluetoothRepository(application)
    val voiceControl = VoiceControlManager(application)

    private val _currentMode = MutableStateFlow(AppMode.STANDBY)
    val currentMode = _currentMode.asStateFlow()

    private val _telemetryHora = MutableStateFlow("--:--")
    val telemetryHora = _telemetryHora.asStateFlow()

    private val _telemetryTiempo = MutableStateFlow("--")
    val telemetryTiempo = _telemetryTiempo.asStateFlow()

    private val _telemetryNombre = MutableStateFlow("--")
    val telemetryNombre = _telemetryNombre.asStateFlow()

    private val _currentGreeting = MutableStateFlow("")
    val currentGreeting = _currentGreeting.asStateFlow()

    private val _lastResponse = MutableStateFlow("")
    val lastResponse = _lastResponse.asStateFlow()

    private val _eyesOn = MutableStateFlow(false)
    val eyesOn = _eyesOn.asStateFlow()

    private val _armsUp = MutableStateFlow(false)
    val armsUp = _armsUp.asStateFlow()

    private val _headPosition = MutableStateFlow(HeadPosition.CENTER)
    val headPosition = _headPosition.asStateFlow()

    private val _lcdText = MutableStateFlow("")
    val lcdText = _lcdText.asStateFlow()

    private val _waitingForGreeting = MutableStateFlow(false)
    val waitingForGreeting = _waitingForGreeting.asStateFlow()

    private var clockJob: Job? = null
    private var telemetryJob: Job? = null
    private var esp32MessageHandler: HandleEsp32MessageUseCase? = null

    init {
        setupEsp32MessageHandler()
        startClock()
        observeIncomingMessages()
        observeVoiceCommands()
        observeConnectionForServoReset()
        voiceControl.initialize()
    }

    private fun setupEsp32MessageHandler() {
        val app = getApplication<Application>()
        val robotVoiceStyle = RobotVoiceStyle.Default
        val fallbackEngine = FallbackTextToSpeechEngine(app)
        val piperTtsEngine = PiperTtsEngine(
            context = app,
            modelConfig = PiperModelConfig(),
            style = robotVoiceStyle
        )
        val audioPlayer = RobotAudioPlayer(style = robotVoiceStyle)
        val wallEProcessor = WallEAudioProcessor(style = robotVoiceStyle)
        val greetingManager = GreetingManager(
            scope = viewModelScope,
            piperTtsEngine = piperTtsEngine,
            robotAudioPlayer = audioPlayer,
            wallEAudioProcessor = wallEProcessor,
            fallbackTextToSpeechEngine = fallbackEngine
        )

        esp32MessageHandler = HandleEsp32MessageUseCase(
            greetingManager = greetingManager,
            onGreetingAccepted = { greetingText ->
                _currentGreeting.value = greetingText
                _waitingForGreeting.value = false
            }
        )
    }

    private fun startClock() {
        clockJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                _telemetryHora.value =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                delay(1000)
            }
        }
    }

    private fun observeIncomingMessages() {
        viewModelScope.launch {
            bluetoothRepo.incomingMessages.collect { message ->
                _lastResponse.value = message
                handleIncomingMessage(message)
            }
        }
    }

    private fun observeVoiceCommands() {
        viewModelScope.launch {
            voiceControl.command.collect { cmd ->
                sendCommand(cmd)
            }
        }
    }

    private fun handleIncomingMessage(message: String) {
        Log.d(TAG, "Recibido: $message")
        when {
            message.startsWith("TTS:") -> {
                esp32MessageHandler?.handleEsp32Message(message)
            }
            message.startsWith("HORA:") -> _telemetryHora.value = message.removePrefix("HORA:")
            message.startsWith("TIEMPO:") -> _telemetryTiempo.value = message.removePrefix("TIEMPO:")
            message.startsWith("NOMBRE:") -> _telemetryNombre.value = message.removePrefix("NOMBRE:")
            message.contains("OK:ON") || message.contains("OK:OFF") || message.contains("ERROR:CMD") -> {
                viewModelScope.launch(Dispatchers.Main) {
                    Toast.makeText(getApplication(), "ESP32: $message", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun connect() {
        viewModelScope.launch(Dispatchers.IO) {
            val success = bluetoothRepo.connect()
            if (success) {
                withContext(Dispatchers.Main) {
                    if (_currentMode.value == AppMode.MODE2) {
                        syncTelemetry()
                    } else {
                        sendCommand(_currentMode.value.espCommand)
                    }
                }
            }
        }
    }

    fun disconnect() {
        bluetoothRepo.disconnect()
        _lastResponse.value = ""
        _telemetryTiempo.value = "--"
        _telemetryNombre.value = "--"
        _currentGreeting.value = ""
        _eyesOn.value = false
    }

    fun sendCommand(command: String) {
        bluetoothRepo.sendCommand(command)
    }

    fun switchMode(mode: AppMode) {
        _currentMode.value = mode
        if (bluetoothRepo.connectionState.value is ConnectionState.Connected) {
            if (mode == AppMode.MODE2) {
                syncTelemetry()
            } else {
                sendCommand(mode.espCommand)
            }
        }
    }

    fun toggleEyes(on: Boolean) {
        _eyesOn.value = on
        sendCommand(if (on) "ON" else "OFF")
    }

    fun toggleArms() {
        _armsUp.value = !_armsUp.value
        sendCommand("BRAZOS")
    }

    fun setHeadPosition(position: HeadPosition) {
        _headPosition.value = position
        val cmd = when (position) {
            HeadPosition.LEFT -> "CABEZA_IZQ"
            HeadPosition.CENTER -> "CABEZA_CEN"
            HeadPosition.RIGHT -> "CABEZA_DER"
        }
        sendCommand(cmd)
    }

    private fun observeConnectionForServoReset() {
        viewModelScope.launch {
            bluetoothRepo.connectionState.collect { state ->
                when (state) {
                    is ConnectionState.Connected,
                    is ConnectionState.Disconnected -> {
                        _armsUp.value = false
                        _headPosition.value = HeadPosition.CENTER
                    }
                    else -> {}
                }
            }
        }
    }

    fun updateLcdText(text: String) {
        _lcdText.value = text
    }

    fun sendGreeting(name: String) {
        if (name.isBlank()) return
        sendCommand("TEXT:$name")
        _lcdText.value = ""
        _waitingForGreeting.value = true
    }

    fun syncTelemetry() {
        viewModelScope.launch(Dispatchers.IO) {
            val hora = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val clima = getClimaActual()
            val nombre = "Wall-E"

            _telemetryHora.value = hora
            _telemetryTiempo.value = clima
            _telemetryNombre.value = nombre

            sendCommand("MODE2")
            delay(100)
            sendCommand("SET_HORA:$hora")
            delay(100)
            sendCommand("SET_TIEMPO:$clima")
            delay(100)
            sendCommand("SET_NOMBRE:$nombre")
        }
    }

    fun startTelemetryAutoSync() {
        telemetryJob?.cancel()
        telemetryJob = viewModelScope.launch {
            while (isActive) {
                if (bluetoothRepo.connectionState.value is ConnectionState.Connected) {
                    syncTelemetry()
                }
                delay(60_000)
            }
        }
    }

    fun stopTelemetryAutoSync() {
        telemetryJob?.cancel()
    }

    private fun getClimaActual(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when {
            hour in 6..12 -> "22 C Despejado"
            hour in 13..18 -> "28 C Soleado"
            hour in 19..23 -> "19 C Fresco"
            else -> "15 C Noche Clara"
        }
    }

    override fun onCleared() {
        super.onCleared()
        clockJob?.cancel()
        telemetryJob?.cancel()
        esp32MessageHandler?.stop()
        esp32MessageHandler?.release()
        voiceControl.release()
        bluetoothRepo.release()
    }

    companion object {
        private const val TAG = "BluexViewModel"
    }
}
