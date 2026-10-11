package com.spotlin.messenger.network

import com.spotlin.messenger.BuildConfig
import com.spotlin.messenger.data.Chat
import com.spotlin.messenger.data.Message
import com.spotlin.messenger.data.SchoolLesson
import com.spotlin.messenger.data.User
import com.spotlin.messenger.util.SpotlinLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

class SpotlinApiClient(
    private var baseUrl: String = "http://f1.rustix.me:31774"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .apply {
            if (BuildConfig.DEBUG) {
                val logging = HttpLoggingInterceptor { message ->
                    SpotlinLogger.d("SpotlinHttp", message)
                }.apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(logging)
            }
        }
        .build()

    var authToken: String? = null
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun setServerUrl(url: String) {
        baseUrl = url.trimEnd('/')
        SpotlinLogger.d("SpotlinApiClient", "Server URL configured: $baseUrl")
    }

    suspend fun register(username: String, password: String, bio: String = ""): Result<Pair<String, User>> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinAuth", "Register request initiated for username: $username")
        try {
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
                put("bio", bio)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/auth/register")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = try { JSONObject(body).optString("error", "Registration error") } catch (_: Exception) { "HTTP ${response.code}" }
                SpotlinLogger.w("SpotlinAuth", "Registration failed: $err")
                return@withContext Result.failure(Exception(err))
            }

            val obj = JSONObject(body)
            val token = obj.getString("token")
            val userObj = obj.getJSONObject("user")
            authToken = token
            val user = parseUser(userObj)
            SpotlinLogger.i("SpotlinAuth", "Registration succeeded for user: ${user.username} (id: ${user.id})")
            Result.success(Pair(token, user))
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinAuth", "Registration network error", e)
            Result.failure(e)
        }
    }

    suspend fun login(username: String, password: String): Result<Pair<String, User>> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinAuth", "Login request initiated for username: $username")
        try {
            val json = JSONObject().apply {
                put("username", username)
                put("password", password)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = try { JSONObject(body).optString("error", "Invalid credentials") } catch (_: Exception) { "HTTP ${response.code}" }
                SpotlinLogger.w("SpotlinAuth", "Login failed: $err")
                return@withContext Result.failure(Exception(err))
            }

            val obj = JSONObject(body)
            val token = obj.getString("token")
            val userObj = obj.getJSONObject("user")
            authToken = token
            val user = parseUser(userObj)
            SpotlinLogger.i("SpotlinAuth", "Login succeeded for user: ${user.username}")
            Result.success(Pair(token, user))
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinAuth", "Login network exception", e)
            Result.failure(e)
        }
    }

    suspend fun getChats(): Result<List<Chat>> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Fetching chats list from server...")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/chats")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                SpotlinLogger.w("SpotlinChat", "GetChats returned HTTP ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val array = JSONArray(body)
            val list = mutableListOf<Chat>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    Chat(
                        id = item.optString("id"),
                        type = item.optString("type", "direct"),
                        title = item.optString("title", "Диалог"),
                        otherUserId = item.optString("other_user_id", ""),
                        otherUsername = item.optString("other_username", item.optString("title")),
                        otherCustomBadge = item.optString("other_badge", ""),
                        otherIsCreator = item.optInt("other_is_creator", 0) == 1,
                        lastMessage = item.optString("last_message", ""),
                        timestamp = (item.optDouble("last_time", 0.0) * 1000).toLong()
                    )
                )
            }
            SpotlinLogger.d("SpotlinChat", "Loaded ${list.size} chats")
            Result.success(list)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinChat", "Error getting chats", e)
            Result.failure(e)
        }
    }

    suspend fun getMessages(chatId: String): Result<List<Message>> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Fetching messages for chat: $chatId")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/chats/$chatId/messages")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                SpotlinLogger.w("SpotlinChat", "GetMessages returned HTTP ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val array = JSONArray(body)
            val list = mutableListOf<Message>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    Message(
                        id = item.optString("id"),
                        chatId = item.optString("chat_id"),
                        senderId = item.optString("sender_id"),
                        senderUsername = item.optString("sender_username", ""),
                        senderBadge = item.optString("sender_badge", ""),
                        senderIsCreator = item.optInt("sender_is_creator", 0) == 1,
                        text = item.optString("ciphertext", ""),
                        isMine = item.optString("sender_id") == authToken,
                        isPinned = item.optInt("is_pinned", 0) == 1,
                        isEdited = item.optInt("is_edited", 0) == 1,
                        mediaUrl = if (item.isNull("media_url")) null else item.optString("media_url"),
                        mediaType = if (item.isNull("media_type")) null else item.optString("media_type"),
                        mediaDuration = item.optDouble("media_duration", 0.0).toFloat(),
                        timestamp = (item.optDouble("created_at", 0.0) * 1000).toLong()
                    )
                )
            }
            SpotlinLogger.d("SpotlinChat", "Loaded ${list.size} messages for chat $chatId")
            Result.success(list)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinChat", "Error getting messages for chat $chatId", e)
            Result.failure(e)
        }
    }

    suspend fun sendMessage(chatId: String, ciphertext: String, iv: String = "", mediaUrl: String? = null, mediaType: String? = null): Result<Message> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Sending message to chat $chatId (hasMedia: ${mediaUrl != null})")
        try {
            val json = JSONObject().apply {
                put("ciphertext", ciphertext)
                put("iv", iv)
                if (mediaUrl != null) put("media_url", mediaUrl)
                if (mediaType != null) put("media_type", mediaType)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/chats/$chatId/messages")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                SpotlinLogger.w("SpotlinChat", "SendMessage error HTTP ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val item = JSONObject(body)
            val msg = Message(
                id = item.optString("id"),
                chatId = item.optString("chat_id"),
                senderId = item.optString("sender_id"),
                senderUsername = item.optString("sender_username", ""),
                senderBadge = item.optString("sender_badge", ""),
                senderIsCreator = item.optBoolean("sender_is_creator", false),
                text = item.optString("ciphertext", ""),
                isMine = true,
                mediaUrl = if (item.isNull("media_url")) null else item.optString("media_url"),
                mediaType = if (item.isNull("media_type")) null else item.optString("media_type"),
                timestamp = (item.optDouble("created_at", 0.0) * 1000).toLong()
            )
            SpotlinLogger.i("SpotlinChat", "Message sent successfully: ${msg.id}")
            Result.success(msg)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinChat", "SendMessage exception", e)
            Result.failure(e)
        }
    }

    suspend fun deleteMessage(messageId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Deleting message $messageId")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/messages/$messageId")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            SpotlinLogger.i("SpotlinChat", "DeleteMessage status: ${response.isSuccessful}")
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinChat", "DeleteMessage exception", e)
            Result.failure(e)
        }
    }

    suspend fun pinMessage(messageId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Toggling pin on message $messageId")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/messages/$messageId/pin")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            val obj = JSONObject(body)
            val isPinned = obj.optBoolean("is_pinned", false)
            SpotlinLogger.i("SpotlinChat", "Message $messageId pin state: $isPinned")
            Result.success(isPinned)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinChat", "PinMessage exception", e)
            Result.failure(e)
        }
    }

    suspend fun getCalendar(): Result<List<SchoolLesson>> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinCalendar", "Fetching school calendar schedule...")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/calendar")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                SpotlinLogger.w("SpotlinCalendar", "GetCalendar returned HTTP ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val array = JSONArray(body)
            val list = mutableListOf<SchoolLesson>()
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                list.add(
                    SchoolLesson(
                        number = item.optInt("lesson_number", 1),
                        subject = item.optString("subject", ""),
                        time = item.optString("time_range", ""),
                        homework = item.optString("homework", "")
                    )
                )
            }
            SpotlinLogger.i("SpotlinCalendar", "Loaded ${list.size} lessons from calendar")
            Result.success(list)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinCalendar", "GetCalendar exception", e)
            Result.failure(e)
        }
    }

    suspend fun updateCalendarLesson(lessonId: Int, homework: String): Result<Boolean> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinCalendar", "Updating lesson $lessonId homework: $homework")
        try {
            val json = JSONObject().apply { put("homework", homework) }
            val request = Request.Builder()
                .url("$baseUrl/api/calendar/$lessonId")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .put(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            SpotlinLogger.i("SpotlinCalendar", "UpdateCalendar status: ${response.isSuccessful}")
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinCalendar", "UpdateCalendar exception", e)
            Result.failure(e)
        }
    }

    private fun parseUser(obj: JSONObject): User {
        return User(
            id = obj.getString("id"),
            username = obj.getString("username"),
            bio = obj.optString("bio", ""),
            avatarUrl = obj.optString("avatar_url", ""),
            role = obj.optString("role", "user"),
            isCreator = obj.optInt("is_creator", 0) == 1,
            customBadge = obj.optString("custom_badge", ""),
            canEditCalendar = obj.optInt("can_edit_calendar", 0) == 1,
            canManageChat = obj.optInt("can_manage_chat", 0) == 1
        )
    }
}
