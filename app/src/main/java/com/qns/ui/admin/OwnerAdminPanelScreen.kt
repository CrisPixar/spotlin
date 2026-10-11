package com.qns.ui.admin

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class ManagedUser(
    val id: String,
    val username: String,
    var customBadge: String = "",
    var canEditCalendar: Boolean = false,
    var canManageChat: Boolean = false,
    var isVerified: Boolean = false,
    val isCreator: Boolean = false
)

/**
 * Скрытая админ-панель для Создателя (Owner):
 * - В профиле Создателя жёстко прописан статус "Создатель"
 * - Абсолютные права во всех чатах (закрепление, редактирование, удаление любых сообщений)
 * - Делегирование кастомных прав и бейджей/подписей пользователям
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerAdminPanelScreen(
    onBack: () -> Unit
) {
    // Список пользователей для управления
    var users by remember {
        mutableStateOf(
            listOf(
                ManagedUser("u1", "CrisPixar", "Создатель", canEditCalendar = true, canManageChat = true, isVerified = true, isCreator = true),
                ManagedUser("u2", "anna_ivanova", "Староста", canEditCalendar = true, canManageChat = true, isVerified = true),
                ManagedUser("u3", "sergey_teacher", "Учитель физики", canEditCalendar = true, canManageChat = true, isVerified = true),
                ManagedUser("u4", "dmitry_student", "", canEditCalendar = false, canManageChat = false, isVerified = false),
                ManagedUser("u5", "elena_zavuch", "Завуч", canEditCalendar = true, canManageChat = true, isVerified = true)
            )
        )
    }

    var selectedUserForEdit by remember { mutableStateOf<ManagedUser?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Управление школой Spotlin")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Карточка статуса Создателя
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFFFD700).copy(alpha = 0.25f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFB300), modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Профиль Владельца", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("Статус: Создатель (Полный абсолютный доступ)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Вам доступны абсолютные права во всех чатах: закрепление сообщений, редактирование и удаление любых сообщений участников, а также делегирование прав и подписей.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            item {
                Text(
                    "Пользователи и делегированные права:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            items(users) { user ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = CircleShape,
                                color = if (user.isCreator) Color(0xFFFFD700).copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        if (user.isCreator) Icons.Filled.Star else Icons.Filled.Person,
                                        contentDescription = null,
                                        tint = if (user.isCreator) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(user.username, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    if (user.customBadge.isNotBlank()) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (user.isCreator) Color(0xFFFFD700).copy(alpha = 0.2f) else MaterialTheme.colorScheme.secondaryContainer
                                        ) {
                                            Text(
                                                "[${user.customBadge}]",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (user.isCreator) Color(0xFFD48800) else MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                        }
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (user.canEditCalendar) {
                                        Text("• Календарь ", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                    if (user.canManageChat) {
                                        Text("• Модерация ", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        if (!user.isCreator) {
                            Button(
                                onClick = { selectedUserForEdit = user },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Права", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог делегирования прав и кастомной подписи
    selectedUserForEdit?.let { targetUser ->
        var badgeText by remember { mutableStateOf(targetUser.customBadge) }
        var allowCalendar by remember { mutableStateOf(targetUser.canEditCalendar) }
        var allowManage by remember { mutableStateOf(targetUser.canManageChat) }
        var verified by remember { mutableStateOf(targetUser.isVerified) }

        AlertDialog(
            onDismissRequest = { selectedUserForEdit = null },
            title = {
                Text("Делегирование: ${targetUser.username}")
            },
            text = {
                Column {
                    Text("Кастомная подпись / Роль:", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    OutlinedTextField(
                        value = badgeText,
                        onValueChange = { badgeText = it },
                        placeholder = { Text("Например: Учитель, Староста, Завуч") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Редактирование календаря и Д/З", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = allowCalendar, onCheckedChange = { allowCalendar = it })
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Управление сообщениями в чатах", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = allowManage, onCheckedChange = { allowManage = it })
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Верифицированный статус (галочка)", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = verified, onCheckedChange = { verified = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    targetUser.customBadge = badgeText.trim()
                    targetUser.canEditCalendar = allowCalendar
                    targetUser.canManageChat = allowManage
                    targetUser.isVerified = verified
                    selectedUserForEdit = null
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedUserForEdit = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
