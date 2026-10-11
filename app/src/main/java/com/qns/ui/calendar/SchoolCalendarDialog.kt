package com.qns.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Школьный календарь Spotlin:
 * - Плавная анимация загрузки
 * - Расписание уроков по дням недели
 * - Домашнее задание к каждому предмету
 * - Индикатор текущего дня недели
 * - Кнопка "Изменить" в правом верхнем углу (доступна только администраторам и уполномоченным)
 */
@Composable
fun SchoolCalendarDialog(
    chatId: String,
    canEdit: Boolean,
    onDismiss: () -> Unit,
) {
    var isLoading by remember { mutableStateOf(true) }
    var isEditing by remember { mutableStateOf(false) }

    val daysOfWeek = remember { listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота") }
    val currentDayIndex = remember {
        val cal = Calendar.getInstance()
        when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 0
            Calendar.TUESDAY -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY -> 3
            Calendar.FRIDAY -> 4
            Calendar.SATURDAY -> 5
            else -> 0
        }
    }
    var selectedDayIndex by remember { mutableStateOf(currentDayIndex) }

    // Расписание по умолчанию
    val schedule = remember {
        mutableStateMapOf(
            "Понедельник" to mutableListOf("1. Алгебра (08:30)", "2. Русский язык (09:25)", "3. Литература (10:20)", "4. История (11:25)", "5. Физика (12:20)", "6. Английский язык (13:15)"),
            "Вторник" to mutableListOf("1. Геометрия (08:30)", "2. Английский язык (09:25)", "3. Информатика (10:20)", "4. Химия (11:25)", "5. Биология (12:20)", "6. Физкультура (13:15)"),
            "Среда" to mutableListOf("1. Алгебра (08:30)", "2. Обществознание (09:25)", "3. География (10:20)", "4. Физика (11:25)", "5. Русский язык (12:20)", "6. Литература (13:15)"),
            "Четверг" to mutableListOf("1. Литература (08:30)", "2. Английский язык (09:25)", "3. История (10:20)", "4. ОБЖ (11:25)", "5. Геометрия (12:20)", "6. Химия (13:15)"),
            "Пятница" to mutableListOf("1. Информатика (08:30)", "2. Биология (09:25)", "3. Алгебра (10:20)", "4. Физкультура (11:25)", "5. Физика (12:20)"),
            "Суббота" to mutableListOf("1. Классный час (09:00)", "2. Подготовка к олимпиадам (10:00)")
        )
    }

    // Домашнее задание
    val homework = remember {
        mutableStateMapOf(
            "Алгебра" to "№ 142, 145 (стр. 67), выучить формулы",
            "Физика" to "Параграф 14, задачи 3-6 в тетради",
            "Русский язык" to "Упражнение 89, повторить правила",
            "Литература" to "Читать главы 4-5 романа, подготовить анализ",
            "Информатика" to "Написать алгоритм сортировки на Python/Java",
            "Английский язык" to "Unit 4, Ex. 3, 5 (Workbook)",
            "Геометрия" to "Теорема о трех перпендикулярах, доказать",
            "Химия" to "Параграф 8, составить уравнения реакций"
        )
    }

    // Имитация плавной микро-анимации загрузки при открытии
    LaunchedEffect(Unit) {
        delay(650)
        isLoading = false
    }

    val selectedDayName = daysOfWeek[selectedDayIndex]
    val todayDateFormatted = remember {
        SimpleDateFormat("d MMMM yyyy, EEEE", Locale("ru")).format(Date())
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize()) {
                // Верхняя панель заголовка календаря
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Школьный календарь",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                todayDateFormatted,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Кнопка "Изменить" в правом верхнем углу (только для уполномоченных)
                        if (canEdit && !isLoading) {
                            Button(
                                onClick = { isEditing = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Изменить", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                        }
                    }
                }

                // Плавная анимация загрузки
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.5.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Синхронизация расписания...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Выбор дня недели с плавной подсветкой
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(daysOfWeek) { index, day ->
                            val isSelected = index == selectedDayIndex
                            val isToday = index == currentDayIndex
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else if (isToday) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                else if (isToday) MaterialTheme.colorScheme.onSecondaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { selectedDayIndex = index }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        day.take(3),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.sp
                                    )
                                    if (isToday) {
                                        Text(
                                            "Сегодня",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Контент выбранного дня: Уроки и Домашнее задание
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Text(
                                "Уроки на $selectedDayName",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        val dayLessons = schedule[selectedDayName] ?: emptyList()
                        if (dayLessons.isEmpty()) {
                            item {
                                Text("На этот день уроков нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(dayLessons) { lesson ->
                                val cleanSubject = lesson.substringAfter(". ").substringBefore(" (")
                                val hw = homework[cleanSubject]
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(Modifier.padding(14.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.Filled.School,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    lesson,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        if (!hw.isNullOrBlank()) {
                                            Spacer(Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Book,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                    Spacer(Modifier.width(6.dp))
                                                    Text(
                                                        "Д/З: $hw",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }

    // Диалог редактирования расписания и домашнего задания для администраторов
    if (isEditing) {
        var editLessonText by remember { mutableStateOf(schedule[selectedDayName]?.joinToString("\n") ?: "") }
        var editSubjectName by remember { mutableStateOf("") }
        var editHwText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Редактирование расписания ($selectedDayName)")
                }
            },
            text = {
                Column {
                    Text("Список уроков (каждый с новой строки):", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editLessonText,
                        onValueChange = { editLessonText = it },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 6
                    )

                    Spacer(Modifier.height(12.dp))
                    Text("Обновить домашнее задание:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editSubjectName,
                        onValueChange = { editSubjectName = it },
                        placeholder = { Text("Название предмета (например: Физика)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = editHwText,
                        onValueChange = { editHwText = it },
                        placeholder = { Text("Текст домашнего задания") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val lines = editLessonText.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                    schedule[selectedDayName] = lines.toMutableList()
                    if (editSubjectName.isNotBlank() && editHwText.isNotBlank()) {
                        homework[editSubjectName.trim()] = editHwText.trim()
                    }
                    isEditing = false
                }) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
