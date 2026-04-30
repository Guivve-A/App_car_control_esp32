package com.example.bluex

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bluex.bluetooth.ConnectionState
import com.example.bluex.ui.components.*
import com.example.bluex.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BluexTheme {
                BluexApp()
            }
        }
    }
}

// ─── Main App ───────────────────────────────────────────────

@Composable
fun BluexApp(viewModel: BluexViewModel = viewModel()) {
    val context = LocalContext.current
    val connectionState by viewModel.bluetoothRepo.connectionState.collectAsState()
    val currentMode by viewModel.currentMode.collectAsState()
    val lastResponse by viewModel.lastResponse.collectAsState()
    val hora by viewModel.telemetryHora.collectAsState()
    val tiempo by viewModel.telemetryTiempo.collectAsState()
    val nombre by viewModel.telemetryNombre.collectAsState()
    val greeting by viewModel.currentGreeting.collectAsState()
    val eyesOn by viewModel.eyesOn.collectAsState()
    val lcdText by viewModel.lcdText.collectAsState()
    val waitingForGreeting by viewModel.waitingForGreeting.collectAsState()
    val isVoiceListening by viewModel.voiceControl.isListening.collectAsState()
    val recognizedText by viewModel.voiceControl.recognizedText.collectAsState()
    val armsUp by viewModel.armsUp.collectAsState()
    val headPosition by viewModel.headPosition.collectAsState()

    val isConnected = connectionState is ConnectionState.Connected

    // Permissions
    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.RECORD_AUDIO)
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.entries.all { it.value }) {
            Toast.makeText(context, "Algunos permisos fueron denegados", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch(permissionsToRequest)
    }

    // Auto telemetry sync when in MODE2
    LaunchedEffect(currentMode, isConnected) {
        if (isConnected && currentMode == AppMode.MODE2) {
            viewModel.startTelemetryAutoSync()
        } else {
            viewModel.stopTelemetryAutoSync()
        }
    }

    Scaffold(
        containerColor = BluexDarkBg,
        topBar = {
            BluexTopBar(
                connectionState = connectionState,
                onConnect = { viewModel.connect() },
                onDisconnect = { viewModel.disconnect() }
            )
        },
        floatingActionButton = {
            if (isConnected) {
                VoiceFab(
                    isListening = isVoiceListening,
                    onToggle = {
                        if (isVoiceListening) {
                            viewModel.voiceControl.stopListening()
                        } else {
                            viewModel.voiceControl.startListening()
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (isConnected) {
                // Eyes toggle + mode tabs
                EyesToggleRow(
                    eyesOn = eyesOn,
                    onToggle = { viewModel.toggleEyes(it) }
                )

                ModeTabBar(
                    currentMode = currentMode,
                    onModeSelected = { viewModel.switchMode(it) }
                )
            }

            // Voice status banner
            AnimatedVisibility(
                visible = isVoiceListening || recognizedText.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                VoiceStatusBanner(
                    isListening = isVoiceListening,
                    recognizedText = recognizedText
                )
            }

            // Content area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (isConnected) {
                    AnimatedContent(
                        targetState = currentMode,
                        transitionSpec = {
                            fadeIn(tween(300)) togetherWith fadeOut(tween(200))
                        },
                        label = "modeTransition"
                    ) { mode ->
                        when (mode) {
                            AppMode.STANDBY -> StandByScreen()
                            AppMode.DRIVING -> DrivingScreen(
                                onSendCommand = { viewModel.sendCommand(it) }
                            )
                            AppMode.MODE1 -> GreetingsScreen(
                                lcdText = lcdText,
                                onTextChange = { viewModel.updateLcdText(it) },
                                onSend = { viewModel.sendGreeting(it) },
                                greeting = greeting,
                                waiting = waitingForGreeting
                            )
                            AppMode.MODE2 -> TelemetryScreen(
                                hora = hora,
                                tiempo = tiempo,
                                nombre = nombre,
                                onSync = { viewModel.syncTelemetry() }
                            )
                            AppMode.MOVIMIENTOS -> MovimientosScreen(
                                armsUp = armsUp,
                                headPosition = headPosition,
                                onArmsToggle = { viewModel.toggleArms() },
                                onHeadPositionChange = { viewModel.setHeadPosition(it) }
                            )
                        }
                    }
                } else {
                    DisconnectedScreen()
                }
            }

            // ESP32 response footer
            AnimatedVisibility(
                visible = lastResponse.isNotEmpty() && isConnected,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Text(
                    text = "ESP32: $lastResponse",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BluexDarkSurface)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = BluexTextTertiary
                )
            }
        }
    }
}

// ─── Top Bar ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluexTopBar(
    connectionState: ConnectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = connectionState is ConnectionState.Connected
    val isConnecting = connectionState is ConnectionState.Connecting
            || connectionState is ConnectionState.Reconnecting

    CenterAlignedTopAppBar(
        title = {
            Text(
                "BLUEX",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = BluexTextPrimary
            )
        },
        navigationIcon = {
            AnimatedConnectionIndicator(
                connectionState = connectionState,
                modifier = Modifier.padding(start = 16.dp)
            )
        },
        actions = {
            TextButton(
                onClick = { if (isConnected) onDisconnect() else onConnect() },
                enabled = !isConnecting
            ) {
                Text(
                    text = when {
                        isConnecting -> "..."
                        isConnected -> "Desconectar"
                        else -> "Conectar"
                    },
                    color = if (isConnected) BluexRed else BluexAccent,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = BluexDarkBg,
            titleContentColor = BluexTextPrimary
        )
    )
}

// ─── Eyes Toggle ────────────────────────────────────────────

@Composable
fun EyesToggleRow(eyesOn: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            "Ojos",
            style = MaterialTheme.typography.labelLarge,
            color = BluexTextSecondary
        )
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = eyesOn,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedThumbColor = BluexAccent,
                checkedTrackColor = BluexAccent.copy(alpha = 0.3f),
                uncheckedThumbColor = BluexTextTertiary,
                uncheckedTrackColor = BluexDarkSurfaceElevated
            )
        )
    }
}

