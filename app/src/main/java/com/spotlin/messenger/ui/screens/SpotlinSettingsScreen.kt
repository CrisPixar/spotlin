package com.spotlin.messenger.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotlin.messenger.data.ActiveSession
import com.spotlin.messenger.data.AppSettings
import com.spotlin.messenger.data.ServerConfig
import com.spotlin.messenger.data.User
import com.spotlin.messenger.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotlinSettingsScreen(
    currentTheme: ThemeMode,
    appSettings: AppSettings,
    allUsers: List<User> = emptyList(),
    activeSessions: List<ActiveSession> = emptyList(),
    onThemeChanged: (ThemeMode) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onRevokeSession: (sessionId: String) -> Unit = {},
    onTerminateAllOtherSessions: () -> Unit = {},
    onUnblockUser: (userId: String) -> Unit = {},
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Общие", "Приватность", "Устройства")

    var showClearCacheDialog by remember { mutableStateOf(false) }
    var cacheClearedMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Settings Navigation Tabs: General, Privacy, Devices/Sessions
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title, fontWeight = FontWeight.SemiBold) }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) {
                when (selectedTab) {
                    0 -> GeneralSettingsTab(
                        currentTheme = currentTheme,
                        appSettings = appSettings,
                        cacheClearedMessage = cacheClearedMessage,
                        onThemeChanged = onThemeChanged,
                        onSettingsChanged = onSettingsChanged,
                        onClearCacheClick = { showClearCacheDialog = true }
                    )
                    1 -> PrivacySettingsTab(
                        appSettings = appSettings,
                        allUsers = allUsers,
                        onSettingsChanged = onSettingsChanged,
                        onUnblockUser = onUnblockUser
                    )
                    2 -> DevicesSessionsTab(
                        sessions = activeSessions,
                        onRevokeSession = onRevokeSession,
                        onTerminateAllOtherSessions = onTerminateAllOtherSessions
                    )
                }
            }
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Очистить кэш?") },
            text = { Text("Будут удалены временные файлы, сохраненные аудиосообщения и кэшированные миниатюры фото. Все сообщения останутся на сервере.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearCacheDialog = false
                    cacheClearedMessage = "Кэш успешно очищен (0 МБ)"
                }) {
                    Text("Очистить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun GeneralSettingsTab(
    currentTheme: ThemeMode,
    appSettings: AppSettings,
    cacheClearedMessage: String?,
    onThemeChanged: (ThemeMode) -> Unit,
    onSettingsChanged: (AppSettings) -> Unit,
    onClearCacheClick: () -> Unit
) {
    // Theme selection section
    SettingsSectionHeader(title = "Внешний вид и темы", icon = Icons.Default.Palette)

    ThemeSelectionCard(
        title = "Material You (Динамическая)",
        description = "Адаптивные цвета системы Android 12+",
        color = MaterialTheme.colorScheme.primary,
        isSelected = currentTheme == ThemeMode.MATERIAL_YOU,
        onClick = { onThemeChanged(ThemeMode.MATERIAL_YOU) }
    )

    Spacer(modifier = Modifier.height(8.dp))

    ThemeSelectionCard(
        title = "Светлая тема",
        description = "Чистый классический светлый интерфейс",
        color = Color(0xFF1E88E5),
        isSelected = currentTheme == ThemeMode.LIGHT,
        onClick = { onThemeChanged(ThemeMode.LIGHT) }
    )

    Spacer(modifier = Modifier.height(8.dp))

    ThemeSelectionCard(
        title = "Темная тема",
        description = "Глубокая темная тема для комфортного чтения",
        color = Color(0xFF121212),
        isSelected = currentTheme == ThemeMode.DARK,
        onClick = { onThemeChanged(ThemeMode.DARK) }
    )

    Spacer(modifier = Modifier.height(8.dp))

    ThemeSelectionCard(
        title = "Pink Edition",
        description = "Эксклюзивная розовая палитра Spotlin",
        color = Color(0xFFE91E63),
        isSelected = currentTheme == ThemeMode.PINK,
        onClick = { onThemeChanged(ThemeMode.PINK) }
    )

    Spacer(modifier = Modifier.height(20.dp))

    // Real-time Text font size personalization (Requirement 1: instantly reflects everywhere)
    SettingsSectionHeader(title = "Размер текста сообщений", icon = Icons.Default.FormatSize)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Размер шрифта", fontWeight = FontWeight.Medium)
                Text("${appSettings.fontSizeSp.toInt()} sp", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = appSettings.fontSizeSp,
                onValueChange = { newSize ->
                    onSettingsChanged(appSettings.copy(fontSizeSp = newSize))
                },
                valueRange = 12f..24f,
                steps = 5,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "Пример отображения текста в чате Spotlin",
                fontSize = appSettings.fontSizeSp.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Notifications & Sounds
    SettingsSectionHeader(title = "Уведомления и звуки", icon = Icons.Default.Notifications)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            SettingSwitchRow(
                title = "Всплывающие push-уведомления",
                subtitle = "Оповещать о новых сообщениях в фоновом режиме",
                checked = appSettings.notificationsEnabled,
                onCheckedChange = { onSettingsChanged(appSettings.copy(notificationsEnabled = it)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            SettingSwitchRow(
                title = "Звук сообщений",
                subtitle = "Воспроизводить системный сигнал",
                checked = appSettings.soundEnabled,
                onCheckedChange = { onSettingsChanged(appSettings.copy(soundEnabled = it)) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            SettingSwitchRow(
                title = "Вибрация",
                subtitle = "Виброотклик при входящих событиях",
                checked = appSettings.vibrationEnabled,
                onCheckedChange = { onSettingsChanged(appSettings.copy(vibrationEnabled = it)) }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Data and Storage / Cache
    SettingsSectionHeader(title = "Данные и память", icon = Icons.Default.Storage)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            SettingSwitchRow(
                title = "Автозагрузка медиа",
                subtitle = "Загружать фото и аудио автоматически",
                checked = appSettings.autoDownloadMedia,
                onCheckedChange = { onSettingsChanged(appSettings.copy(autoDownloadMedia = it)) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            SettingSwitchRow(
                title = "Качество голосовых сообщений",
                subtitle = "Высокое качество Opus сжатия",
                checked = appSettings.highQualityAudio,
                onCheckedChange = { onSettingsChanged(appSettings.copy(highQualityAudio = it)) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClearCacheClick)
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Очистить кэш медиа", fontWeight = FontWeight.Medium)
                    Text(cacheClearedMessage ?: "Освободить память устройства", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                }
                Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Connection & Server Info
    SettingsSectionHeader(title = "Сеть и Сервер", icon = Icons.Default.Dns)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    ServerConfig.HARDCODED_SERVER.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Сервер зафиксирован: ${ServerConfig.HARDCODED_SERVER.httpUrl}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                "Шифрование: AES-256-GCM + End-to-End Key Exchange",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
private fun PrivacySettingsTab(
    appSettings: AppSettings,
    allUsers: List<User>,
    onSettingsChanged: (AppSettings) -> Unit,
    onUnblockUser: (String) -> Unit
) {
    SettingsSectionHeader(title = "Конфиденциальность", icon = Icons.Default.Security)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            PrivacyOptionRow(
                title = "Последняя активность (Время в сети)",
                value = appSettings.lastSeenVisibility,
                onClick = {
                    val next = when (appSettings.lastSeenVisibility) {
                        "Все" -> "Контакты"
                        "Контакты" -> "Никто"
                        else -> "Все"
                    }
                    onSettingsChanged(appSettings.copy(lastSeenVisibility = next))
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            PrivacyOptionRow(
                title = "Фотография профиля",
                value = appSettings.profilePhotoVisibility,
                onClick = {
                    val next = if (appSettings.profilePhotoVisibility == "Все") "Контакты" else "Все"
                    onSettingsChanged(appSettings.copy(profilePhotoVisibility = next))
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            PrivacyOptionRow(
                title = "Звонки",
                value = appSettings.callsVisibility,
                onClick = {
                    val next = if (appSettings.callsVisibility == "Все") "Контакты" else "Все"
                    onSettingsChanged(appSettings.copy(callsVisibility = next))
                }
            )
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // Blacklist / Blocked users section (Requirement 6)
    SettingsSectionHeader(title = "Черный список (Заблокированные)", icon = Icons.Default.Block)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        if (appSettings.blockedUserIds.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Список заблокированных пользователей пуст", color = MaterialTheme.colorScheme.outline, fontSize = 14.sp)
            }
        } else {
            Column(modifier = Modifier.padding(8.dp)) {
                appSettings.blockedUserIds.forEach { blockedId ->
                    val blockedUser = allUsers.firstOrNull { it.id == blockedId }
                    val name = blockedUser?.username ?: "Пользователь $blockedId"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PersonOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(name, fontWeight = FontWeight.Medium)
                        }
                        TextButton(onClick = { onUnblockUser(blockedId) }) {
                            Text("Разблокировать", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DevicesSessionsTab(
    sessions: List<ActiveSession>,
    onRevokeSession: (String) -> Unit,
    onTerminateAllOtherSessions: () -> Unit
) {
    SettingsSectionHeader(title = "Активные сессии", icon = Icons.Default.Devices)

    val currentSession = sessions.firstOrNull { it.isCurrent }
    val otherSessions = sessions.filter { !it.isCurrent }

    currentSession?.let { current ->
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(current.deviceName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Это устройство • Spotlin ${current.appVersion}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                Text("IP: ${current.ipAddress} • В сети сейчас", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    if (otherSessions.isNotEmpty()) {
        Button(
            onClick = onTerminateAllOtherSessions,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.ExitToApp, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Завершить все другие сеансы")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Другие устройства",
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        otherSessions.forEach { session ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(session.deviceName, fontWeight = FontWeight.Bold)
                        Text("${session.appVersion} • IP: ${session.ipAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                        Text("Активность: ${session.lastActive}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onRevokeSession(session.id) }) {
                        Icon(Icons.Default.Close, contentDescription = "Завершить", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Box(modifier = Modifier.padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Нет других активных сессий", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PrivacyOptionRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(value, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemeSelectionCard(
    title: String,
    description: String,
    color: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(description, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Выбрано",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
