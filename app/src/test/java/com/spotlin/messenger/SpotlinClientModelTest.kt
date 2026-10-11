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
            title = "Мария Ивановна",
            otherUserId = "maria_ivanovna",
            otherUsername = "Мария Ивановна",
            otherCustomBadge = "Учитель",
            lastMessage = "Проверьте календарь"
        )
        assertEquals("direct", chat.type)
        assertEquals("Учитель", chat.otherCustomBadge)

        val message = Message(
            id = "m1",
            chatId = chat.id,
            senderId = "maria_ivanovna",
            senderUsername = "Мария Ивановна",
            senderBadge = "Учитель",
            text = "Проверьте календарь",
            isMine = false,
            isPinned = true
        )
        assertTrue(message.isPinned)
        assertFalse(message.isMine)
        assertEquals("Учитель", message.senderBadge)
    }
}
