package com.spotlin.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.spotlin.messenger.data.Chat
import com.spotlin.messenger.data.Message
import com.spotlin.messenger.data.SchoolLesson
import com.spotlin.messenger.data.ServerConfig
import com.spotlin.messenger.data.User
import com.spotlin.messenger.network.SpotlinApiClient
import com.spotlin.messenger.ui.admin.SpotlinOwnerAdminScreen
import com.spotlin.messenger.ui.info.SpotlinChatInfoScreen
import com.spotlin.messenger.ui.screens.*
import com.spotlin.messenger.ui.theme.SpotlinTheme
import com.spotlin.messenger.ui.theme.ThemeMode
import com.spotlin.messenger.util.SpotlinLogger
import kotlinx.coroutines.launch

sealed class Screen {
    object Auth : Screen()
    object ChatList : Screen()
    object NewChatSearch : Screen()
    data class ChatView(val chat: Chat) : Screen()
    data class ChatInfo(val chat: Chat) : Screen()
    object Profile : Screen()
    object Settings : Screen()
    object AdminPanel : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SpotlinLogger.i("MainActivity", "Spotlin messenger starting. Debug logging: ${BuildConfig.DEBUG}")
        setContent {
            var currentThemeMode by remember { mutableStateOf(ThemeMode.MATERIAL_YOU) }

            SpotlinTheme(themeMode = currentThemeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpotlinApp(
                        currentThemeMode = currentThemeMode,
                        onThemeChanged = {
                            SpotlinLogger.d("MainActivity", "Theme changed to: $it")
                            currentThemeMode = it
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SpotlinApp(
    currentThemeMode: ThemeMode,
    onThemeChanged: (ThemeMode) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // Server is permanently hardcoded to SXT-SV (f1.rustix.me:31774)
    val hardcodedServer = remember { ServerConfig.HARDCODED_SERVER }
    val apiClient = remember { SpotlinApiClient(baseUrl = hardcodedServer.httpUrl) }

    // Start with NO mock user - User MUST authenticate on the Auth screen!
    var currentUser by remember { mutableStateOf<User?>(null) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Auth) }

    // App Settings (Font size, privacy, blocked users)
    var appSettings by remember { mutableStateOf(com.spotlin.messenger.data.AppSettings()) }
    var activeSessions by remember {
        mutableStateOf(
            listOf(
                com.spotlin.messenger.data.ActiveSession(
                    id = "sess_current",
                    deviceName = "Spotlin Android App",
                    appVersion = "v2.5.0",
                    ipAddress = "127.0.0.1",
                    lastActive = "Онлайн (это устройство)",
                    isCurrent = true
                ),
                com.spotlin.messenger.data.ActiveSession(
                    id = "sess_web",
                    deviceName = "Spotlin Web Client (Chrome)",
                    appVersion = "v2.4.1",
                    ipAddress = "192.168.1.42",
                    lastActive = "Вчера в 18:22",
                    isCurrent = false
                )
            )
        )
    }

    // Real server-loaded state (No mocks)
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var lessons by remember { mutableStateOf<List<SchoolLesson>>(emptyList()) }
    var messagesMap by remember { mutableStateOf<Map<String, List<Message>>>(emptyMap()) }

    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingChats by remember { mutableStateOf(false) }

    // Android system Back Button handler: navigate back inside app instead of closing
    BackHandler(enabled = currentScreen !is Screen.Auth && currentScreen !is Screen.ChatList) {
        when (val screen = currentScreen) {
            is Screen.ChatInfo -> currentScreen = Screen.ChatView(screen.chat)
            is Screen.ChatView -> currentScreen = Screen.ChatList
            is Screen.NewChatSearch -> currentScreen = Screen.ChatList
            is Screen.Profile -> currentScreen = Screen.ChatList
            is Screen.Settings -> currentScreen = Screen.ChatList
            is Screen.AdminPanel -> currentScreen = Screen.ChatList
            else -> {}
        }
    }

    // Helper: refresh chats and users from server
    fun refreshData() {
        coroutineScope.launch {
            isLoadingChats = true
            apiClient.getChats().onSuccess { fetchedChats ->
                chats = fetchedChats
                SpotlinLogger.d("MainActivity", "Successfully loaded ${fetchedChats.size} chats from server")
            }.onFailure { err ->
                SpotlinLogger.w("MainActivity", "Failed to fetch chats: ${err.message}")
            }

            apiClient.getUsers().onSuccess { fetchedUsers ->
                allUsers = fetchedUsers
                SpotlinLogger.d("MainActivity", "Successfully loaded ${fetchedUsers.size} users from server")
            }

            apiClient.getCalendar().onSuccess { fetchedLessons ->
                lessons = fetchedLessons
                SpotlinLogger.d("MainActivity", "Successfully loaded ${fetchedLessons.size} lessons from server")
            }

            isLoadingChats = false
        }
    }

    // Helper: refresh messages for active chat and mark as read
    fun refreshChatMessages(chatId: String) {
        coroutineScope.launch {
            apiClient.getMessages(chatId).onSuccess { fetchedMsgs ->
                messagesMap = messagesMap.toMutableMap().apply { put(chatId, fetchedMsgs) }
                SpotlinLogger.d("MainActivity", "Loaded ${fetchedMsgs.size} messages for chat $chatId")
            }
            apiClient.markChatRead(chatId)
        }
    }

    // Animated transitions across all screens
    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220)) + slideInHorizontally(animationSpec = tween(250)) { it / 4 })
                .togetherWith(fadeOut(animationSpec = tween(180)) + slideOutHorizontally(animationSpec = tween(250)) { -it / 4 })
        },
        label = "SpotlinScreenTransition"
    ) { screen ->
        when (screen) {
            is Screen.Auth -> {
                SpotlinLogger.d("MainActivity", "Active Screen: Auth (Login/Register)")
                SpotlinAuthScreen(
                    errorMessage = authErrorMessage,
                    isLoading = isAuthLoading,
                    onLogin = { username, password, isRegister, bio ->
                        isAuthLoading = true
                        authErrorMessage = null
                        coroutineScope.launch {
                            val result = if (isRegister) {
                                apiClient.register(username, password, bio)
                            } else {
                                apiClient.login(username, password)
                            }

                            result.onSuccess { (token, user) ->
                                isAuthLoading = false
                                currentUser = user
                                SpotlinLogger.i("MainActivity", "User ${user.username} authenticated successfully. Switching to ChatList.")
                                refreshData()
                                currentScreen = Screen.ChatList
                            }.onFailure { ex ->
                                isAuthLoading = false
                                val err = ex.message ?: "Ошибка подключения к серверу SXT-SV (${hardcodedServer.httpUrl})"
                                authErrorMessage = err
                                SpotlinLogger.e("MainActivity", "Auth error: $err", ex)
                            }
                        }
                    }
                )
            }

            is Screen.ChatList -> {
                val user = currentUser
                if (user == null) {
                    currentScreen = Screen.Auth
                    return@AnimatedContent
                }
                SpotlinLogger.d("MainActivity", "Active Screen: ChatList for ${user.username}")

                SpotlinChatListScreen(
                    currentUser = user,
                    chats = chats,
                    availableUsers = allUsers,
                    isLoadingChats = isLoadingChats,
                    onRefreshChats = { refreshData() },
                    onSelectChat = { selectedChat ->
                        refreshChatMessages(selectedChat.id)
                        currentScreen = Screen.ChatView(selectedChat)
                    },
                    onOpenNewChat = { currentScreen = Screen.NewChatSearch },
                    onOpenProfile = { currentScreen = Screen.Profile },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onOpenAdminPanel = { currentScreen = Screen.AdminPanel },
                    onCreateDirectChat = { targetUser ->
                        coroutineScope.launch {
                            isLoadingChats = true
                            apiClient.createDirectChat(targetUser.id, targetUser.username).onSuccess { newChat ->
                                chats = listOf(newChat) + chats
                                refreshChatMessages(newChat.id)
                                currentScreen = Screen.ChatView(newChat)
                            }.onFailure { err ->
                                SpotlinLogger.e("MainActivity", "Failed to create direct chat", err)
                            }
                            isLoadingChats = false
                        }
                    },
                    onCreateGroupChat = { groupTitle ->
                        coroutineScope.launch {
                            isLoadingChats = true
                            apiClient.createGroupChat(groupTitle).onSuccess { newChat ->
                                chats = listOf(newChat) + chats
                                refreshChatMessages(newChat.id)
                                currentScreen = Screen.ChatView(newChat)
                            }.onFailure { err ->
                                SpotlinLogger.e("MainActivity", "Failed to create group chat", err)
                            }
                            isLoadingChats = false
                        }
                    }
                )
            }

            is Screen.NewChatSearch -> {
                val user = currentUser
                if (user == null) {
                    currentScreen = Screen.Auth
                    return@AnimatedContent
                }
                SpotlinNewChatSearchScreen(
                    currentUser = user,
                    availableUsers = allUsers,
                    onBack = { currentScreen = Screen.ChatList },
                    onSelectUser = { targetUser ->
                        coroutineScope.launch {
                            isLoadingChats = true
                            apiClient.createDirectChat(targetUser.id, targetUser.username).onSuccess { newChat ->
                                chats = listOf(newChat) + chats.filter { it.id != newChat.id }
                                refreshChatMessages(newChat.id)
                                currentScreen = Screen.ChatView(newChat)
                            }.onFailure { err ->
                                SpotlinLogger.e("MainActivity", "Failed to create direct chat with ${targetUser.username}", err)
                            }
                            isLoadingChats = false
                        }
                    }
                )
            }

            is Screen.ChatView -> {
                val user = currentUser
                if (user == null) {
                    currentScreen = Screen.Auth
                    return@AnimatedContent
                }
                val activeChat = screen.chat
                val currentMessages = messagesMap[activeChat.id] ?: emptyList()

                SpotlinChatScreen(
                    chat = activeChat,
                    currentUser = user,
                    messages = currentMessages,
                    lessons = lessons,
                    fontSizeSp = appSettings.fontSizeSp,
                    apiClient = apiClient,
                    onBack = {
                        refreshData()
                        currentScreen = Screen.ChatList
                    },
                    onOpenInfo = { currentScreen = Screen.ChatInfo(activeChat) },
                    onSendMessage = { text ->
                        coroutineScope.launch {
                            apiClient.sendMessage(activeChat.id, text).onSuccess { sentMsg ->
                                val updated = currentMessages + sentMsg
                                messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                                chats = chats.map {
                                    if (it.id == activeChat.id) it.copy(lastMessage = text) else it
                                }
                            }.onFailure { err ->
                                SpotlinLogger.e("MainActivity", "Failed to send message", err)
                            }
                        }
                    },
                    onSendMediaMessage = { mediaUrl, mediaType, caption ->
                        coroutineScope.launch {
                            apiClient.sendMessage(
                                chatId = activeChat.id,
                                ciphertext = caption.ifEmpty { if (mediaType == "audio") "🎤 Голосовое сообщение" else "📎 Медиафайл" },
                                mediaUrl = mediaUrl,
                                mediaType = mediaType
                            ).onSuccess { sentMsg ->
                                val updated = currentMessages + sentMsg
                                messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                            }.onFailure { err ->
                                SpotlinLogger.e("MainActivity", "Failed to send media message", err)
                            }
                        }
                    },
                    onDeleteMessage = { msgToDelete ->
                        coroutineScope.launch {
                            apiClient.deleteMessage(msgToDelete.id).onSuccess {
                                val updated = currentMessages.filter { it.id != msgToDelete.id }
                                messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                            }
                        }
                    },
                    onPinMessage = { msgToPin ->
                        coroutineScope.launch {
                            apiClient.pinMessage(msgToPin.id).onSuccess { newPinnedState ->
                                val updated = currentMessages.map {
                                    if (it.id == msgToPin.id) it.copy(isPinned = newPinnedState)
                                    else it.copy(isPinned = false)
                                }
                                messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                            }
                        }
                    },
                    onEditMessage = { msgToEdit, newText ->
                        coroutineScope.launch {
                            apiClient.editMessage(msgToEdit.id, newText).onSuccess {
                                val updated = currentMessages.map {
                                    if (it.id == msgToEdit.id) it.copy(text = newText, isEdited = true)
                                    else it
                                }
                                messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                            }
                        }
                    },
                    onSaveLesson = { lessonNumber, newHomework, newSubject, newTime, newClass ->
                        coroutineScope.launch {
                            apiClient.updateCalendarLesson(lessonNumber, newHomework).onSuccess {
                                lessons = lessons.map {
                                    if (it.number == lessonNumber) it.copy(
                                        homework = newHomework,
                                        subject = if (newSubject.isNotEmpty()) newSubject else it.subject,
                                        time = if (newTime.isNotEmpty()) newTime else it.time,
                                        schoolClass = if (newClass.isNotEmpty()) newClass else it.schoolClass
                                    ) else it
                                }
                            }
                        }
                    },
                    onReactToMessage = { msgToReact, emoji ->
                        val currentReactions = msgToReact.reactions.toMutableMap()
                        val currentCount = currentReactions[emoji] ?: 0
                        val isSameUserReaction = msgToReact.userReaction == emoji
                        val newUserReaction = if (isSameUserReaction) null else emoji
                        if (isSameUserReaction) {
                            if (currentCount > 1) currentReactions[emoji] = currentCount - 1
                            else currentReactions.remove(emoji)
                        } else {
                            currentReactions[emoji] = currentCount + 1
                        }
                        val updated = currentMessages.map {
                            if (it.id == msgToReact.id) it.copy(
                                reactions = currentReactions,
                                userReaction = newUserReaction
                            ) else it
                        }
                        messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                    }
                )
            }

            is Screen.ChatInfo -> {
                val user = currentUser ?: return@AnimatedContent
                SpotlinChatInfoScreen(
                    chat = screen.chat,
                    currentUser = user,
                    members = allUsers.filter { it.id == screen.chat.otherUserId },
                    onBack = { currentScreen = Screen.ChatView(screen.chat) },
                    onOpenAdminPanel = { currentScreen = Screen.AdminPanel },
                    onDeleteChat = {
                        coroutineScope.launch {
                            apiClient.deleteChat(screen.chat.id).onSuccess {
                                chats = chats.filter { it.id != screen.chat.id }
                                messagesMap = messagesMap.toMutableMap().apply { remove(screen.chat.id) }
                                currentScreen = Screen.ChatList
                            }.onFailure { err ->
                                SpotlinLogger.e("MainActivity", "Failed to delete chat", err)
                            }
                        }
                    },
                    onClearHistory = {
                        coroutineScope.launch {
                            apiClient.clearChatHistory(screen.chat.id).onSuccess {
                                messagesMap = messagesMap.toMutableMap().apply { put(screen.chat.id, emptyList()) }
                                chats = chats.map { if (it.id == screen.chat.id) it.copy(lastMessage = "") else it }
                                currentScreen = Screen.ChatView(screen.chat)
                            }.onFailure { err ->
                                SpotlinLogger.e("MainActivity", "Failed to clear chat history", err)
                            }
                        }
                    },
                    onToggleMute = { isMuted ->
                        chats = chats.map { if (it.id == screen.chat.id) it.copy(isMuted = isMuted) else it }
                    }
                )
            }

            is Screen.Profile -> {
                val user = currentUser
                if (user == null) {
                    currentScreen = Screen.Auth
                    return@AnimatedContent
                }
                SpotlinProfileScreen(
                    user = user,
                    apiClient = apiClient,
                    onBack = { currentScreen = Screen.ChatList },
                    onSaveProfile = { newBio, newAvatar ->
                        coroutineScope.launch {
                            apiClient.updateProfile(newBio, newAvatar).onSuccess { updatedUser ->
                                currentUser = updatedUser
                                SpotlinLogger.i("MainActivity", "Profile updated on server")
                            }
                        }
                    },
                    onLogout = {
                        SpotlinLogger.i("MainActivity", "User logged out")
                        apiClient.authToken = null
                        currentUser = null
                        chats = emptyList()
                        messagesMap = emptyMap()
                        currentScreen = Screen.Auth
                    }
                )
            }

            is Screen.Settings -> {
                SpotlinSettingsScreen(
                    currentTheme = currentThemeMode,
                    appSettings = appSettings,
                    allUsers = allUsers,
                    activeSessions = activeSessions,
                    onThemeChanged = onThemeChanged,
                    onSettingsChanged = { newSettings ->
                        appSettings = newSettings
                        SpotlinLogger.i("MainActivity", "Settings updated: fontSize=${newSettings.fontSizeSp}sp")
                    },
                    onRevokeSession = { sessionId ->
                        activeSessions = activeSessions.filter { it.id != sessionId }
                        SpotlinLogger.i("MainActivity", "Terminated session $sessionId")
                    },
                    onTerminateAllOtherSessions = {
                        activeSessions = activeSessions.filter { it.isCurrent }
                        SpotlinLogger.i("MainActivity", "Terminated all other active sessions")
                    },
                    onUnblockUser = { unblockedUserId ->
                        appSettings = appSettings.copy(
                            blockedUserIds = appSettings.blockedUserIds - unblockedUserId
                        )
                        SpotlinLogger.i("MainActivity", "Unblocked user $unblockedUserId")
                    },
                    onBack = { currentScreen = Screen.ChatList }
                )
            }

            is Screen.AdminPanel -> {
                val user = currentUser
                if (user == null || !user.isCreator) {
                    currentScreen = Screen.ChatList
                    return@AnimatedContent
                }
                SpotlinOwnerAdminScreen(
                    currentUser = user,
                    users = allUsers,
                    onBack = { currentScreen = Screen.ChatList },
                    onUpdateUserBadge = { userId, badge, canEditCalendar, canManageChat ->
                        coroutineScope.launch {
                            apiClient.delegateAdminPermissions(userId, badge, canEditCalendar, canManageChat).onSuccess { updatedUser ->
                                allUsers = allUsers.map { if (it.id == userId) updatedUser else it }
                                chats = chats.map {
                                    if (it.otherUserId == userId) it.copy(otherCustomBadge = badge) else it
                                }
                                SpotlinLogger.i("MainActivity", "Admin updated permissions on server for $userId")
                            }
                        }
                    }
                )
            }
        }
    }
}
