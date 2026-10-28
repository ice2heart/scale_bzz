package ru.scalejournal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.scalejournal.data.Measurement
import ru.scalejournal.data.UserProfile
import ru.scalejournal.util.Bmi
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

private val ruLocale = Locale("ru")
private val chartHeight = 180.dp

@Composable
fun AppScreen(
    status: String,
    measurements: List<Measurement>,
    profile: UserProfile?,
    updateMessage: String,
    updateAvailable: Boolean,
    storedLogToken: String,
    onMeasure: () -> Unit,
    onAdd: (Double) -> Unit,
    onDelete: (Measurement) -> Unit,
    onSaveProfile: (Int, Long, Boolean) -> Unit,
    onSendLog: (String, (String) -> Unit) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                    label = { Text("Весы") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text("Мой вес") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = null) },
                    label = { Text("Профиль") },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (tab == 0) 0.dp else padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
            when (tab) {
                0 -> ScaleTab(status, rememberRecentlySaved(measurements), onMeasure)
                1 -> DataTab(measurements, profile, onAdd, onDelete)
                2 -> ProfileTab(
                    profile,
                    updateMessage,
                    updateAvailable,
                    storedLogToken,
                    onSaveProfile,
                    onSendLog,
                    onCheckUpdate,
                    onDownloadUpdate,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DataTab(
    measurements: List<Measurement>,
    profile: UserProfile?,
    onAdd: (Double) -> Unit,
    onDelete: (Measurement) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            if (measurements.size >= 2) {
                item {
                    Card(modifier = Modifier.padding(horizontal = 16.dp)) {
                        TrendChart(measurements)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
            item {
                BmiCard(measurements, profile)
            }
            for (group in dayGroups(measurements)) {
                item(key = "header_${group.date}") {
                    DayHeader(group)
                }
                items(group.entries, key = { "m_${it.id}" }) { m ->
                    SwipeDeleteRow(m, onDelete)
                }
            }
            item { Spacer(modifier = Modifier.height(8.dp)) }
        }
        OutlinedButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) { Text("Добавить данные") }
    }

    if (showAdd) {
        AddDialog(
            onDismiss = { showAdd = false },
            onConfirm = { weight ->
                showAdd = false
                onAdd(weight)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BmiCard(measurements: List<Measurement>, profile: UserProfile?) {
    val latest = measurements.maxByOrNull { it.timeMillis } ?: return
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            val bmi = profile?.let { Bmi.value(latest.weightKg, it.heightCm) }
            if (bmi == null) {
                Text(
                    "Последний вес: %.1f кг. Укажите рост в «Профиле» для расчёта ИМТ."
                        .format(latest.weightKg),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                Text(
                    "ИМТ %.1f — %s".format(bmi, Bmi.category(bmi)),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "вес %.1f кг при росте %d см".format(latest.weightKg, profile.heightCm),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun rememberRecentlySaved(measurements: List<Measurement>, windowMs: Long = 15_000L): Boolean {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = System.currentTimeMillis()
        }
    }
    val last = measurements.maxByOrNull { it.timeMillis } ?: return false
    return now - last.timeMillis <= windowMs
}

@Composable
private fun ScaleTab(status: String, recentlySaved: Boolean, onMeasure: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
    ) {
        val h = maxHeight
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = h / 4 - 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            SavedIndicator(recentlySaved)
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            RadarAnimation()
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                status,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
        Button(
            onClick = onMeasure,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (h / 4) * 3 - 34.dp),
        ) { Text("Взвеситься") }
    }
}

@Composable
private fun SavedIndicator(saved: Boolean) {
    val color = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(40.dp)
            .border(2.dp, color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visible = saved, enter = fadeIn(), exit = fadeOut()) {
            Icon(
                Icons.Default.Check,
                contentDescription = "Данные записаны",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun RadarAnimation() {
    val transition = rememberInfiniteTransition(label = "radar")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "radarProgress",
    )
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(220.dp)) {
        val maxR = size.minDimension / 2f
        repeat(3) { i ->
            val t = (progress + i / 3f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - t) * 0.9f),
                radius = maxR * t,
                style = Stroke(width = 5f),
            )
        }
        drawCircle(color = color, radius = 8.dp.toPx())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileTab(
    profile: UserProfile?,
    updateMessage: String,
    updateAvailable: Boolean,
    storedLogToken: String,
    onSaveProfile: (Int, Long, Boolean) -> Unit,
    onSendLog: (String, (String) -> Unit) -> Unit,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
) {
    var heightText by remember(profile) { mutableStateOf(profile?.heightCm?.toString() ?: "") }
    var birthMillis by remember(profile) {
        mutableStateOf(profile?.birthMillis ?: System.currentTimeMillis())
    }
    var isMale by remember(profile) { mutableStateOf(profile?.isMale ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    val heightCm = heightText.toIntOrNull()
    val dateText = Instant.ofEpochMilli(birthMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy", ruLocale))

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
    ) {
        item { Text("Профиль", style = MaterialTheme.typography.titleLarge) }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it },
                singleLine = true,
                label = { Text("Рост, см") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Дата рождения: $dateText") }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (isMale) {
                    Button(onClick = { isMale = true }, modifier = Modifier.weight(1f)) {
                        Text("Мужской")
                    }
                    OutlinedButton(onClick = { isMale = false }, modifier = Modifier.weight(1f)) {
                        Text("Женский")
                    }
                } else {
                    OutlinedButton(onClick = { isMale = true }, modifier = Modifier.weight(1f)) {
                        Text("Мужской")
                    }
                    Button(onClick = { isMale = false }, modifier = Modifier.weight(1f)) {
                        Text("Женский")
                    }
                }
            }
        }
        item {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { heightCm?.let { onSaveProfile(it, birthMillis, isMale) } },
                enabled = heightCm != null && heightCm in 50..250,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить") }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Обновление приложения", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = onCheckUpdate,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Проверить обновления") }
            if (updateMessage.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(updateMessage, style = MaterialTheme.typography.bodySmall)
            }
            if (updateAvailable) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onDownloadUpdate,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Скачать и установить") }
            }
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Отладка", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showLog = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Отправить лог на GitHub") }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = birthMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { birthMillis = it }
                    showDatePicker = false
                }) { Text("ОК") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Отмена") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showLog) {
        LogDialog(
            initialToken = storedLogToken,
            onDismiss = { showLog = false },
            onSend = onSendLog,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeDeleteRow(m: Measurement, onDelete: (Measurement) -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete(m)
                true
            } else {
                false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sourceColor = if (m.source == "manual") {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
            Text(
                "%.1f кг".format(m.weightKg),
                style = MaterialTheme.typography.bodyLarge,
                color = sourceColor,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("—", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(10.dp))
            Text(formatTime(m.timeMillis), style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "· ${if (m.source == "manual") "вручную" else "весы"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private data class DayGroup(
    val date: LocalDate,
    val entries: List<Measurement>,
    val deltaKg: Double?,
)

private fun dayGroups(measurements: List<Measurement>): List<DayGroup> {
    if (measurements.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val chronological = measurements.sortedBy { it.timeMillis }
    var previousValue: Double? = null
    val groups = chronological
        .groupBy { Instant.ofEpochMilli(it.timeMillis).atZone(zone).toLocalDate() }
        .toSortedMap()
    val result = mutableListOf<DayGroup>()
    for ((date, entries) in groups) {
        val dayFirst = entries.first().weightKg
        val delta = previousValue?.let { dayFirst - it }
        result.add(DayGroup(date, entries.sortedByDescending { it.timeMillis }, delta))
        previousValue = entries.last().weightKg
    }
    return result.sortedByDescending { it.date }
}

@Composable
private fun DayHeader(group: DayGroup) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            group.date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", ruLocale)),
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        group.deltaKg?.let { delta ->
            val text = (if (delta >= 0) "+" else "") + "%.1f".format(delta)
            Text(
                "$text кг",
                style = MaterialTheme.typography.bodyMedium,
                color = if (delta > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun niceStep(raw: Double): Double {
    val candidates = doubleArrayOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0)
    return candidates.firstOrNull { it >= raw } ?: 20.0
}

@Composable
private fun TrendChart(measurements: List<Measurement>) {
    val chronological = measurements.sortedBy { it.timeMillis }.takeLast(60)
    val values = chronological.map { it.weightKg }
    val sources = chronological.map { it.source }
    var lo = floor(values.min() - 0.2)
    var hi = ceil(values.max() + 0.2)
    if (hi - lo < 1.0) hi = lo + 1.0
    val range = hi - lo
    val step = niceStep(range / 4.0)
    val ticks = mutableListOf<Double>()
    var tick = ceil(lo / step) * step
    while (tick <= hi + 1e-9) {
        ticks.add(tick)
        tick += step
    }
    val tickFormat = if (step < 1) "%.1f" else "%.0f"
    val lineColor = MaterialTheme.colorScheme.primary
    val manualColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val formatter = DateTimeFormatter.ofPattern("d.MM", ruLocale)

    Column(modifier = Modifier.padding(12.dp)) {
        Text("Динамика веса", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Row {
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(chartHeight),
            ) {
                ticks.forEach { t ->
                    val fraction = ((hi - t) / range).toFloat()
                    Text(
                        tickFormat.format(t),
                        fontSize = 10.sp,
                        color = textColor,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .offset(y = (chartHeight * fraction) - 8.dp),
                    )
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(chartHeight),
            ) {
                ticks.forEach { t ->
                    val y = ((hi - t) / range * size.height).toFloat()
                    drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }
                repeat(3) { i ->
                    val x = size.width * (i + 1) / 4
                    drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                }
                val stepX = size.width / (values.size - 1)
                val path = Path()
                values.forEachIndexed { i, v ->
                    val x = i * stepX
                    val y = ((hi - v) / range * size.height).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 6f))
                values.forEachIndexed { i, v ->
                    drawCircle(
                        if (sources[i] == "manual") manualColor else lineColor,
                        radius = 8f,
                        center = Offset(i * stepX, ((hi - v) / range * size.height).toFloat()),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 34.dp),
        ) {
            val dates = chronological.map {
                Instant.ofEpochMilli(it.timeMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                    .format(formatter)
            }
            Text(dates.first(), fontSize = 10.sp, color = textColor)
            Spacer(modifier = Modifier.weight(1f))
            Text(dates[dates.size / 2], fontSize = 10.sp, color = textColor)
            Spacer(modifier = Modifier.weight(1f))
            Text(dates.last(), fontSize = 10.sp, color = textColor)
        }
    }
}

@Composable
private fun AddDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val parsed = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая запись") },
        text = {
            Column {
                Text("Вес, кг", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    placeholder = { Text("например, 98.9") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = parsed != null && parsed > 0 && parsed < 500,
                onClick = { parsed?.let(onConfirm) },
            ) { Text("Сохранить") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun LogDialog(
    initialToken: String,
    onDismiss: () -> Unit,
    onSend: (String, (String) -> Unit) -> Unit,
) {
    var token by remember { mutableStateOf(initialToken) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Лог на GitHub") },
        text = {
            Column {
                Text(
                    "Репозиторий: oblvsp/scale, файл logs/log-<дата>.txt",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    singleLine = true,
                    label = { Text("GitHub token") },
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Введите fine-grained токен (Settings → Developer settings → " +
                        "Personal access tokens → Fine-grained tokens): доступ только к репозиторию " +
                        "oblvsp/scale, право Contents: Read and write. " +
                        "Токен хранится только на телефоне.",
                    style = MaterialTheme.typography.bodySmall,
                )
                result?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.startsWith("Ошибка")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && token.isNotBlank(),
                onClick = {
                    busy = true
                    onSend(token) { message ->
                        busy = false
                        result = message
                    }
                },
            ) { Text(if (busy) "Отправка…" else "Отправить") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
            ) { Text("Закрыть") }
        },
    )
}

private fun formatTime(millis: Long): String =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(ruLocale))