// ─── Mode Tab Bar ───────────────────────────────────────────

@Composable
fun ModeTabBar(currentMode: AppMode, onModeSelected: (AppMode) -> Unit) {
    val modes = AppMode.entries

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        modes.forEach { mode ->
            val isSelected = currentMode == mode
            val animatedBg by animateColorAsState(
                targetValue = if (isSelected) BluexAccent else Color.Transparent,
                animationSpec = tween(250),
                label = "tabBg"
            )
            val animatedTextColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else BluexTextSecondary,
                animationSpec = tween(250),
                label = "tabText"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(animatedBg)
                    .then(
                        if (!isSelected) Modifier.border(
                            0.5.dp,
                            BluexSeparator,
                            RoundedCornerShape(12.dp)
                        ) else Modifier
                    )
                    .clickable { onModeSelected(mode) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = mode.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = animatedTextColor,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ─── Voice FAB ──────────────────────────────────────────────

@Composable
fun VoiceFab(isListening: Boolean, onToggle: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue = if (isListening) 1.15f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "fabScale"
    )
    val containerColor by animateColorAsState(
        targetValue = if (isListening) BluexRed else BluexAccent,
        animationSpec = tween(300),
        label = "fabColor"
    )

    FloatingActionButton(
        onClick = onToggle,
        containerColor = containerColor,
        contentColor = Color.White,
        modifier = Modifier.scale(scale),
        shape = CircleShape
    ) {
        Icon(
            imageVector = if (isListening) Icons.Default.Close else Icons.Default.Mic,
            contentDescription = if (isListening) "Detener escucha" else "Comando de voz",
            modifier = Modifier.size(24.dp)
        )
    }
}

// ─── Voice Status Banner ────────────────────────────────────

@Composable
fun VoiceStatusBanner(isListening: Boolean, recognizedText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isListening) BluexAccent.copy(alpha = 0.1f)
                else BluexDarkSurfaceElevated
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isListening) {
            val infiniteTransition = rememberInfiniteTransition(label = "mic")
            val alpha by infiniteTransition.animateFloat(
                initialValue = 0.4f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(600), RepeatMode.Reverse
                ),
                label = "micAlpha"
            )
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                tint = BluexAccent.copy(alpha = alpha),
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Escuchando...",
                style = MaterialTheme.typography.labelMedium,
                color = BluexAccent
            )
        } else if (recognizedText.isNotEmpty()) {
            Icon(
                Icons.Default.RecordVoiceOver,
                contentDescription = null,
                tint = BluexTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                recognizedText,
                style = MaterialTheme.typography.labelMedium,
                color = BluexTextSecondary,
                maxLines = 1
            )
        }
    }
}

// ─── Disconnected Screen ────────────────────────────────────

@Composable
fun DisconnectedScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "robot")
            val offsetY by infiniteTransition.animateFloat(
                initialValue = -6f,
                targetValue = 6f,
                animationSpec = infiniteRepeatable(
                    tween(2000, easing = EaseInOut),
                    RepeatMode.Reverse
                ),
                label = "float"
            )

            Text(
                text = "WALL-E",
                style = MaterialTheme.typography.displayLarge,
                color = BluexAccent,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = offsetY.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Conecta tu ESP32 para comenzar",
                style = MaterialTheme.typography.bodyLarge,
                color = BluexTextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Buscando: Car-ESP32",
                style = MaterialTheme.typography.bodySmall,
                color = BluexTextTertiary
            )
        }
    }
}

