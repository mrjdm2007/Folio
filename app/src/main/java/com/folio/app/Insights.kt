package com.folio.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate

/** Twelve weeks of study as a calendar grid: darker gold means more minutes that day. */
@Composable fun StudyHeatmap(d: Data) {
    val today = LocalDate.now()
    val start = today.minusDays(((today.dayOfWeek.value % 7) + 7 * 11).toLong())   // weeks run Sunday to Saturday
    val byDay = d.sessions.groupBy { dayOf(it.time) }.mapValues { e -> e.value.sumOf { it.minutes } }
    GlassCard(Modifier.fillMaxWidth(), RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("LAST 12 WEEKS", color = Gold, fontSize = 11.sp, letterSpacing = 1.sp)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (w in 0 until 12) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (dow in 0 until 7) {
                            val day = start.plusDays((w * 7 + dow).toLong())
                            val m = if (day.isAfter(today)) -1 else (byDay[day] ?: 0)
                            val a = when { m < 0 -> 0f; m == 0 -> 0.07f; m < 30 -> 0.3f; m < 60 -> 0.5f; m < 120 -> 0.75f; else -> 1f }
                            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(3.dp))
                                .background(if (m < 0) Color.Transparent else Gold.copy(alpha = a)))
                        }
                    }
                }
            }
        }
    }
}

/** Lists every block on the page that contains the search text; tapping one jumps to it. */
@Composable fun FindDialog(blocks: List<Block>, onGo: (Int) -> Unit, onClose: () -> Unit) {
    var q by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onClose, containerColor = Color(0xFF202327).copy(alpha = 0.95f), shape = RoundedCornerShape(24.dp),
        title = { Text("Find in this page") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search text") }, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                val hits = if (q.length < 2) emptyList() else blocks.withIndex().filter { (_, b) -> b.text.contains(q, true) || b.extra.contains(q, true) }
                if (q.length >= 2) Text("${hits.size} match(es)", fontSize = 12.sp, color = Ink.copy(alpha = 0.6f), modifier = Modifier.padding(vertical = 6.dp))
                hits.take(40).forEach { (i, b) ->
                    val full = if (b.text.contains(q, true)) b.text else b.extra
                    val at = full.indexOf(q, ignoreCase = true).coerceAtLeast(0)
                    val snippet = full.substring(maxOf(0, at - 30), minOf(full.length, at + q.length + 40)).replace("\n", " ")
                    Text("…$snippet…", Modifier.fillMaxWidth().clickable { onGo(i); onClose() }.padding(vertical = 8.dp), fontSize = 14.sp)
                }
            }
        }, confirmButton = { TextButton(onClose) { Text("Close") } })
}
