package com.spotlin.messenger.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotlin.messenger.data.ServerConfig

@Composable
fun SpotlinAuthScreen(
    onLogin: (username: String, password: String, isRegister: Boolean, bio: String) -> Unit,
    errorMessage: String? = null,
    isLoading: Boolean = false
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var isRegister by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    val displayedError = errorMessage ?: localError

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Spotlin Messenger",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Реальный школьный мессенджер с защитой данных",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Hardcoded SXT-SV Server Info Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Сервер: SXT-SV (${ServerConfig.DEFAULT_HTTP_URL})",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Mode Toggle (Login vs Register)
            TabRow(
                selectedTabIndex = if (isRegister) 1 else 0,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Tab(
                    selected = !isRegister,
                    onClick = {
                        isRegister = false
                        localError = null
                    },
                    text = { Text("Вход", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = isRegister,
                    onClick = {
                        isRegister = true
                        localError = null
                    },
                    text = { Text("Регистрация", fontWeight = FontWeight.SemiBold) }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    localError = null
                },
                label = { Text("Логин / Имя пользователя") },
                leadingIcon = {
                    Icon(Icons.Default.Person, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    localError = null
                },
                label = { Text("Пароль") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, contentDescription = null)
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            AnimatedVisibility(visible = isRegister) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = bio,
                        onValueChange = { bio = it },
                        label = { Text("О себе (класс, роль в школе)") },
                        leadingIcon = {
                            Icon(Icons.Default.Description, contentDescription = null)
                        },
                        singleLine = false,
                        maxLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            if (displayedError != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayedError,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    if (username.isBlank() || password.isBlank()) {
                        localError = "Пожалуйста, введите логин и пароль"
                        return@Button
                    }
                    if (password.length < 4) {
                        localError = "Пароль должен содержать минимум 4 символа"
                        return@Button
                    }
                    localError = null
                    onLogin(username.trim(), password.trim(), isRegister, bio.trim())
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = if (isRegister) "Зарегистрироваться" else "Войти в Spotlin",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
