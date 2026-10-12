package com.spotlin.messenger.data

data class User(
    val id: String,
    val username: String, // format without @
    val bio: String = "",
    val avatarUrl: String = "",
    val role: String = "user",
    val isCreator: Boolean = false,
    val customBadge: String = "",
    val canEditCalendar: Boolean = false,
    val canManageChat: Boolean = false,
    val isVerified: Boolean = false,
    val isOnline: Boolean = false,
    val lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    val handle: String
        get() = "@$username"
}

data class Chat(
    val id: String,
    val type: String = "direct", // "direct" or "group"
    val title: String,
    val otherUserId: String = "",
    val otherUsername: String = "",
    val otherCustomBadge: String = "",
    val otherIsCreator: Boolean = false,
    val otherIsVerified: Boolean = false,
    val otherIsOnline: Boolean = false,
    val lastMessage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val pinnedMessageId: String? = null,
    val isMuted: Boolean = false
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderUsername: String = "",
    val senderBadge: String = "",
    val senderIsCreator: Boolean = false,
    var text: String,
    val isMine: Boolean = false,
    var isPinned: Boolean = false,
    var isEdited: Boolean = false,
    var isRead: Boolean = false,
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "photo", "audio", "video", "file"
    val mediaDuration: Float = 0f,
    val fileName: String? = null,
    val fileSize: String? = null,
    val audioTitle: String? = null,
    val audioPerformer: String? = null,
    val reactions: Map<String, Int> = emptyMap(), // emoji -> count
    val userReaction: String? = null,
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToSender: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class SchoolLesson(
    val number: Int,
    var subject: String,
    var time: String,
    var homework: String = "",
    var schoolClass: String = ""
)

data class ActiveSession(
    val id: String,
    val deviceName: String,
    val appVersion: String,
    val ipAddress: String,
    val lastActive: String,
    val isCurrent: Boolean = false
)

data class AppSettings(
    val fontSizeSp: Float = 16f,
    val notificationsEnabled: Boolean = true,
    val soundEnabled: Boolean = true,
    val vibrationEnabled: Boolean = true,
    val autoDownloadMedia: Boolean = true,
    val highQualityAudio: Boolean = true,
    // Privacy
    val lastSeenVisibility: String = "Все", // "Все", "Контакты", "Никто"
    val profilePhotoVisibility: String = "Все",
    val callsVisibility: String = "Все",
    val blockedUserIds: Set<String> = emptySet()
)
