package com.folio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

/** Floating focus timer shown on every screen while a session is running or paused. Drag it anywhere. */
@Composable fun TimerPill(s: Store) {
    val active = s.timerRunning || s.timerLeft < s.timerMinutes * 60
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    if (!active) return
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Row(
            Modifier.padding(top = 6.dp)
                .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
                .pointerInput(Unit) { detectDragGestures { change, drag -> change.consume(); ox += drag.x; oy += drag.y } }
                .clip(RoundedCornerShape(50))
                .background(Color(0xE617191C))
                .border(1.dp, Brush.linearGradient(listOf(Gold.copy(alpha = 0.7f), Color.White.copy(alpha = 0.15f))), RoundedCornerShape(50))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(String.format(Locale.US, "%02d:%02d", s.timerLeft / 60, s.timerLeft % 60), color = Gold, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            IconButton({ s.timerToggle() }, Modifier.size(34.dp)) {
                Icon(if (s.timerRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Pause or resume timer", tint = Ink, modifier = Modifier.size(18.dp))
            }
            IconButton({ s.timerReset() }, Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, "Reset timer", tint = Ink.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
            }
        }
    }
}
