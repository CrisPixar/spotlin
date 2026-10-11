package com.spotlin.messenger.ui.screens

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
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
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onSendMediaMessage: ((mediaUrl: String, mediaType: String, text: String) -> Unit)? = null,
    onDeleteMessage: (Message) -> Unit,
    onPinMessage: (Message) -> Unit,
    onEditMessage: (Message, String) -> Unit,
    onSaveLesson: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    var inputText by remember { mutableStateOf("") }
    var showCalendar by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }

    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var previewMediaUrl by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }

    // Voice recording simulation state
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val hasAdminPowers = currentUser.isCreator || currentUser.canManageChat

    // Media file picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        showAttachMenu = false
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val type = when {
                mime.startsWith("image") -> "photo"
                mime.startsWith("audio") -> "audio"
                mime.startsWith("video") -> "video"
                else -> "file"
            }
            onSendMediaMessage?.invoke(uri.toString(), type, "")
                ?: onSendMessage("[Файл]: $uri")
        }
    }

    LaunchedEffect(isRecordingVoice) {
        if (isRecordingVoice) {
            voiceRecordSeconds = 0
            while (isRecordingVoice) {
                delay(1000)
                voiceRecordSeconds++
            }
        }
    }

    val displayedMessages = remember(messages, isSearchActive, messageSearchQuery) {
        if (!isSearchActive || messageSearchQuery.isBlank()) messages
        else messages.filter { it.text.contains(messageSearchQuery, ignoreCase = true) }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = messageSearchQuery,
                            onValueChange = { messageSearchQuery = it },
                            placeholder = { Text("Поиск в этом чате...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            messageSearchQuery = ""
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        if (messageSearchQuery.isNotEmpty()) {
                            IconButton(onClick = { messageSearchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Очистить")
                            }
                        }
                    }
                )
            } else {
                TopAppBar(
                    title = {
                        // Clickable header opens Chat Info Screen!
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenInfo() }
                                .padding(vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (if (chat.type == "direct") chat.otherUsername else chat.title)
                                        .firstOrNull()?.uppercase() ?: "S",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
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
                                    fontSize = 12.sp,
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
                        // Search in messages button
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск сообщений")
                        }

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
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
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
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Закрепленное сообщение",
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
                items(displayedMessages, key = { it.id }) { msg ->
                    TelegramMessageBubble(
                        message = msg,
                        isCurrentUser = msg.isMine || msg.senderId == currentUser.id,
                        hasAdminPowers = hasAdminPowers,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(msg.text))
                        },
                        onEdit = {
                            editingMessage = msg
                            inputText = msg.text
                        },
                        onPin = { onPinMessage(msg) },
                        onDelete = { onDeleteMessage(msg) },
                        onPlayAudio = { url -> previewMediaUrl = Pair(url, "audio") },
                        onPlayVideo = { url -> previewMediaUrl = Pair(url, "video") },
                        onViewImage = { url -> previewMediaUrl = Pair(url, "photo") }
                    )
                }
            }

            // Edit message banner
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
                        Text("Редактирование", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = { editingMessage = null; inputText = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Voice Recording Bar or Standard Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (isRecordingVoice) {
                    // Voice recording active UI
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format(Locale.US, "Запись: %02d:%02d", voiceRecordSeconds / 60, voiceRecordSeconds % 60),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { isRecordingVoice = false }) {
                            Text("Отмена", color = MaterialTheme.colorScheme.outline)
                        }
                        IconButton(
                            onClick = {
                                isRecordingVoice = false
                                onSendMediaMessage?.invoke("local_voice", "audio", "Голосовое сообщение")
                                    ?: onSendMessage("🎤 Голосовое сообщение (${voiceRecordSeconds} сек)")
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить голосовое", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                } else {
                    // Telegram Input Row: Paperclip + Text Field + Send/Mic
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Paperclip Attachment Button
                        Box {
                            IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                                Icon(
                                    Icons.Default.AttachFile,
                                    contentDescription = "Прикрепить файл",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Attach dropdown menu
                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Фото или Видео") },
                                    onClick = { filePickerLauncher.launch("image/*") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Аудиозапись") },
                                    onClick = { filePickerLauncher.launch("audio/*") },
                                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Документ / Файл") },
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    leadingIcon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null) }
                                )
                            }
                        }

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

                        Spacer(modifier = Modifier.width(6.dp))

                        // If input is empty -> Show MIC button; If has text -> Show SEND button
                        if (inputText.isBlank()) {
                            IconButton(
                                onClick = { isRecordingVoice = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Mic,
                                    contentDescription = "Голосовое сообщение",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
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
                                    .size(44.dp)
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
        }
    }

    // School Calendar Dialog
    if (showCalendar) {
        SpotlinCalendarDialog(
            chatId = chat.id,
            canEdit = hasAdminPowers,
            lessons = lessons,
            onSaveLesson = onSaveLesson,
            onDismiss = { showCalendar = false }
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
private fun TelegramMessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    hasAdminPowers: Boolean,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onPlayAudio: (String) -> Unit,
    onPlayVideo: (String) -> Unit,
    onViewImage: (String) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    val canModify = isCurrentUser || hasAdminPowers
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
        // Sender Name badge if not current user
        if (!isCurrentUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, bottom = 2.dp)
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

        Box {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                    bottomEnd = if (isCurrentUser) 4.dp else 16.dp
                ),
                color = bubbleColor,
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { showContextMenu = true }
                    )
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    // Audio / Voice
                    if (message.mediaType == "audio" && message.mediaUrl != null) {
                        SpotlinAudioPlayer(audioUrl = message.mediaUrl, title = "Голосовое сообщение")
                    }

                    // Video
                    if (message.mediaType == "video" && message.mediaUrl != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onPlayVideo(message.mediaUrl) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Photo
                    if (message.mediaType == "photo" && message.mediaUrl != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.DarkGray,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onViewImage(message.mediaUrl) }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Image, contentDescription = "Photo", tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (message.text.isNotEmpty()) {
                        Text(
                            text = message.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor
                        )
                    }

                    // Time and Telegram Checks (1 check = sent, 2 checks = read)
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isEdited) {
                            Text(
                                text = "изм. ",
                                fontSize = 10.sp,
                                color = textColor.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = timeFormatted,
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(3.dp))
                            // Double checks vs Single check
                            if (message.isRead) {
                                // Double check ✓✓
                                Text(
                                    text = "✓✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2E7D32)
                                )
                            } else {
                                // Single check ✓
                                Text(
                                    text = "✓",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor.copy(alpha = 0.65f)
                                )
                            }
                        }
                    }
                }
            }

            // Telegram Popup Context Menu directly above/adjacent to the bubble
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                // Quick emoji reactions
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("❤️", "👍", "🔥", "😂", "👏").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { showContextMenu = false }
                                .padding(4.dp)
                        )
                    }
                }
                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Копировать") },
                    onClick = {
                        showContextMenu = false
                        onCopy()
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )

                if (canModify) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = {
                            showContextMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                }

                if (hasAdminPowers) {
                    DropdownMenuItem(
                        text = { Text(if (message.isPinned) "Открепить" else "Закрепить") },
                        onClick = {
                            showContextMenu = false
                            onPin()
                        },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                    )
                }

                if (canModify) {
                    DropdownMenuItem(
                        text = { Text("Удалить", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showContextMenu = false
                            onDelete()
                        },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                    )
                }
            }
        }
    }
}
