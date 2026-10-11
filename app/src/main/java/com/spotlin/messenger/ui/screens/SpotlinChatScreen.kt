package com.spotlin.messenger.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.spotlin.messenger.data.Message
import com.spotlin.messenger.data.SchoolLesson
import com.spotlin.messenger.data.User
import com.spotlin.messenger.ui.calendar.SpotlinCalendarDialog
import com.spotlin.messenger.ui.media.SpotlinAudioPlayer
import com.spotlin.messenger.ui.media.SpotlinImageViewerDialog
import com.spotlin.messenger.ui.media.SpotlinVideoPlayerDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpotlinChatScreen(
    chat: Chat,
    currentUser: User,
    messages: List<Message>,
    lessons: List<SchoolLesson>,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onSendMessage: (String) -> Unit,
    onDeleteMessage: (Message) -> Unit,
    onPinMessage: (Message) -> Unit,
    onEditMessage: (Message, String) -> Unit,
    onSaveLesson: (Int, String) -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    var showCalendar by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var selectedMessageForAction by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var previewMediaUrl by remember { mutableStateOf<Pair<String, String>?>(null) } // url, type

    val listState = rememberLazyListState()

    // Can current user pin/edit/delete any message? Owner (isCreator) or canManageChat
    val hasAdminPowers = currentUser.isCreator || currentUser.canManageChat

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (if (chat.type == "direct") chat.otherUsername else chat.title)
                                    .firstOrNull()?.uppercase() ?: "S",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (chat.type == "direct") chat.otherUsername else chat.title,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                if (chat.otherIsCreator) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("★", color = Color(0xFFFFB300), fontSize = 14.sp)
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
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                "в сети",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    // School Calendar Button
                    IconButton(onClick = { showCalendar = true }) {
                        Icon(
                            Icons.Default.CalendarMonth,
                            contentDescription = "Школьный календарь",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 3 Dots Menu
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                    }

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Информация о собеседнике") },
                            onClick = {
                                showMenu = false
                                onOpenInfo()
                            },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
                        )
                        DropdownMenuItem(
                            text = { Text("Очистить историю") },
                            onClick = { showMenu = false },
                            leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null) }
                        )
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
            // Pinned Message Bar
            val pinned = messages.firstOrNull { it.isPinned }
            if (pinned != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.PushPin, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Закрепленное сообщение", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(pinned.text, fontSize = 13.sp, maxLines = 1)
                        }
                        if (hasAdminPowers) {
                            IconButton(onClick = { onPinMessage(pinned) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Открепить", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Message List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                reverseLayout = false
            ) {
                items(messages, key = { it.id }) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 2 }
                    ) {
                        MessageBubble(
                            message = message,
                            currentUser = currentUser,
                            hasAdminPowers = hasAdminPowers,
                            onLongClick = { selectedMessageForAction = message },
                            onMediaClick = { url, type -> previewMediaUrl = Pair(url, type) }
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { /* Media attachment */ }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить")
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        placeholder = { Text("Сообщение...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp)
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendMessage(inputText.trim())
                                inputText = ""
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // School Calendar Dialog
    if (showCalendar) {
        SpotlinCalendarDialog(
            chatId = chat.id,
            canEdit = currentUser.isCreator || currentUser.canEditCalendar,
            onDismiss = { showCalendar = false }
        )
    }

    // Message Actions BottomSheet / Dialog
    selectedMessageForAction?.let { msg ->
        val canModify = msg.isMine || hasAdminPowers
        AlertDialog(
            onDismissRequest = { selectedMessageForAction = null },
            title = { Text("Действия с сообщением") },
            text = {
                Column {
                    if (hasAdminPowers) {
                        TextButton(
                            onClick = {
                                onPinMessage(msg)
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (msg.isPinned) "Открепить" else "Закрепить (Админ)")
                        }
                    }
                    if (canModify) {
                        TextButton(
                            onClick = {
                                editingMessage = msg
                                inputText = msg.text
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Редактировать ${if (currentUser.isCreator && !msg.isMine) "(Владелец)" else ""}")
                        }
                        TextButton(
                            onClick = {
                                onDeleteMessage(msg)
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Удалить ${if (currentUser.isCreator && !msg.isMine) "(Владелец)" else ""}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedMessageForAction = null }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Media Dialogs
    previewMediaUrl?.let { (url, type) ->
        when (type) {
            "photo" -> SpotlinImageViewerDialog(imageUrl = url, onDismiss = { previewMediaUrl = null })
            "video" -> SpotlinVideoPlayerDialog(videoUrl = url, onDismiss = { previewMediaUrl = null })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    currentUser: User,
    hasAdminPowers: Boolean,
    onLongClick: () -> Unit,
    onMediaClick: (String, String) -> Unit
) {
    val isMine = message.isMine
    val bubbleColor = if (isMine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = if (isMine) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isMine) 16.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Sender Badge for non-mine messages
                if (!isMine) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = message.senderUsername,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (message.senderIsCreator) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = Color(0xFFFFB300)
                            ) {
                                Text(
                                    "★ Создатель",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        } else if (message.senderBadge.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Surface(
                                shape = RoundedCornerShape(3.dp),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    message.senderBadge,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Media player if present
                if (message.mediaType != null && message.mediaUrl != null) {
                    when (message.mediaType) {
                        "audio" -> {
                            SpotlinAudioPlayer(audioUrl = message.mediaUrl)
                        }
                        "photo" -> {
                            Button(
                                onClick = { onMediaClick(message.mediaUrl, "photo") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Photo, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Просмотреть фото")
                            }
                        }
                        "video" -> {
                            Button(
                                onClick = { onMediaClick(message.mediaUrl, "video") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.PlayCircle, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Смотреть видео")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Message Text
                Text(
                    text = message.text,
                    color = textColor,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Indicators: edited, pinned
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text("ред.", fontSize = 9.sp, color = textColor.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (message.isPinned) {
                        Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(10.dp), tint = textColor.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}
