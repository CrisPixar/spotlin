package com.spotlin.messenger.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.spotlin.messenger.data.Chat
import com.spotlin.messenger.data.User

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotlinChatListScreen(
    currentUser: User,
    chats: List<Chat>,
    availableUsers: List<User> = emptyList(),
    isLoadingChats: Boolean = false,
    onSelectChat: (Chat) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAdminPanel: () -> Unit,
    onCreateDirectChat: (targetUser: User) -> Unit,
    onRefreshChats: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var showNewChatDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Spotlin", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        if (currentUser.isCreator) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFB300)
                            ) {
                                Text(
                                    "Создатель",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        } else if (currentUser.customBadge.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    currentUser.customBadge,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefreshChats) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить чаты")
                    }
                    if (currentUser.isCreator) {
                        IconButton(onClick = onOpenAdminPanel) {
                            Icon(Icons.Default.Security, contentDescription = "Панель Создателя", tint = Color(0xFFFFB300))
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Palette, contentDescription = "Темы")
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Профиль")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            // Regular users write only 1-on-1 (direct messages, NO group creation)
            FloatingActionButton(
                onClick = { showNewChatDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Edit, contentDescription = "Начать диалог")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск диалогов...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                ),
                singleLine = true
            )

            val filteredChats = remember(searchQuery, chats) {
                if (searchQuery.isBlank()) chats
                else chats.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                    it.otherUsername.contains(searchQuery, ignoreCase = true) ||
                    it.otherCustomBadge.contains(searchQuery, ignoreCase = true)
                }
            }

            if (isLoadingChats) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredChats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            Icons.Default.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "У вас пока нет активных диалогов",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Нажмите на кнопку карандаша ниже, чтобы выбрать собеседника и начать переписку тет-а-тет.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredChats, key = { it.id }) { chat ->
                        ChatItemRow(
                            chat = chat,
                            onClick = { onSelectChat(chat) }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }

    // New Direct Chat Dialog (User Picker from School Directory)
    if (showNewChatDialog) {
        var userFilterQuery by remember { mutableStateOf("") }
        val otherUsers = remember(availableUsers, currentUser) {
            availableUsers.filter { it.id != currentUser.id }
        }
        val displayedCandidates = remember(otherUsers, userFilterQuery) {
            if (userFilterQuery.isBlank()) otherUsers
            else otherUsers.filter {
                it.username.contains(userFilterQuery, ignoreCase = true) ||
                it.customBadge.contains(userFilterQuery, ignoreCase = true)
            }
        }

        AlertDialog(
            onDismissRequest = { showNewChatDialog = false },
            title = {
                Text("Начать тет-а-тет диалог", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    Text(
                        "Выберите школьного собеседника из базы пользователей:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = userFilterQuery,
                        onValueChange = { userFilterQuery = it },
                        placeholder = { Text("Поиск пользователя...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.PersonSearch, contentDescription = null) },
                        shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (displayedCandidates.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text("Пользователи не найдены", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(displayedCandidates, key = { it.id }) { targetUser ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            showNewChatDialog = false
                                            onCreateDirectChat(targetUser)
                                        },
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primaryContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                targetUser.username.firstOrNull()?.uppercase() ?: "U",
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(targetUser.username, fontWeight = FontWeight.SemiBold)
                                                if (targetUser.isCreator) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("★", color = Color(0xFFFFB300), fontSize = 12.sp)
                                                } else if (targetUser.customBadge.isNotEmpty()) {
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = MaterialTheme.colorScheme.secondaryContainer
                                                    ) {
                                                        Text(
                                                            targetUser.customBadge,
                                                            fontSize = 9.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                        )
                                                    }
                                                }
                                            }
                                            if (targetUser.bio.isNotEmpty()) {
                                                Text(
                                                    targetUser.bio,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showNewChatDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun ChatItemRow(
    chat: Chat,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = (if (chat.type == "direct") chat.otherUsername else chat.title)
                        .firstOrNull()?.uppercase() ?: "S",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Text(
                            text = if (chat.type == "direct") chat.otherUsername else chat.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (chat.otherIsCreator) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFB300)
                            ) {
                                Text(
                                    "★ Создатель",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        } else if (chat.otherCustomBadge.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    chat.otherCustomBadge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = "сейчас",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (chat.lastMessage.isNotEmpty()) chat.lastMessage else "Нет сообщений",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