// ─── StandBy Screen ─────────────────────────────────────────

@Composable
fun StandByScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "star")
            val starScale by infiniteTransition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    tween(1500, easing = EaseInOut),
                    RepeatMode.Reverse
                ),
                label = "starPulse"
            )

            Icon(
                Icons.Default.Star,
                contentDescription = null,
                modifier = Modifier
                    .size(72.dp)
                    .scale(starScale),
                tint = BluexYellow
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                "Bienvenido",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = BluexTextPrimary
            )

            Spacer(modifier = Modifier.height(16.dp))

            GlassCardAccent(
                accentColor = BluexAccent,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 20.dp
            ) {
                Text(
                    "Paga 0.50 ctvs por un saludo",
                    style = MaterialTheme.typography.titleLarge,
                    color = BluexTextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// ─── Driving Screen (Joystick + Special Moves) ──────────────

@Composable
fun DrivingScreen(onSendCommand: (String) -> Unit) {
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Control",
            style = MaterialTheme.typography.headlineMedium,
            color = BluexTextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Joystick
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                JoystickControl(
                    onDirectionChanged = { direction ->
                        val cmd = when (direction) {
                            JoystickDirection.FORWARD -> "F"
                            JoystickDirection.BACKWARD -> "B"
                            JoystickDirection.LEFT -> "L"
                            JoystickDirection.RIGHT -> "R"
                            JoystickDirection.NONE -> "S"
                        }
                        onSendCommand(cmd)
                    },
                    onRelease = { onSendCommand("S") }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick turn buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            PremiumButton(
                text = "90 Izq",
                icon = Icons.Default.RotateLeft,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSendCommand("G")
                }
            )
            PremiumButton(
                text = "90 Der",
                icon = Icons.Default.RotateRight,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    onSendCommand("H")
                }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Special Moves section
        Text(
            "Movimientos Especiales",
            style = MaterialTheme.typography.titleLarge,
            color = BluexTextPrimary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Special moves grid (2x2)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpecialMoveCard(
                title = "Explorar",
                subtitle = "Movimiento aleatorio",
                icon = Icons.Default.Explore,
                accentColor = ExploreColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onSendCommand("X")
                }
            )
            SpecialMoveCard(
                title = "Bailar",
                subtitle = "Secuencia de baile",
                icon = Icons.Default.MusicNote,
                accentColor = DanceColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onSendCommand("D")
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpecialMoveCard(
                title = "Vigilar",
                subtitle = "Escaneo 360",
                icon = Icons.Default.Radar,
                accentColor = ScanColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onSendCommand("V")
                }
            )
            SpecialMoveCard(
                title = "Susto",
                subtitle = "Avance rapido",
                icon = Icons.Default.FlashOn,
                accentColor = PrankColor,
                modifier = Modifier.weight(1f),
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    onSendCommand("P")
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

// ─── Special Move Card ──────────────────────────────────────

@Composable
fun SpecialMoveCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "moveScale"
    )

    GlassCardAccent(
        accentColor = accentColor,
        modifier = modifier
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
        cornerRadius = 16.dp,
        contentPadding = 14.dp
    ) {
        Column {
            Icon(
                icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = BluexTextPrimary,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = BluexTextTertiary
            )
        }
    }
}

// ─── Premium Button ─────────────────────────────────────────

@Composable
fun PremiumButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.75f),
        label = "btnScale"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(48.dp)
            .scale(scale),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BluexDarkSurfaceElevated,
            contentColor = BluexTextPrimary
        ),
        interactionSource = interactionSource,
        border = BorderStroke(0.5.dp, GlassBorder)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

// ─── Greetings Screen ───────────────────────────────────────

@Composable
fun GreetingsScreen(
    lcdText: String,
    onTextChange: (String) -> Unit,
    onSend: (String) -> Unit,
    greeting: String,
    waiting: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Saludos",
            style = MaterialTheme.typography.headlineMedium,
            color = BluexTextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            "Escribe un nombre para saludar",
            style = MaterialTheme.typography.bodyMedium,
            color = BluexTextSecondary
        )

        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = lcdText,
            onValueChange = onTextChange,
            label = { Text("Nombre o saludo") },
            placeholder = { Text("Ej: Juan") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BluexAccent,
                unfocusedBorderColor = GlassBorder,
                focusedLabelColor = BluexAccent,
                unfocusedLabelColor = BluexTextTertiary,
                cursorColor = BluexAccent,
                focusedTextColor = BluexTextPrimary,
                unfocusedTextColor = BluexTextPrimary,
                focusedPlaceholderColor = BluexTextTertiary,
                unfocusedPlaceholderColor = BluexTextTertiary
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { if (lcdText.trim().isNotEmpty()) onSend(lcdText.trim()) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BluexAccent)
        ) {
            Text(
                "Pedir Saludo",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        AnimatedVisibility(
            visible = waiting,
            enter = fadeIn() + scaleIn(initialScale = 0.9f),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = BluexAccent,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "Generando saludo...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = BluexTextSecondary
                )
            }
        }

        AnimatedVisibility(
            visible = !waiting && greeting.isNotEmpty(),
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut()
        ) {
            GlassCardAccent(
                accentColor = BluexGreen,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = 20.dp
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Saludo recibido",
                        style = MaterialTheme.typography.labelMedium,
                        color = BluexGreen
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        greeting,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = BluexTextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Reproduciendo voz robotica...",
                        style = MaterialTheme.typography.bodySmall,
                        color = BluexTextTertiary
                    )
                }
            }
        }
    }
}

