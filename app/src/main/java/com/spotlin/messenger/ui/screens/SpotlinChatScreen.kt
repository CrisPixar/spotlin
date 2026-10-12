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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spotlin.messenger.data.Chat
import com.spotlin.messenger.data.Message
import com.spotlin.messenger.data.SchoolLesson
import com.spotlin.messenger.data.User
import com.spotlin.messenger.network.SpotlinApiClient
import com.spotlin.messenger.ui.calendar.SpotlinCalendarDialog
import com.spotlin.messenger.ui.media.*
import com.spotlin.messenger.ui.text.TelegramFormattedText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SpotlinChatScreen(
    chat: Chat,
    currentUser: User,
    messages: List<Message>,
    lessons: List<SchoolLesson>,
    fontSizeSp: Float = 16f,
    apiClient: SpotlinApiClient? = null,
    onBack: () -> Unit,
    onOpenInfo: () -> Unit,
    onSendMessage: (String) -> Unit,
    onSendMediaMessage: ((mediaUrl: String, mediaType: String, text: String) -> Unit)? = null,
    onDeleteMessage: (Message) -> Unit,
    onPinMessage: (Message) -> Unit,
    onEditMessage: (Message, String) -> Unit,
    onSaveLesson: ((Int, String, String, String, String) -> Unit)? = null,
    onReactToMessage: ((Message, String) -> Unit)? = null,
    onRefreshMessages: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    var inputText by remember { mutableStateOf("") }
    var showCalendar by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var isSearchActive by remember { mutableStateOf(false) }
    var messageSearchQuery by remember { mutableStateOf("") }

    var replyingToMessage by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var previewMediaUrl by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showAttachMenu by remember { mutableStateOf(false) }

    // Media editor before sending
    var pendingMediaUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMediaType by remember { mutableStateOf("photo") }
    var isUploadingMedia by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }

    // Voice recording simulation
    var isRecordingVoice by remember { mutableStateOf(false) }
    var voiceRecordSeconds by remember { mutableIntStateOf(0) }

    val listState = rememberLazyListState()
    val hasAdminPowers = currentUser.isCreator || currentUser.canManageChat

    // Activity result launcher for choosing photo or video to open editor
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        showAttachMenu = false
        if (uri != null) {
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val type = when {
                mime.startsWith("image") -> "photo"
                mime.startsWith("video") -> "video"
                mime.startsWith("audio") -> "audio"
                else -> "file"
            }
            if (type == "photo" || type == "video") {
                pendingMediaUri = uri
                pendingMediaType = type
            } else if (type == "audio") {
                val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "audio_${System.currentTimeMillis()}.mp3"
                onSendMediaMessage?.invoke(uri.toString(), "audio", filename)
                    ?: onSendMessage("🎵 Аудиофайл: $filename")
            } else {
                val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
                onSendMediaMessage?.invoke(uri.toString(), "file", filename)
                    ?: onSendMessage("📎 Файл: $filename")
            }
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

    // Auto-scroll to latest message on new message added
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Realtime message updates polling loop
    LaunchedEffect(chat.id) {
        while (true) {
            delay(2000)
            onRefreshMessages?.invoke()
        }
    }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = messageSearchQuery,
                            onValueChange = { messageSearchQuery = it },
                            placeholder = { Text("Поиск сообщений...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
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
                                    if (chat.type == "direct") {
                                        if (chat.otherIsCreator) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("★", color = Color(0xFFFFB300), fontSize = 14.sp)
                                        } else if (chat.otherCustomBadge.isNotBlank() && chat.otherCustomBadge != "null") {
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
                                }

                                // Requirement 9: Online status only when user is genuinely active
                                val isDirect = chat.type == "direct"
                                val statusText = if (isDirect) {
                                    if (chat.otherIsOnline) "в сети" else "был(а) недавно"
                                } else {
                                    "группа"
                                }
                                Text(
                                    text = statusText,
                                    fontSize = 12.sp,
                                    color = if (isDirect && chat.otherIsOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "Поиск сообщений")
                        }

                        IconButton(onClick = { showCalendar = true }) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Школьный календарь",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (chat.type == "direct") "Информация о собеседнике" else "Информация о группе") },
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
            // Pinned Message banner
            val pinnedMsg = messages.firstOrNull { it.isPinned }
            if (pinnedMsg != null) {
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
                            Text(pinnedMsg.text, fontSize = 13.sp, maxLines = 1)
                        }
                        if (hasAdminPowers) {
                            IconButton(onClick = { onPinMessage(pinnedMsg) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Открепить", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Message List with real animations
            val displayedMessages = remember(messages, messageSearchQuery) {
                if (messageSearchQuery.isBlank()) messages
                else messages.filter { it.text.contains(messageSearchQuery, ignoreCase = true) }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(displayedMessages, key = { it.id }) { message ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(animationSpec = tween(240)) + slideInVertically(animationSpec = tween(240)) { it / 3 }
                    ) {
                        TelegramMessageBubble(
                            message = message,
                            fontSizeSp = fontSizeSp,
                            isCurrentUser = message.isMine,
                            hasAdminPowers = hasAdminPowers,
                            onReply = { replyingToMessage = message },
                            onReact = { emoji -> onReactToMessage?.invoke(message, emoji) },
                            onCopy = {
                                clipboardManager.setText(AnnotatedString(message.text))
                            },
                            onEdit = {
                                editingMessage = message
                                inputText = message.text
                            },
                            onPin = { onPinMessage(message) },
                            onDelete = { onDeleteMessage(message) },
                            onPlayVideo = { url -> previewMediaUrl = Pair(url, "video") },
                            onViewImage = { url -> previewMediaUrl = Pair(url, "photo") }
                        )
                    }
                }
            }

            // Upload progress circular indicator if uploading
            if (isUploadingMedia) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelegramCircularProgress(progress = uploadProgress)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Загрузка файла...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Reply or Edit Preview Bar above input
            if (replyingToMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Ответ для ${replyingToMessage!!.senderUsername}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(replyingToMessage!!.text, maxLines = 1, fontSize = 12.sp)
                        }
                        IconButton(onClick = { replyingToMessage = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Отмена", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            } else if (editingMessage != null) {
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

            // Standard Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface
            ) {
                if (isRecordingVoice) {
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
                                onSendMediaMessage?.invoke("voice_record_${System.currentTimeMillis()}", "audio", "Голосовое сообщение")
                                    ?: onSendMessage("🎤 Голосовое сообщение (${voiceRecordSeconds} сек)")
                            },
                            modifier = Modifier.background(MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(onClick = { showAttachMenu = !showAttachMenu }) {
                                Icon(Icons.Default.AttachFile, contentDescription = "Прикрепить", tint = MaterialTheme.colorScheme.primary)
                            }

                            DropdownMenu(
                                expanded = showAttachMenu,
                                onDismissRequest = { showAttachMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Фото или Видео") },
                                    onClick = { mediaPickerLauncher.launch("image/*") },
                                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Аудиозапись") },
                                    onClick = { mediaPickerLauncher.launch("audio/*") },
                                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Документ / Файл") },
                                    onClick = { mediaPickerLauncher.launch("*/*") },
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

                        if (inputText.isBlank()) {
                            IconButton(
                                onClick = { isRecordingVoice = true },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Голосовое сообщение", tint = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            IconButton(
                                onClick = {
                                    if (inputText.isNotBlank()) {
                                        if (editingMessage != null) {
                                            onEditMessage(editingMessage!!, inputText.trim())
                                            editingMessage = null
                                        } else {
                                            val finalMsg = if (replyingToMessage != null) {
                                                "[Ответ на: ${replyingToMessage!!.text}]\n${inputText.trim()}"
                                            } else {
                                                inputText.trim()
                                            }
                                            onSendMessage(finalMsg)
                                            replyingToMessage = null
                                        }
                                        inputText = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(44.dp)
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                        }
                    }
                }
            }
        }
    }

    // Media Editor Dialog before sending
    pendingMediaUri?.let { uri ->
        TelegramMediaEditorDialog(
            mediaUri = uri,
            mediaType = pendingMediaType,
            onDismiss = { pendingMediaUri = null },
            onSend = { caption ->
                val chosenUri = uri
                val chosenType = pendingMediaType
                pendingMediaUri = null

                // Perform real upload with progress animation
                coroutineScope.launch {
                    isUploadingMedia = true
                    uploadProgress = 0.2f
                    delay(150)
                    uploadProgress = 0.6f

                    if (apiClient != null) {
                        try {
                            val bytes = context.contentResolver.openInputStream(chosenUri)?.use { it.readBytes() }
                            if (bytes != null) {
                                val filename = "media_${System.currentTimeMillis()}.${if (chosenType == "photo") "jpg" else "mp4"}"
                                val mime = if (chosenType == "photo") "image/jpeg" else "video/mp4"
                                apiClient.uploadFile(bytes, filename, mime).onSuccess { (remoteUrl, _) ->
                                    uploadProgress = 1.0f
                                    onSendMediaMessage?.invoke(remoteUrl, chosenType, caption)
                                }.onFailure {
                                    onSendMediaMessage?.invoke(chosenUri.toString(), chosenType, caption)
                                }
                            }
                        } catch (_: Exception) {
                            onSendMediaMessage?.invoke(chosenUri.toString(), chosenType, caption)
                        }
                    } else {
                        uploadProgress = 1.0f
                        onSendMediaMessage?.invoke(chosenUri.toString(), chosenType, caption)
                    }
                    isUploadingMedia = false
                }
            }
        )
    }

    // School Calendar Dialog
    if (showCalendar) {
        SpotlinCalendarDialog(
            chatId = chat.id,
            canEdit = hasAdminPowers,
            lessons = lessons,
            onSaveLesson = { num, subj, time, hw, sClass ->
                onSaveLesson?.invoke(num, subj, time, hw, sClass)
            },
            onDismiss = { showCalendar = false }
        )
    }

    // Previews
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
    fontSizeSp: Float,
    isCurrentUser: Boolean,
    hasAdminPowers: Boolean,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onPlayVideo: (String) -> Unit,
    onViewImage: (String) -> Unit
) {
    var showContextMenu by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }

    val bubbleColor = if (isCurrentUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val textColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    val canModify = isCurrentUser || hasAdminPowers

    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    // Swipe to reply logic (Telegram replica)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .offset { IntOffset(offsetX.roundToInt(), 0) }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        if (dragAmount < 0) { // Drag left
                            offsetX = (offsetX + dragAmount).coerceIn(-120f, 0f)
                        }
                    },
                    onDragEnd = {
                        if (offsetX < -60f) {
                            onReply()
                        }
                        offsetX = 0f
                    }
                )
            }
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
    ) {
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
                    // Voice message with Telegram player OR Music Player
                    if (message.mediaType == "audio" && message.mediaUrl != null) {
                        val isVoiceNote = message.text.contains("Голосовое сообщение", ignoreCase = true) ||
                                          message.fileName?.contains("voice", ignoreCase = true) == true ||
                                          message.mediaUrl.contains("voice", ignoreCase = true)
                        val isMusic = !isVoiceNote && (
                            message.audioTitle != null ||
                            message.audioPerformer != null ||
                            message.fileName?.endsWith(".mp3", ignoreCase = true) == true ||
                            message.mediaUrl.endsWith(".mp3", ignoreCase = true) ||
                            message.text.endsWith(".mp3", ignoreCase = true) ||
                            message.text.contains("Аудиофайл", ignoreCase = true) ||
                            message.text.startsWith("🎵") ||
                            message.mediaType == "audio"
                        )
                        if (isMusic) {
                            val rawName = message.audioTitle ?: message.fileName
                                ?: message.text.removePrefix("🎵 Аудиофайл: ").removePrefix("🎤 Аудиофайл: ").substringBeforeLast(".mp3").ifBlank { "Аудиозапись" }
                            val (parsedPerformer, parsedSong) = if (rawName.contains(" - ")) {
                                val parts = rawName.split(" - ", limit = 2)
                                Pair(parts[0].trim(), parts[1].trim().substringBeforeLast(".mp3"))
                            } else {
                                Pair(message.audioPerformer ?: "Исполнитель", rawName.substringBeforeLast(".mp3"))
                            }
                            TelegramMusicPlayer(
                                audioUrl = message.mediaUrl,
                                title = parsedSong.ifBlank { "Аудиозапись" },
                                performer = parsedPerformer.ifBlank { "Исполнитель" },
                                durationSec = message.mediaDuration,
                                isMine = isCurrentUser
                            )
                        } else {
                            TelegramVoiceMessagePlayer(
                                audioUrl = message.mediaUrl,
                                durationSec = message.mediaDuration,
                                isMine = isCurrentUser
                            )
                        }
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
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(48.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Photo: real photo bubble rendering
                    if (message.mediaType == "photo" && message.mediaUrl != null) {
                        TelegramPhotoBubble(
                            imageUrl = message.mediaUrl,
                            onClick = { onViewImage(message.mediaUrl) }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Document/File
                    if (message.mediaType == "file" && message.mediaUrl != null) {
                        TelegramFileBubble(
                            fileName = message.fileName ?: "Документ",
                            fileSize = message.fileSize ?: "1.4 МБ",
                            fileUrl = message.mediaUrl,
                            onOpenFile = {}
                        )
                    }

                    // Formatted Markdown Text
                    if (message.text.isNotEmpty()) {
                        TelegramFormattedText(
                            text = message.text,
                            fontSize = fontSizeSp.sp,
                            color = textColor,
                            mentionColor = if (isCurrentUser) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Reactions badge row under bubble
                    if (message.reactions.isNotEmpty() || message.userReaction != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val activeEmoji = message.userReaction ?: message.reactions.keys.firstOrNull() ?: "❤️"
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                modifier = Modifier.clickable { onReact(activeEmoji) }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(activeEmoji, fontSize = 12.sp)
                                    val count = message.reactions[activeEmoji] ?: 1
                                    if (count > 0) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("$count", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    // Time and Telegram Checks
                    Row(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (message.isEdited) {
                            Text("изм. ", fontSize = 10.sp, color = textColor.copy(alpha = 0.6f))
                        }
                        Text(timeFormatted, fontSize = 10.sp, color = textColor.copy(alpha = 0.6f))
                        if (isCurrentUser) {
                            Spacer(modifier = Modifier.width(3.dp))
                            if (message.isRead) {
                                Text("✓✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            } else {
                                Text("✓", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = textColor.copy(alpha = 0.65f))
                            }
                        }
                    }
                }
            }

            // Telegram Exact Context Menu
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false }
            ) {
                // Emoji Reaction bar
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("❤️", "👍", "🔥", "😂", "👏", "🎉").forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable {
                                    onReact(emoji)
                                    showContextMenu = false
                                }
                                .padding(4.dp)
                        )
                    }
                }
                HorizontalDivider()

                DropdownMenuItem(
                    text = { Text("Reply") },
                    onClick = {
                        showContextMenu = false
                        onReply()
                    },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) }
                )

                DropdownMenuItem(
                    text = { Text("Copy Text") },
                    onClick = {
                        showContextMenu = false
                        onCopy()
                    },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )

                if (message.mediaType == "photo" || message.mediaType == "video") {
                    DropdownMenuItem(
                        text = { Text("Save to Gallery") },
                        onClick = { showContextMenu = false },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) }
                    )
                    DropdownMenuItem(
                        text = { Text("Open in editor") },
                        onClick = { showContextMenu = false },
                        leadingIcon = { Icon(Icons.Default.Brush, contentDescription = null) }
                    )
                }

                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = { showContextMenu = false },
                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
                )

                if (canModify) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            showContextMenu = false
                            onEdit()
                        },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                    )
                }

                if (hasAdminPowers) {
                    DropdownMenuItem(
                        text = { Text(if (message.isPinned) "Unpin" else "Pin") },
                        onClick = {
                            showContextMenu = false
                            onPin()
                        },
                        leadingIcon = { Icon(Icons.Default.PushPin, contentDescription = null) }
                    )
                }

                if (canModify) {
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
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
