package com.spotlin.messenger.data

data class User(
    val id: String,
    val username: String,
    val bio: String = "",
    val avatarUrl: String = "",
    val role: String = "user",
    val isCreator: Boolean = false,
    val customBadge: String = "",
    val canEditCalendar: Boolean = false,
    val canManageChat: Boolean = false,
    val isVerified: Boolean = false
)

data class Chat(
    val id: String,
    val type: String = "direct", // "direct" or "group"
    val title: String,
    val otherUserId: String = "",
    val otherUsername: String = "",
    val otherCustomBadge: String = "",
    val otherIsCreator: Boolean = false,
    val otherIsVerified: Boolean = false,
    val lastMessage: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val pinnedMessageId: String? = null
)

data class Message(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderUsername: String = "",
    val senderBadge: String = "",
    val senderIsCreator: Boolean = false,
    var text: String,
    val isMine: Boolean,
    var isPinned: Boolean = false,
    var isEdited: Boolean = false,
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "photo", "audio", "video"
    val mediaDuration: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class SchoolLesson(
    val number: Int,
    val subject: String,
    val time: String,
    var homework: String = ""
)
