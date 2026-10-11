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
import com.spotlin.messenger.ui.server.SpotlinServerPickerScreen
import com.spotlin.messenger.ui.theme.SpotlinTheme
import com.spotlin.messenger.ui.theme.ThemeMode
import kotlinx.coroutines.launch

sealed class Screen {
    object Auth : Screen()
    object ChatList : Screen()
    data class ChatView(val chat: Chat) : Screen()
    data class ChatInfo(val chat: Chat) : Screen()
    object Profile : Screen()
    object Settings : Screen()
    object AdminPanel : Screen()
    object ServerPicker : Screen()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentThemeMode by remember { mutableStateOf(ThemeMode.MATERIAL_YOU) }

            SpotlinTheme(themeMode = currentThemeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpotlinApp(
                        currentThemeMode = currentThemeMode,
                        onThemeChanged = { currentThemeMode = it }
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

    // Server selection state (Default SXT-SV: f1.rustix.me:31774)
    var serversList by remember { mutableStateOf(ServerConfig.DEFAULT_SERVERS) }
    var currentServer by remember { mutableStateOf(serversList.first { it.isDefault }) }
    val apiClient = remember(currentServer) { SpotlinApiClient(baseUrl = currentServer.httpUrl) }

    // Current user state (CrisPixar default / logged-in user)
    var currentUser by remember {
        mutableStateOf<User?>(
            User(
                id = "cris_pixar_owner",
                username = "CrisPixar",
                bio = "Создатель и разработчик школьного мессенджера Spotlin",
                role = "owner",
                isCreator = true,
                customBadge = "Создатель",
                canEditCalendar = true,
                canManageChat = true,
                isVerified = true
            )
        )
    }

    // Users in School Directory
    var allUsers by remember {
        mutableStateOf(
            listOf(
                User(
                    id = "cris_pixar_owner",
                    username = "CrisPixar",
                    bio = "Создатель и разработчик Spotlin",
                    isCreator = true,
                    customBadge = "Создатель",
                    canEditCalendar = true,
                    canManageChat = true
                ),
                User(
                    id = "maria_ivanovna",
                    username = "Мария Ивановна",
                    bio = "Классный руководитель 10-А класса",
                    customBadge = "Учитель",
                    canEditCalendar = true,
                    canManageChat = true
                ),
                User(
                    id = "alex_starosta",
                    username = "Алексей Смирнов",
                    bio = "Староста 10-А класса",
                    customBadge = "Староста",
                    canEditCalendar = true,
                    canManageChat = false
                ),
                User(
                    id = "dmitry_student",
                    username = "Дмитрий Ковалев",
                    bio = "Ученик 10-А",
                    customBadge = "",
                    canEditCalendar = false,
                    canManageChat = false
                )
            )
        )
    }

    // Chats List
    var chats by remember {
        mutableStateOf(
            listOf(
                Chat(
                    id = "chat_1",
                    type = "direct",
                    title = "Мария Ивановна",
                    otherUserId = "maria_ivanovna",
                    otherUsername = "Мария Ивановна",
                    otherCustomBadge = "Учитель",
                    otherIsCreator = false,
                    lastMessage = "Не забудьте подготовить доклад по физике к четвергу!"
                ),
                Chat(
                    id = "chat_2",
                    type = "direct",
                    title = "Алексей Смирнов",
                    otherUserId = "alex_starosta",
                    otherUsername = "Алексей Смирнов",
                    otherCustomBadge = "Староста",
                    otherIsCreator = false,
                    lastMessage = "Я обновил домашнее задание в календаре."
                ),
                Chat(
                    id = "chat_3",
                    type = "direct",
                    title = "Дмитрий Ковалев",
                    otherUserId = "dmitry_student",
                    otherUsername = "Дмитрий Ковалев",
                    otherCustomBadge = "",
                    otherIsCreator = false,
                    lastMessage = "Привет! Скинь номер упражнения по алгебре"
                )
            )
        )
    }

    // Messages repository per chat
    var messagesMap by remember {
        mutableStateOf(
            mutableMapOf(
                "chat_1" to listOf(
                    Message(
                        id = "m1",
                        chatId = "chat_1",
                        senderId = "maria_ivanovna",
                        senderUsername = "Мария Ивановна",
                        senderBadge = "Учитель",
                        text = "Здравствуйте! Напоминаю расписание на завтра.",
                        isMine = false,
                        isPinned = true
                    ),
                    Message(
                        id = "m2",
                        chatId = "chat_1",
                        senderId = "cris_pixar_owner",
                        senderUsername = "CrisPixar",
                        senderBadge = "Создатель",
                        senderIsCreator = true,
                        text = "Здравствуйте, Мария Ивановна! Все проверил в календаре.",
                        isMine = true
                    ),
                    Message(
                        id = "m3",
                        chatId = "chat_1",
                        senderId = "maria_ivanovna",
                        senderUsername = "Мария Ивановна",
                        senderBadge = "Учитель",
                        text = "Не забудьте подготовить доклад по физике к четвергу!",
                        isMine = false
                    )
                ),
                "chat_2" to listOf(
                    Message(
                        id = "m2_1",
                        chatId = "chat_2",
                        senderId = "alex_starosta",
                        senderUsername = "Алексей Смирнов",
                        senderBadge = "Староста",
                        text = "Я обновил домашнее задание в календаре.",
                        isMine = false
                    )
                ),
                "chat_3" to listOf(
                    Message(
                        id = "m3_1",
                        chatId = "chat_3",
                        senderId = "dmitry_student",
                        senderUsername = "Дмитрий Ковалев",
                        senderBadge = "",
                        text = "Привет! Скинь номер упражнения по алгебре",
                        isMine = false
                    )
                )
            )
        )
    }

    // School Schedule
    var lessons by remember {
        mutableStateOf(
            listOf(
                SchoolLesson(1, "Алгебра", "08:30 - 09:15", "Параграф 14, № 14.5, 14.8"),
                SchoolLesson(2, "Геометрия", "09:25 - 10:10", "Теорема о трех перпендикулярах, доказательство"),
                SchoolLesson(3, "Физика", "10:30 - 11:15", "Подготовить доклад: Законы термодинамики"),
                SchoolLesson(4, "Русский язык", "11:35 - 12:20", "Упражнение 218 (подчеркнуть грамматические основы)"),
                SchoolLesson(5, "Литература", "12:35 - 13:20", "Читать главы 4-6 романа 'Война и мир'"),
                SchoolLesson(6, "Информатика", "13:30 - 14:15", "Написать программу в Spotlin Engine")
            )
        )
    }

    var currentScreen by remember { mutableStateOf<Screen>(Screen.ChatList) }

    // Screen transition with smooth micro-animations
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
                SpotlinAuthScreen(
                    currentServer = currentServer,
                    onOpenServerPicker = { currentScreen = Screen.ServerPicker },
                    onLogin = { userLogin, password, isRegister ->
                        coroutineScope.launch {
                            val result = if (isRegister) {
                                apiClient.register(userLogin, password)
                            } else {
                                apiClient.login(userLogin, password)
                            }
                            result.onSuccess { (_, user) ->
                                currentUser = user
                                currentScreen = Screen.ChatList
                            }.onFailure {
                                // Fallback for offline or local preview
                                val isOwner = userLogin.equals("CrisPixar", ignoreCase = true)
                                currentUser = User(
                                    id = if (isOwner) "cris_pixar_owner" else "user_${System.currentTimeMillis()}",
                                    username = userLogin,
                                    isCreator = isOwner,
                                    customBadge = if (isOwner) "Создатель" else "",
                                    canEditCalendar = isOwner,
                                    canManageChat = isOwner
                                )
                                currentScreen = Screen.ChatList
                            }
                        }
                    }
                )
            }

            is Screen.ServerPicker -> {
                SpotlinServerPickerScreen(
                    currentServer = currentServer,
                    servers = serversList,
                    onSelectServer = { server ->
                        currentServer = server
                        apiClient.setServerUrl(server.httpUrl)
                        currentScreen = Screen.ChatList
                    },
                    onAddCustomServer = { name, url ->
                        val custom = ServerConfig(
                            id = "custom_${System.currentTimeMillis()}",
                            name = name,
                            httpUrl = url,
                            wsUrl = url.replace("http", "ws") + "/ws"
                        )
                        serversList = serversList + custom
                        currentServer = custom
                        apiClient.setServerUrl(custom.httpUrl)
                    },
                    onBack = { currentScreen = Screen.ChatList }
                )
            }

            is Screen.ChatList -> {
                val user = currentUser ?: return@AnimatedContent
                SpotlinChatListScreen(
                    currentUser = user,
                    chats = chats,
                    onSelectChat = { selectedChat ->
                        currentScreen = Screen.ChatView(selectedChat)
                    },
                    onOpenProfile = { currentScreen = Screen.Profile },
                    onOpenSettings = { currentScreen = Screen.Settings },
                    onOpenAdminPanel = { currentScreen = Screen.AdminPanel },
                    onOpenServerPicker = { currentScreen = Screen.ServerPicker },
                    onNewDirectChat = {
                        val newChatId = "chat_${System.currentTimeMillis()}"
                        val newChat = Chat(
                            id = newChatId,
                            type = "direct",
                            title = "Новый контакт",
                            otherUserId = "new_contact",
                            otherUsername = "Новый контакт",
                            lastMessage = "Диалог создан"
                        )
                        chats = listOf(newChat) + chats
                        messagesMap[newChatId] = emptyList()
                        currentScreen = Screen.ChatView(newChat)
                    }
                )
            }

            is Screen.ChatView -> {
                val user = currentUser ?: return@AnimatedContent
                val activeChat = screen.chat
                val currentMessages = messagesMap[activeChat.id] ?: emptyList()

                SpotlinChatScreen(
                    chat = activeChat,
                    currentUser = user,
                    messages = currentMessages,
                    lessons = lessons,
                    onBack = { currentScreen = Screen.ChatList },
                    onOpenInfo = { currentScreen = Screen.ChatInfo(activeChat) },
                    onSendMessage = { text ->
                        val newMsg = Message(
                            id = "msg_${System.currentTimeMillis()}",
                            chatId = activeChat.id,
                            senderId = user.id,
                            senderUsername = user.username,
                            senderBadge = user.customBadge,
                            senderIsCreator = user.isCreator,
                            text = text,
                            isMine = true
                        )
                        val updated = currentMessages + newMsg
                        messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                        chats = chats.map {
                            if (it.id == activeChat.id) it.copy(lastMessage = text) else it
                        }
                    },
                    onDeleteMessage = { msgToDelete ->
                        val updated = currentMessages.filter { it.id != msgToDelete.id }
                        messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                    },
                    onPinMessage = { msgToPin ->
                        val updated = currentMessages.map {
                            if (it.id == msgToPin.id) it.copy(isPinned = !it.isPinned)
                            else it.copy(isPinned = false)
                        }
                        messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                    },
                    onEditMessage = { msgToEdit, newText ->
                        val updated = currentMessages.map {
                            if (it.id == msgToEdit.id) it.copy(text = newText, isEdited = true)
                            else it
                        }
                        messagesMap = messagesMap.toMutableMap().apply { put(activeChat.id, updated) }
                    },
                    onSaveLesson = { lessonNumber, newHomework ->
                        lessons = lessons.map {
                            if (it.number == lessonNumber) it.copy(homework = newHomework) else it
                        }
                    }
                )
            }

            is Screen.ChatInfo -> {
                val user = currentUser ?: return@AnimatedContent
                SpotlinChatInfoScreen(
                    chat = screen.chat,
                    currentUser = user,
                    members = allUsers.filter { it.username == screen.chat.otherUsername },
                    onBack = { currentScreen = Screen.ChatView(screen.chat) },
                    onOpenAdminPanel = { currentScreen = Screen.AdminPanel }
                )
            }

            is Screen.Profile -> {
                val user = currentUser ?: return@AnimatedContent
                SpotlinProfileScreen(
                    user = user,
                    onBack = { currentScreen = Screen.ChatList },
                    onSaveProfile = { bio, avatar ->
                        currentUser = user.copy(bio = bio, avatarUrl = avatar)
                    },
                    onLogout = {
                        currentUser = null
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
                val user = currentUser ?: return@AnimatedContent
                SpotlinOwnerAdminScreen(
                    currentUser = user,
                    users = allUsers,
                    onBack = { currentScreen = Screen.ChatList },
                    onUpdateUserBadge = { userId, badge, canEditCalendar, canManageChat ->
                        allUsers = allUsers.map { u ->
                            if (u.id == userId) u.copy(
                                customBadge = badge,
                                canEditCalendar = canEditCalendar,
                                canManageChat = canManageChat
                            ) else u
                        }
                        chats = chats.map { c ->
                            if (c.otherUserId == userId) c.copy(otherCustomBadge = badge) else c
                        }
                    }
                )
            }
        }
    }
}
