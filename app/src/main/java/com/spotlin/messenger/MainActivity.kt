package com.spotlin.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
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

    // Real server-loaded state (No mocks)
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var lessons by remember { mutableStateOf<List<SchoolLesson>>(emptyList()) }
    var messagesMap by remember { mutableStateOf<Map<String, List<Message>>>(emptyMap()) }

    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var isLoadingChats by remember { mutableStateOf(false) }

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

    // Helper: refresh messages for active chat
    fun refreshChatMessages(chatId: String) {
        coroutineScope.launch {
            apiClient.getMessages(chatId).onSuccess { fetchedMsgs ->
                messagesMap = messagesMap.toMutableMap().apply { put(chatId, fetchedMsgs) }
                SpotlinLogger.d("MainActivity", "Loaded ${fetchedMsgs.size} messages for chat $chatId")
            }
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
                    onSaveLesson = { lessonNumber, newHomework ->
                        coroutineScope.launch {
                            apiClient.updateCalendarLesson(lessonNumber, newHomework).onSuccess {
                                lessons = lessons.map {
                                    if (it.number == lessonNumber) it.copy(homework = newHomework) else it
                                }
                            }
                        }
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
                    onOpenAdminPanel = { currentScreen = Screen.AdminPanel }
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
                    onThemeChanged = onThemeChanged,
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
