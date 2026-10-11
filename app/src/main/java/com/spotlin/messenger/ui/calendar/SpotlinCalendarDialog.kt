package com.spotlin.messenger.ui.calendar

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spotlin.messenger.data.SchoolLesson
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SpotlinCalendarDialog(
    chatId: String,
    canEdit: Boolean,
    onDismiss: () -> Unit
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
    var selectedDayIndex by remember { mutableIntStateOf(currentDayIndex) }

    val scheduleState = remember {
        mutableStateMapOf(
            "Понедельник" to mutableListOf(
                SchoolLesson(1, "Алгебра", "08:30 - 09:15", "№ 142, 145 (стр. 67)"),
                SchoolLesson(2, "Русский язык", "09:25 - 10:10", "Упражнение 89"),
                SchoolLesson(3, "Литература", "10:20 - 11:05", "Читать главы 4-5"),
                SchoolLesson(4, "История", "11:25 - 12:10", "Параграф 14, вопросы"),
                SchoolLesson(5, "Физика", "12:20 - 13:05", "Задачи 3-6 в тетради"),
                SchoolLesson(6, "Английский язык", "13:15 - 14:00", "Workbook Unit 4")
            ),
            "Вторник" to mutableListOf(
                SchoolLesson(1, "Геометрия", "08:30 - 09:15", "Теорема о перпендикулярах"),
                SchoolLesson(2, "Английский язык", "09:25 - 10:10", "Выучить слова с. 78"),
                SchoolLesson(3, "Информатика", "10:20 - 11:05", "Практикум в среде Spotlin"),
                SchoolLesson(4, "Химия", "11:25 - 12:10", "Параграф 8, реакции"),
                SchoolLesson(5, "Биология", "12:20 - 13:05", "Таблица по строению клетки"),
                SchoolLesson(6, "Физкультура", "13:15 - 14:00", "Спортивная форма")
            ),
            "Среда" to mutableListOf(
                SchoolLesson(1, "Алгебра", "08:30 - 09:15", "№ 150-153"),
                SchoolLesson(2, "Обществознание", "09:25 - 10:10", "Эссе по теме"),
                SchoolLesson(3, "География", "10:20 - 11:05", "Контурные карты"),
                SchoolLesson(4, "Физика", "11:25 - 12:10", "Лабораторная работа"),
                SchoolLesson(5, "Русский язык", "12:20 - 13:05", "Сочинение-рассуждение"),
                SchoolLesson(6, "Литература", "13:15 - 14:00", "Анализ стихотворения")
            ),
            "Четверг" to mutableListOf(
                SchoolLesson(1, "Литература", "08:30 - 09:15", "Образы героев"),
                SchoolLesson(2, "Английский язык", "09:25 - 10:10", "Диалог в парах"),
                SchoolLesson(3, "История", "10:20 - 11:05", "Хронология событий"),
                SchoolLesson(4, "ОБЖ", "11:25 - 12:10", "Правила безопасности"),
                SchoolLesson(5, "Геометрия", "12:20 - 13:05", "№ 88, 91"),
                SchoolLesson(6, "Химия", "13:15 - 14:00", "Решение задач")
            ),
            "Пятница" to mutableListOf(
                SchoolLesson(1, "Информатика", "08:30 - 09:15", "Защита проекта"),
                SchoolLesson(2, "Биология", "09:25 - 10:10", "Конспект"),
                SchoolLesson(3, "Алгебра", "10:20 - 11:05", "Подготовка к контрольной"),
                SchoolLesson(4, "Физкультура", "11:25 - 12:10", "Легкая атлетика"),
                SchoolLesson(5, "Физика", "12:20 - 13:05", "Повторение")
            ),
            "Суббота" to mutableListOf(
                SchoolLesson(1, "Классный час", "09:00 - 09:45", "Школьные мероприятия"),
                SchoolLesson(2, "Олимпиада", "10:00 - 11:30", "Подготовка")
            )
        )
    }

    LaunchedEffect(Unit) {
        delay(600)
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

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(46.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.5.dp
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Загрузка расписания уроков...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
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
                                        Text("Сегодня", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val dayLessons = scheduleState[selectedDayName] ?: emptyList()
                        if (dayLessons.isEmpty()) {
                            item {
                                Text("На этот день уроков нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                            items(dayLessons) { lesson ->
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
                                                    "${lesson.number}. ${lesson.subject}",
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                            Text(
                                                lesson.time,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (lesson.homework.isNotBlank()) {
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
                                                        "Д/З: ${lesson.homework}",
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

    if (isEditing) {
        val currentLessons = scheduleState[selectedDayName] ?: mutableListOf()
        var subjectInput by remember { mutableStateOf("") }
        var hwInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { isEditing = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Редактирование ($selectedDayName)")
                }
            },
            text = {
                Column {
                    Text("Укажите предмет для добавления домашнего задания:", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subjectInput,
                        onValueChange = { subjectInput = it },
                        placeholder = { Text("Предмет (например: Алгебра)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = hwInput,
                        onValueChange = { hwInput = it },
                        placeholder = { Text("Задание (например: № 201-205)") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (subjectInput.isNotBlank()) {
                        val index = currentLessons.indexOfFirst { it.subject.equals(subjectInput.trim(), ignoreCase = true) }
                        if (index != -1) {
                            currentLessons[index].homework = hwInput.trim()
                        } else {
                            currentLessons.add(SchoolLesson(currentLessons.size + 1, subjectInput.trim(), "По расписанию", hwInput.trim()))
                        }
                        scheduleState[selectedDayName] = currentLessons
                    }
                    isEditing = false
                }) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { isEditing = false }) { Text("Отмена") }
            }
        )
    }
}
