package com.spotlin.messenger

import com.spotlin.messenger.data.Chat
import com.spotlin.messenger.data.Message
import com.spotlin.messenger.data.SchoolLesson
import com.spotlin.messenger.data.ServerConfig
import com.spotlin.messenger.data.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotlinClientModelTest {

    @Test
    fun testHardcodedServerDefault() {
        val server = ServerConfig.HARDCODED_SERVER
        assertEquals("sxt_sv", server.id)
        assertEquals("http://f1.rustix.me:31774", server.httpUrl)
        assertEquals("ws://f1.rustix.me:31774/ws", server.wsUrl)
        assertTrue(server.isDefault)
    }

    @Test
    fun testOwnerRolePermissions() {
        val owner = User(
            id = "owner_cris_pixar",
            username = "CrisPixar",
            bio = "Создатель Spotlin",
            isCreator = true,
            customBadge = "Создатель",
            canEditCalendar = true,
            canManageChat = true
        )
        assertTrue(owner.isCreator)
        assertTrue(owner.canEditCalendar)
        assertTrue(owner.canManageChat)
        assertEquals("Создатель", owner.customBadge)
    }

    @Test
    fun testRegularUserRolePermissions() {
        val student = User(
            id = "student_1",
            username = "Иван Иванов",
            bio = "Ученик 10-А",
            isCreator = false,
            customBadge = "",
            canEditCalendar = false,
            canManageChat = false
        )
        assertFalse(student.isCreator)
        assertFalse(student.canEditCalendar)
        assertFalse(student.canManageChat)
        assertEquals("", student.customBadge)
    }

    @Test
    fun testSchoolLessonModel() {
        val lesson = SchoolLesson(
            number = 1,
            subject = "Алгебра",
            time = "08:30 - 09:15",
            homework = "Параграф 14, № 14.5"
        )
        assertEquals(1, lesson.number)
        assertEquals("Алгебра", lesson.subject)
        assertEquals("08:30 - 09:15", lesson.time)
        assertEquals("Параграф 14, № 14.5", lesson.homework)
    }

    @Test
    fun testDirectChatMessageModel() {
        val chat = Chat(
            id = "chat_direct_1",
            type = "direct",
            title = "Преподаватель",
            otherUserId = "teacher_1",
            otherUsername = "Преподаватель",
            otherCustomBadge = "Учитель",
            lastMessage = "Проверьте календарь"
        )
        assertEquals("direct", chat.type)
        assertEquals("Учитель", chat.otherCustomBadge)

        val message = Message(
            id = "m1",
            chatId = chat.id,
            senderId = "teacher_1",
            senderUsername = "Преподаватель",
            senderBadge = "Учитель",
            text = "Проверьте календарь",
            isMine = false,
            isPinned = true
        )
        assertTrue(message.isPinned)
        assertFalse(message.isMine)
        assertEquals("Учитель", message.senderBadge)
    }

    @Test
    fun testAppSettingsModel() {
        val settings = com.spotlin.messenger.data.AppSettings(
            fontSizeSp = 18f,
            blockedUserIds = setOf("bad_user_1", "spammer_2")
        )
        assertEquals(18f, settings.fontSizeSp, 0.01f)
        assertTrue(settings.blockedUserIds.contains("bad_user_1"))
        assertEquals("Все", settings.lastSeenVisibility)
    }

    @Test
    fun testActiveSessionModel() {
        val session = com.spotlin.messenger.data.ActiveSession(
            id = "s1",
            deviceName = "Spotlin Android App",
            appVersion = "v2.5.0",
            ipAddress = "192.168.1.1",
            lastActive = "Онлайн",
            isCurrent = true
        )
        assertEquals("s1", session.id)
        assertTrue(session.isCurrent)
    }

    @Test
    fun testUserHandleAndOnlineStatus() {
        val user = User(
            id = "u1",
            username = "Alex",
            isOnline = true
        )
        assertEquals("@Alex", user.handle)
        assertTrue(user.isOnline)
    }

    @Test
    fun testMessageReactionsAndReply() {
        val msg = Message(
            id = "m_react",
            chatId = "c1",
            senderId = "u1",
            text = "Привет всем",
            isMine = true,
            reactions = mapOf("❤️" to 3, "👍" to 5),
            userReaction = "👍",
            replyToMessageId = "m_orig",
            replyToText = "Исходный текст"
        )
        assertEquals(5, msg.reactions["👍"])
        assertEquals("👍", msg.userReaction)
        assertEquals("m_orig", msg.replyToMessageId)
    }
}
