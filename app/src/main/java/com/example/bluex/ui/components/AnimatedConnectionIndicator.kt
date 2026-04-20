package com.example.bluex.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import com.example.bluex.bluetooth.ConnectionState
import com.example.bluex.ui.theme.*

@Composable
fun AnimatedConnectionIndicator(
    connectionState: ConnectionState,
    modifier: Modifier = Modifier
) {
    val (color, label) = when (connectionState) {
        is ConnectionState.Connected -> BluexGreen to "Conectado"
        is ConnectionState.Connecting -> BluexOrange to "Conectando..."
        is ConnectionState.Reconnecting -> BluexYellow to "Reconectando (${connectionState.attempt})"
        is ConnectionState.Error -> BluexRed to connectionState.message
        is ConnectionState.Disconnected -> BluexRed to "Desconectado"
    }

    val isAnimating = connectionState is ConnectionState.Connecting
            || connectionState is ConnectionState.Reconnecting

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAnimating) 1.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isAnimating) 0.3f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val animatedColor by animateColorAsState(
        targetValue = color,
        animationSpec = tween(400),
        label = "stateColor"
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Pulse ring
            if (isAnimating) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .scale(pulseScale)
                        .background(
                            animatedColor.copy(alpha = pulseAlpha * 0.4f),
                            CircleShape
                        )
                )
            }
            // Core dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(animatedColor, CircleShape)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = animatedColor
        )
    }
}
