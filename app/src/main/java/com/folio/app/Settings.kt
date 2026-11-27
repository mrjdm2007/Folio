package com.folio.app

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Locale

@Composable fun RemindersScreen(s: Store) {
    val ctx = LocalContext.current
    val sp = remember { Prefs.p(ctx) }
    var on by remember { mutableStateOf(sp.getBoolean("rem_on", false)) }
    var hours by remember { mutableIntStateOf(sp.getInt("rem_hours", 2)) }
    var qs by remember { mutableIntStateOf(sp.getInt("quiet_start", 22)) }
    var qe by remember { mutableIntStateOf(sp.getInt("quiet_end", 7)) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun save() {
        sp.edit().putBoolean("rem_on", on).putInt("rem_hours", hours).putInt("quiet_start", qs).putInt("quiet_end", qe).apply()
        ReminderScheduler.apply(ctx)
    }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Study reminders", Modifier.weight(1f), fontWeight = FontWeight.Medium)
            Switch(on, { v ->
                on = v
                if (v && Build.VERSION.SDK_INT >= 33) perm.launch(Manifest.permission.POST_NOTIFICATIONS)
                save()
            }, colors = SwitchDefaults.colors(checkedTrackColor = Gold, checkedThumbColor = Graphite))
        }
        Text("Each reminder shows a due flashcard (answer underneath), an upcoming exam, or a topic from your notes to revise. The same item is not repeated on the same day.", fontSize = 13.sp)
        SectionTitle("Remind me every")
        Row { listOf(1, 2, 3, 4).forEach { h -> Chip("$h h", hours == h) { hours = h; save() } } }
        SectionTitle("Quiet hours")
        HourRow("From", qs) { qs = it; save() }
        HourRow("Until", qe) { qe = it; save() }
        OutlinedButton({
            WorkManager.getInstance(ctx).enqueue(OneTimeWorkRequestBuilder<ReminderWorker>().setInputData(workDataOf("force" to true)).build())
        }) { Text("Send a test reminder") }
    }
}

@Composable fun HourRow(label: String, h: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.width(64.dp))
        Chip("−") { onChange((h + 23) % 24) }
        Text(String.format(Locale.US, "%02d:00", h), color = Gold)
        Chip("+") { onChange((h + 1) % 24) }
    }
}
