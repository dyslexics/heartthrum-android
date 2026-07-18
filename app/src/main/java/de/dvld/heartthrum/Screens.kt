package de.dvld.heartthrum

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

// ---------- Onboarding ----------

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Favorite, null, Modifier.size(84.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Text("• " + stringResource(R.string.onboarding_b1))
        Spacer(Modifier.height(10.dp))
        Text("• " + stringResource(R.string.onboarding_b2))
        Spacer(Modifier.height(10.dp))
        Text("• " + stringResource(R.string.onboarding_b3))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.weight(1f))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.get_started))
        }
    }
}

// ---------- Measure ----------

@Composable
fun MeasureScreen(modifier: Modifier = Modifier, vm: MeasureViewModel = viewModel()) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var denied by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { denied = false; vm.start(owner) } else denied = true
    }

    Column(modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Heart thrum", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        when (vm.phase) {
            MeasureViewModel.Phase.IDLE -> IdleView(denied) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    vm.start(owner)
                } else permission.launch(Manifest.permission.CAMERA)
            }
            MeasureViewModel.Phase.NO_FINGER, MeasureViewModel.Phase.MEASURING -> MeasuringView(vm)
            MeasureViewModel.Phase.DONE -> ResultView(vm)
        }
    }
}

@Composable
private fun IdleView(denied: Boolean, onStart: () -> Unit) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Icon(Icons.Filled.Favorite, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.cover_camera), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium)
        if (denied) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.camera_denied), textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.start_measurement))
        }
    }
}

