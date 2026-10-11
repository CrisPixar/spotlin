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

    // Owner (isCreator) or canManageChat has full administration rights
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
                    // School Calendar Button placed right next to 3 dots menu
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
            AnimatedVisibility(
                visible = pinned != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                if (pinned != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Закрепленное сообщение (${pinned.senderUsername})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    pinned.text,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (hasAdminPowers) {
                                IconButton(
                                    onClick = { onPinMessage(pinned) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Открепить", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Message Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        isCurrentUser = msg.isMine || msg.senderId == currentUser.id,
                        hasAdminPowers = hasAdminPowers,
                        onLongClick = {
                            selectedMessageForAction = msg
                        },
                        onPlayAudio = { url ->
                            previewMediaUrl = Pair(url, "audio")
                        },
                        onPlayVideo = { url ->
                            previewMediaUrl = Pair(url, "video")
                        },
                        onViewImage = { url ->
                            previewMediaUrl = Pair(url, "photo")
                        }
                    )
                }
            }

            // Edit bar if currently editing
            if (editingMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Редактирование сообщения", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editingMessage = null; inputText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Сообщение...") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        ),
                        maxLines = 4
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                if (editingMessage != null) {
                                    onEditMessage(editingMessage!!, inputText.trim())
                                    editingMessage = null
                                } else {
                                    onSendMessage(inputText.trim())
                                }
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Отправить",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    // School Calendar Dialog
    if (showCalendar) {
        SpotlinCalendarDialog(
            chatId = chat.id,
            canEdit = hasAdminPowers,
            onDismiss = { showCalendar = false }
        )
    }

    // Message Actions BottomSheet / Dialog
    if (selectedMessageForAction != null) {
        val targetMsg = selectedMessageForAction!!
        val canModify = targetMsg.isMine || hasAdminPowers

        AlertDialog(
            onDismissRequest = { selectedMessageForAction = null },
            title = { Text("Действия с сообщением") },
            text = {
                Column {
                    if (canModify) {
                        TextButton(
                            onClick = {
                                editingMessage = targetMsg
                                inputText = targetMsg.text
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Редактировать")
                        }
                    }
                    if (hasAdminPowers) {
                        TextButton(
                            onClick = {
                                onPinMessage(targetMsg)
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.PushPin, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (targetMsg.isPinned) "Открепить" else "Закрепить")
                        }
                    }
                    if (canModify) {
                        TextButton(
                            onClick = {
                                onDeleteMessage(targetMsg)
                                selectedMessageForAction = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
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

    // Native Media Previews
    previewMediaUrl?.let { (url, type) ->
        when (type) {
            "video" -> SpotlinVideoPlayerDialog(videoUrl = url, onDismiss = { previewMediaUrl = null })
            "photo" -> SpotlinImageViewerDialog(imageUrl = url, onDismiss = { previewMediaUrl = null })
            else -> {}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    hasAdminPowers: Boolean,
    onLongClick: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onPlayVideo: (String) -> Unit,
    onViewImage: (String) -> Unit
) {
    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = onLongClick
            ),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        // Sender Name & Role Badge
        if (!isCurrentUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
            ) {
                Text(
                    text = message.senderUsername,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (message.senderIsCreator) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("★", color = Color(0xFFFFB300), fontSize = 11.sp)
                } else if (message.senderBadge.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(3.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(
                            message.senderBadge,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                bottomEnd = if (isCurrentUser) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Audio Player integration
                if (message.mediaType == "audio" && message.mediaUrl != null) {
                    SpotlinAudioPlayer(audioUrl = message.mediaUrl, title = "Аудиозапись")
                }

                // Video Preview integration
                if (message.mediaType == "video" && message.mediaUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable { onPlayVideo(message.mediaUrl) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Play Video", tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                // Photo Viewer integration
                if (message.mediaType == "photo" && message.mediaUrl != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.DarkGray,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .combinedClickable { onViewImage(message.mediaUrl) }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Image, contentDescription = "View Photo", tint = Color.White, modifier = Modifier.size(48.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor
                )

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "ред. ",
                            fontSize = 9.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        text = "12:45",
                        fontSize = 9.sp,
                        color = textColor.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
