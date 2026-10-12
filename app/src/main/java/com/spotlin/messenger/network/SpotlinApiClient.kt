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
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SpotlinApiClient(
    private var baseUrl: String = "http://f1.rustix.me:31774"
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
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
    var currentUserId: String? = null
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    fun setServerUrl(url: String) {
        baseUrl = url.trimEnd('/')
        SpotlinLogger.d("SpotlinApiClient", "Server URL configured: $baseUrl")
    }

    fun getServerUrl(): String = baseUrl

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
                val err = try { JSONObject(body).optString("error", "Ошибка регистрации") } catch (_: Exception) { "HTTP ${response.code}" }
                SpotlinLogger.w("SpotlinAuth", "Registration failed: $err")
                return@withContext Result.failure(Exception(err))
            }

            val obj = JSONObject(body)
            val token = obj.getString("token")
            val userObj = obj.getJSONObject("user")
            authToken = token
            val user = parseUser(userObj)
            currentUserId = user.id
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
                val err = try { JSONObject(body).optString("error", "Неверный логин или пароль") } catch (_: Exception) { "HTTP ${response.code}" }
                SpotlinLogger.w("SpotlinAuth", "Login failed: $err")
                return@withContext Result.failure(Exception(err))
            }

            val obj = JSONObject(body)
            val token = obj.getString("token")
            val userObj = obj.getJSONObject("user")
            authToken = token
            val user = parseUser(userObj)
            currentUserId = user.id
            SpotlinLogger.i("SpotlinAuth", "Login succeeded for user: ${user.username}")
            Result.success(Pair(token, user))
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinAuth", "Login network exception", e)
            Result.failure(e)
        }
    }

    suspend fun getUsers(): Result<List<User>> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinUsers", "Fetching users list from server...")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/users")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }

            val array = JSONArray(body)
            val list = mutableListOf<User>()
            for (i in 0 until array.length()) {
                list.add(parseUser(array.getJSONObject(i)))
            }
            Result.success(list)
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinUsers", "Error fetching users", e)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(bio: String, avatarUrl: String): Result<User> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinProfile", "Updating user profile on server...")
        try {
            val json = JSONObject().apply {
                put("bio", bio)
                put("avatar_url", avatarUrl)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/users/profile")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .put(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))

            Result.success(parseUser(JSONObject(body)))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun delegateAdminPermissions(targetUserId: String, customBadge: String, canEditCalendar: Boolean, canManageChat: Boolean): Result<User> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinAdmin", "Delegating permissions to $targetUserId")
        try {
            val json = JSONObject().apply {
                put("user_id", targetUserId)
                put("custom_badge", customBadge)
                put("can_edit_calendar", canEditCalendar)
                put("can_manage_chat", canManageChat)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/admin/delegate")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("HTTP ${response.code}"))

            Result.success(parseUser(JSONObject(body)))
        } catch (e: Exception) {
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

    suspend fun createDirectChat(targetUserId: String, title: String = "Диалог"): Result<Chat> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Creating direct chat with target: $targetUserId")
        try {
            val json = JSONObject().apply {
                put("type", "direct")
                put("target_user_id", targetUserId)
                put("title", title)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/chats")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = try { JSONObject(body).optString("error", "Failed to create chat") } catch (_: Exception) { "HTTP ${response.code}" }
                return@withContext Result.failure(Exception(err))
            }

            val obj = JSONObject(body)
            Result.success(
                Chat(
                    id = obj.getString("id"),
                    type = obj.optString("type", "direct"),
                    title = obj.optString("title", title),
                    otherUserId = targetUserId,
                    otherUsername = title,
                    lastMessage = "Диалог создан"
                )
            )
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinChat", "Error creating direct chat", e)
            Result.failure(e)
        }
    }

    suspend fun createGroupChat(title: String): Result<Chat> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Creating group chat with title: $title")
        try {
            val json = JSONObject().apply {
                put("type", "group")
                put("title", title)
            }
            val request = Request.Builder()
                .url("$baseUrl/api/chats")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .post(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                val err = try { JSONObject(body).optString("error", "Failed to create group") } catch (_: Exception) { "HTTP ${response.code}" }
                return@withContext Result.failure(Exception(err))
            }

            val obj = JSONObject(body)
            Result.success(
                Chat(
                    id = obj.getString("id"),
                    type = "group",
                    title = obj.optString("title", title),
                    lastMessage = "Группа создана"
                )
            )
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinChat", "Error creating group chat", e)
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Deleting chat $chatId")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/chats/$chatId")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun clearChatHistory(chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Clearing history for chat $chatId")
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/chats/$chatId/messages")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .delete()
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun markChatRead(chatId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/chats/$chatId/read")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .post("{}".toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
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
                        isMine = (currentUserId != null && item.optString("sender_id") == currentUserId),
                        isPinned = item.optInt("is_pinned", 0) == 1,
                        isEdited = item.optInt("is_edited", 0) == 1,
                        isRead = item.optInt("is_read", 0) == 1,
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
                isRead = false,
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

    suspend fun uploadFile(bytes: ByteArray, filename: String, mimeType: String): Result<Pair<String, String>> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinUpload", "Uploading file $filename (${bytes.size} bytes)")
        try {
            val mediaType = mimeType.toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", filename, bytes.toRequestBody(mediaType))
                .build()

            val request = Request.Builder()
                .url("$baseUrl/api/upload")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (!response.isSuccessful) return@withContext Result.failure(Exception("Upload HTTP ${response.code}"))

            val obj = JSONObject(body)
            val url = obj.getString("url")
            val type = obj.optString("media_type", "file")
            Result.success(Pair(url, type))
        } catch (e: Exception) {
            SpotlinLogger.e("SpotlinUpload", "Upload error", e)
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
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun editMessage(messageId: String, newText: String): Result<Boolean> = withContext(Dispatchers.IO) {
        SpotlinLogger.d("SpotlinChat", "Editing message $messageId")
        try {
            val json = JSONObject().apply {
                put("ciphertext", newText)
                put("iv", "")
            }
            val request = Request.Builder()
                .url("$baseUrl/api/messages/$messageId")
                .header("Authorization", "Bearer ${authToken ?: ""}")
                .put(json.toString().toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
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
            Result.success(isPinned)
        } catch (e: Exception) {
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
            Result.success(list)
        } catch (e: Exception) {
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
            Result.success(response.isSuccessful)
        } catch (e: Exception) {
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
            isCreator = obj.optInt("is_creator", 0) == 1 || obj.optBoolean("is_creator", false),
            customBadge = obj.optString("custom_badge", ""),
            canEditCalendar = obj.optInt("can_edit_calendar", 0) == 1 || obj.optBoolean("can_edit_calendar", false),
            canManageChat = obj.optInt("can_manage_chat", 0) == 1 || obj.optBoolean("can_manage_chat", false)
        )
    }
}