@Composable
private fun MeasuringView(vm: MeasureViewModel) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Box(Modifier.size(240.dp), contentAlignment = Alignment.Center) {
            val primary = MaterialTheme.colorScheme.primary
            val track = primary.copy(alpha = 0.15f)
            val progress by animateFloatAsState(vm.progress.toFloat(), label = "progress")
            Canvas(Modifier.fillMaxSize()) {
                val stroke = Stroke(width = 24f, cap = StrokeCap.Round)
                drawArc(track, -90f, 360f, false, style = stroke)
                drawArc(primary, -90f, 360f * progress, false, style = stroke)
            }
            if (vm.phase == MeasureViewModel.Phase.MEASURING && vm.liveBPM != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${vm.liveBPM}", fontSize = 56.sp, fontWeight = FontWeight.Bold)
                    Text("BPM", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Text(
                    stringResource(if (vm.phase == MeasureViewModel.Phase.MEASURING) R.string.hold_still else R.string.place_finger),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Waveform(vm.waveform)
        Spacer(Modifier.height(10.dp))
        if (vm.phase == MeasureViewModel.Phase.MEASURING) {
            Text(
                stringResource(if (vm.quality > 0.6) R.string.good_signal else R.string.keep_still),
                style = MaterialTheme.typography.bodySmall,
                color = if (vm.quality > 0.6) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.stop)) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun Waveform(samples: List<Double>) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(Modifier.fillMaxWidth().height(64.dp)) {
        if (samples.size < 2) return@Canvas
        val stepX = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height / 2 - (v * size.height * 0.45).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round))
    }
}

private data class TagUi(val tag: MeasureTag, val label: Int, val icon: ImageVector)

@Composable
private fun ResultView(vm: MeasureViewModel) {
    var selectedTag by remember { mutableStateOf<MeasureTag?>(null) }
    var selectedMood by remember { mutableStateOf<Int?>(null) }
    val tags = listOf(
        TagUi(MeasureTag.RESTING, R.string.tag_resting, Icons.Filled.Bedtime),
        TagUi(MeasureTag.AFTER_ACTIVITY, R.string.tag_after_activity, Icons.Filled.DirectionsRun),
        TagUi(MeasureTag.MORNING, R.string.tag_morning, Icons.Filled.WbSunny),
        TagUi(MeasureTag.EVENING, R.string.tag_evening, Icons.Filled.WbTwilight),
    )
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.measurement_complete), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${vm.resultBPM ?: 0}", fontSize = 68.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            Text(" BPM", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.context_label), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { t ->
                val selected = selectedTag == t.tag
                Column(
                    Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { selectedTag = if (selected) null else t.tag }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(t.icon, null, Modifier.size(20.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(t.label), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.how_feeling), fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            listOf("😕", "🙂", "😄").forEachIndexed { i, emoji ->
                val mood = i + 1
                val selected = selectedMood == mood
                Text(
                    emoji, fontSize = 34.sp,
                    modifier = Modifier
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else androidx.compose.ui.graphics.Color.Transparent,
                            CircleShape
                        )
                        .clickable { selectedMood = if (selected) null else mood }
                        .padding(8.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { vm.save(selectedTag, selectedMood) { selectedTag = null; selectedMood = null; vm.cancel() } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.save)) }
        TextButton(onClick = { vm.cancel() }) { Text(stringResource(R.string.discard)) }
    }
}

// ---------- History ----------

@Composable
fun HistoryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).pulseDao() }
    val records by dao.all().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var range by rememberSaveable { mutableIntStateOf(1) }
    val ranges = listOf(R.string.range_day to 1L, R.string.range_week to 7L, R.string.range_month to 30L)
    val cutoff = System.currentTimeMillis() - ranges[range].second * 86_400_000L
    val visible = records.filter { it.date >= cutoff }

    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.tab_history), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (records.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.no_measurements), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ranges.forEachIndexed { i, (label, _) ->
                SegmentedButton(
                    selected = range == i, onClick = { range = i },
                    shape = SegmentedButtonDefaults.itemShape(i, ranges.size)
                ) { Text(stringResource(label)) }
            }
        }
        Spacer(Modifier.height(14.dp))
        if (visible.isNotEmpty()) {
            Chart(visible)
            Spacer(Modifier.height(10.dp))
            val values = visible.map { it.bpm }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(R.string.stat_avg, values.average().toInt(), Modifier.weight(1f))
                StatBox(R.string.stat_min, values.min(), Modifier.weight(1f))
                StatBox(R.string.stat_max, values.max(), Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
        }
        LazyColumn {
            items(records, key = { it.id }) { record ->
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text("${record.bpm} BPM", fontWeight = FontWeight.SemiBold)
                            record.mood?.let { if (it in 1..3) Text(" " + listOf("😕", "🙂", "😄")[it - 1]) }
                        }
                        Text(
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(record.date)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    MeasureTag.from(record.tag)?.let { t ->
                        Text(stringResource(tagLabel(t)), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { scope.launch { dao.delete(record) } }) {
                        Icon(Icons.Filled.Delete, stringResource(R.string.delete),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun tagLabel(tag: MeasureTag) = when (tag) {
    MeasureTag.RESTING -> R.string.tag_resting
    MeasureTag.AFTER_ACTIVITY -> R.string.tag_after_activity
    MeasureTag.MORNING -> R.string.tag_morning
    MeasureTag.EVENING -> R.string.tag_evening
}

@Composable
private fun StatBox(label: Int, value: Int, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$value", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Chart(records: List<PulseRecord>) {
    val color = MaterialTheme.colorScheme.primary
    val sorted = records.sortedBy { it.date }
    Canvas(Modifier.fillMaxWidth().height(160.dp)) {
        if (sorted.size < 2) {
            sorted.firstOrNull()?.let {
                drawCircle(color, 8f, Offset(size.width / 2, size.height / 2))
            }
            return@Canvas
        }
        val minB = (sorted.minOf { it.bpm } - 5).coerceAtLeast(30)
        val maxB = sorted.maxOf { it.bpm } + 5
        val minT = sorted.first().date.toFloat()
        val maxT = sorted.last().date.toFloat()
        val path = Path()
        sorted.forEachIndexed { i, r ->
            val x = if (maxT == minT) size.width / 2 else (r.date - minT) / (maxT - minT) * size.width
            val y = size.height - (r.bpm - minB).toFloat() / (maxB - minB) * size.height
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(color, 7f, Offset(x, y))
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
    }
}

// ---------- Breathe ----------

@Composable
fun BreatheScreen(modifier: Modifier = Modifier) {
    var running by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var durationIdx by rememberSaveable { mutableIntStateOf(1) }
    val durations = listOf(60, 180, 300)
    val labels = listOf(R.string.dur_1, R.string.dur_3, R.string.dur_5)
    var inhale by remember { mutableStateOf(true) }
    var remaining by remember { mutableIntStateOf(0) }
    var target by remember { mutableStateOf(1f) }
    val scale by animateFloatAsState(target, tween(if (inhale) 4000 else 6000), label = "breathe")

    LaunchedEffect(running) {
        if (!running) { target = 1f; return@LaunchedEffect }
        finished = false
        val total = durations[durationIdx]
        val end = System.currentTimeMillis() + total * 1000L
        while (running && System.currentTimeMillis() < end) {
            inhale = true; target = 1.45f
            delay(4000)
            remaining = ((end - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
            if (!running || System.currentTimeMillis() >= end) break
            inhale = false; target = 1f
            delay(6000)
            remaining = ((end - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
        }
        if (running) { finished = true; running = false }
        target = 1f
    }

    Column(modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.tab_breathe), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            val teal = MaterialTheme.colorScheme.tertiary
            Canvas(Modifier.fillMaxSize()) {
                drawCircle(teal.copy(alpha = 0.15f))
                drawCircle(teal.copy(alpha = 0.35f), radius = size.minDimension / 3.2f * scale)
            }
            Text(
                when {
                    running -> stringResource(if (inhale) R.string.breathe_in else R.string.breathe_out)
                    finished -> stringResource(R.string.well_done)
                    else -> stringResource(R.string.guided_breathing)
                },
                fontWeight = FontWeight.Bold, color = teal, textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(20.dp))
        if (running) {
            Text("%d:%02d".format(remaining / 60, remaining % 60),
                style = MaterialTheme.typography.titleLarge)
        } else {
            Text(stringResource(R.string.breathe_hint), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow {
                labels.forEachIndexed { i, label ->
                    SegmentedButton(selected = durationIdx == i, onClick = { durationIdx = i },
                        shape = SegmentedButtonDefaults.itemShape(i, labels.size)) {
                        Text(stringResource(label))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(
            onClick = { if (running) running = false else { remaining = durations[durationIdx]; running = true } },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(if (running) R.string.done else R.string.start)) }
    }
}

// ---------- Settings ----------

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.about), fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.disclaimer), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.free_line), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://heartthrum.com/about.html")))
        }) { Text(stringResource(R.string.about_link)) }
        TextButton(onClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://heartthrum.com/privacy.html")))
        }) { Text(stringResource(R.string.privacy_policy)) }
        Spacer(Modifier.weight(1f))
        Text("Version 1.0", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
