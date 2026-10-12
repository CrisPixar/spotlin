package com.spotlin.messenger.ui.calendar

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.spotlin.messenger.data.SchoolLesson
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * Interactive School Calendar:
 * - Functional day selector (Monday to Saturday)
 * - Display of current day, lessons schedule, bell timings and homework
 * - Administrative configuration of lesson times globally for all days or manual override
 * - Editing subject name, class tag (e.g. 10-А), and homework saving to server
 */
@Composable
fun SpotlinCalendarDialog(
    chatId: String,
    canEdit: Boolean, // True for Admins/Creator or delegated users
    lessons: List<SchoolLesson> = emptyList(),
    onSaveLesson: ((number: Int, subject: String, time: String, homework: String, schoolClass: String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }

    val daysOfWeek = remember { listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота") }
    val isSunday = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY }
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

    // Schedule map per day
    val scheduleState = remember(lessons) {
        val map = mutableStateMapOf<String, MutableList<SchoolLesson>>()
        val defaultLessons = if (lessons.isNotEmpty()) lessons else listOf(
            SchoolLesson(1, "Алгебра", "08:30 - 09:15", "Параграф 14, № 14.5, 14.8", ""),
            SchoolLesson(2, "Геометрия", "09:25 - 10:10", "Теорема о трех перпендикулярах", ""),
            SchoolLesson(3, "Физика", "10:30 - 11:15", "Доклад: Законы термодинамики", ""),
            SchoolLesson(4, "Русский язык", "11:35 - 12:20", "Упражнение 218", ""),
            SchoolLesson(5, "Литература", "12:35 - 13:20", "Война и мир (тома 1-2)", ""),
            SchoolLesson(6, "Информатика", "13:30 - 14:15", "Spotlin Android App", "")
        )

        for (day in daysOfWeek) {
            map[day] = defaultLessons.map { it.copy() }.toMutableList()
        }
        map
    }

    LaunchedEffect(Unit) {
        delay(350)
        isLoading = false
    }

    // Dialog state for editing a lesson
    var editingLesson by remember { mutableStateOf<SchoolLesson?>(null) }
    var editSubject by remember { mutableStateOf("") }
    var editTime by remember { mutableStateOf("") }
    var editHomework by remember { mutableStateOf("") }
    var editSchoolClass by remember { mutableStateOf("") }
    var applyToAllDays by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "Школьный Календарь",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val todayName = if (isSunday) "Воскресенье (выходной)" else daysOfWeek[currentDayIndex]
                            Text(
                                "Сегодня: $todayName • Расписание уроков",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Days of week selector (Requirement 16: fully working buttons)
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(daysOfWeek) { index, day ->
                        val isSelected = index == selectedDayIndex
                        val isToday = !isSunday && (index == currentDayIndex)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else if (isToday) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { selectedDayIndex = index }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = day,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else if (isToday) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                                if (isToday && !isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Синхронизация расписания...", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    val activeDay = daysOfWeek[selectedDayIndex]
                    val currentLessons = scheduleState[activeDay] ?: emptyList()

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(currentLessons) { _, lesson ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        "${lesson.number}",
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontSize = 13.sp
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(
                                                        lesson.subject,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    if (lesson.schoolClass.isNotBlank()) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = MaterialTheme.colorScheme.secondaryContainer
                                                        ) {
                                                            Text(
                                                                lesson.schoolClass,
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                                Text(
                                                    lesson.time,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }

                                        // Edit button visible only for Admins / Users with canEdit rights
                                        if (canEdit) {
                                            IconButton(
                                                onClick = {
                                                    editingLesson = lesson
                                                    editSubject = lesson.subject
                                                    editTime = lesson.time
                                                    editHomework = lesson.homework
                                                    editSchoolClass = lesson.schoolClass
                                                }
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "Редактировать урок", tint = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Text(
                                                "Домашнее задание:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                if (lesson.homework.isNotEmpty()) lesson.homework else "Не задано",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Admin Lesson Editing Dialog (Subject name, bell time, class tag, homework, apply to all days)
    editingLesson?.let { lesson ->
        AlertDialog(
            onDismissRequest = { editingLesson = null },
            title = { Text("Урок №${lesson.number}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editSubject,
                        onValueChange = { editSubject = it },
                        label = { Text("Предмет") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editTime,
                        onValueChange = { editTime = it },
                        label = { Text("Время звонка (например, 08:30 - 09:15)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editSchoolClass,
                        onValueChange = { editSchoolClass = it },
                        label = { Text("Класс (например, 10-А)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editHomework,
                        onValueChange = { editHomework = it },
                        label = { Text("Домашнее задание") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToAllDays,
                            onCheckedChange = { applyToAllDays = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Применить время ко всем дням недели", fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeDay = daysOfWeek[selectedDayIndex]
                        if (applyToAllDays) {
                            // Update time globally for all days with new list instance to trigger recomposition
                            daysOfWeek.forEach { day ->
                                scheduleState[day] = scheduleState[day]?.map {
                                    if (it.number == lesson.number) it.copy(time = editTime) else it
                                }?.toMutableList() ?: mutableListOf()
                            }
                        }
                        // Update current day specific lesson with new list instance
                        scheduleState[activeDay] = scheduleState[activeDay]?.map {
                            if (it.number == lesson.number) it.copy(
                                subject = editSubject,
                                time = editTime,
                                homework = editHomework,
                                schoolClass = editSchoolClass
                            ) else it
                        }?.toMutableList() ?: mutableListOf()

                        onSaveLesson?.invoke(lesson.number, editSubject, editTime, editHomework, editSchoolClass)
                        editingLesson = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingLesson = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