// ─── Telemetry Screen ───────────────────────────────────────

@Composable
fun TelemetryScreen(
    hora: String,
    tiempo: String,
    nombre: String,
    onSync: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Telemetria",
                style = MaterialTheme.typography.headlineMedium,
                color = BluexTextPrimary,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onSync) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Sincronizar",
                    tint = BluexAccent
                )
            }
        }

        TelemetryGlassCard(
            label = "Hora del Telefono",
            value = hora,
            icon = Icons.Outlined.Schedule,
            accentColor = BluexAccent
        )
        TelemetryGlassCard(
            label = "Clima",
            value = tiempo,
            icon = Icons.Outlined.WbSunny,
            accentColor = BluexOrange
        )
        TelemetryGlassCard(
            label = "Nombre Robot",
            value = nombre,
            icon = Icons.Outlined.SmartToy,
            accentColor = BluexPurple
        )

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Datos sincronizados automaticamente cada minuto.",
            style = MaterialTheme.typography.bodySmall,
            color = BluexTextTertiary
        )
    }
}

// ─── Movimientos Screen ─────────────────────────────────────

@Composable
fun MovimientosScreen(
    armsUp: Boolean,
    headPosition: HeadPosition,
    onArmsToggle: () -> Unit,
    onHeadPositionChange: (HeadPosition) -> Unit
) {
    val view = LocalView.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Movimientos",
            style = MaterialTheme.typography.headlineMedium,
            color = BluexTextPrimary,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Section 1: Brazos
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Brazos",
                    style = MaterialTheme.typography.titleMedium,
                    color = BluexTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                val armsContainerColor by animateColorAsState(
                    targetValue = if (armsUp) BluexAccent else Color.Transparent,
                    animationSpec = tween(250),
                    label = "armsContainer"
                )
                val armsContentColor by animateColorAsState(
                    targetValue = if (armsUp) Color.White else BluexTextPrimary,
                    animationSpec = tween(250),
                    label = "armsContent"
                )
                val armsBorderColor by animateColorAsState(
                    targetValue = if (armsUp) BluexAccent else GlassBorder,
                    animationSpec = tween(250),
                    label = "armsBorder"
                )

                Button(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onArmsToggle()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = armsContainerColor,
                        contentColor = armsContentColor
                    ),
                    border = BorderStroke(1.dp, armsBorderColor)
                ) {
                    Icon(
                        imageVector = if (armsUp) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Mover Brazos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (armsUp) "Brazos levantados" else "Brazos en reposo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (armsUp) BluexAccent else BluexTextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Section 2: Cabeza
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = 20.dp
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Cabeza",
                    style = MaterialTheme.typography.titleMedium,
                    color = BluexTextSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HeadButton(
                        label = "← Izquierda",
                        isSelected = headPosition == HeadPosition.LEFT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onHeadPositionChange(HeadPosition.LEFT)
                        }
                    )
                    HeadButton(
                        label = "Centro",
                        isSelected = headPosition == HeadPosition.CENTER,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onHeadPositionChange(HeadPosition.CENTER)
                        }
                    )
                    HeadButton(
                        label = "Derecha →",
                        isSelected = headPosition == HeadPosition.RIGHT,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            onHeadPositionChange(HeadPosition.RIGHT)
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun HeadButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) BluexAccent else Color.Transparent,
        animationSpec = tween(250),
        label = "headBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else BluexTextSecondary,
        animationSpec = tween(250),
        label = "headText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) BluexAccent else GlassBorder,
        animationSpec = tween(250),
        label = "headBorder"
    )

    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = bgColor,
            contentColor = textColor
        ),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ─── Telemetry Glass Card ────────────────────────────────────

@Composable
fun TelemetryGlassCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = 16.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = BluexTextTertiary
                )
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = BluexTextPrimary
                )
            }
        }
    }
}
