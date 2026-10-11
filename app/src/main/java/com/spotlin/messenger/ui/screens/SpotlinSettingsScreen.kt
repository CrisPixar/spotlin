package com.spotlin.messenger.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotlin.messenger.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotlinSettingsScreen(
    currentTheme: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки оформления", fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
        ) {
            Text(
                "Цветовая схема",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

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
        }
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
