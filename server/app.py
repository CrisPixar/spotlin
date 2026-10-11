import os
import json
import uuid
import time
from aiohttp import web
from db import init_db, get_connection, hash_password
from compressor import compress_image, compress_audio, compress_video, UPLOAD_DIR, THUMBNAILS_DIR

@web.middleware
async def cors_middleware(request, handler):
    if request.method == "OPTIONS":
        response = web.Response(status=200)
    else:
        try:
            response = await handler(request)
        except web.HTTPException as ex:
            response = ex
    response.headers["Access-Control-Allow-Origin"] = "*"
    response.headers["Access-Control-Allow-Methods"] = "GET, POST, PUT, DELETE, OPTIONS"
    response.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type, Range"
    return response

routes = web.RouteTableDef()

# In-memory WebSocket connections: user_id -> set(web.WebSocketResponse)
connected_clients = {}

def get_current_user(request):
    auth_header = request.headers.get("Authorization", "")
    if not auth_header.startswith("Bearer "):
        return None
    user_id = auth_header.replace("Bearer ", "").strip()
    conn = get_connection()
    user = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    conn.close()
    return user

@routes.post("/api/auth/register")
async def register(request):
    data = await request.json()
    username = data.get("username", "").strip()
    password = data.get("password", "").strip()
    bio = data.get("bio", "").strip()

    if not username or len(username) < 3 or not password or len(password) < 4:
        return web.json_response({"error": "Invalid username or password length"}, status=400)

    user_id = f"user_{uuid.uuid4().hex[:12]}"
    pw_hash = hash_password(password)

    is_creator = 1 if username.lower() == "crispixar" else 0
    role = "owner" if is_creator else "user"
    custom_badge = "Создатель" if is_creator else ""
    can_edit = 1 if is_creator else 0
    can_manage = 1 if is_creator else 0

    conn = get_connection()
    try:
        conn.execute("""
            INSERT INTO users (id, username, password_hash, bio, role, is_creator, custom_badge, can_edit_calendar, can_manage_chat, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (user_id, username, pw_hash, bio, role, is_creator, custom_badge, can_edit, can_manage, time.time()))
        conn.commit()
    except Exception:
        conn.close()
        return web.json_response({"error": "Username already taken"}, status=409)

    user = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
    conn.close()

    return web.json_response({
        "token": user_id,
        "user": dict(user)
    })

@routes.post("/api/auth/login")
async def login(request):
    data = await request.json()
    username = data.get("username", "").strip()
    password = data.get("password", "").strip()

    pw_hash = hash_password(password)
    conn = get_connection()
    user = conn.execute("SELECT * FROM users WHERE username = ? AND password_hash = ?", (username, pw_hash)).fetchone()
    conn.close()

    if not user:
        return web.json_response({"error": "Invalid credentials"}, status=401)

    return web.json_response({
        "token": user["id"],
        "user": dict(user)
    })

@routes.get("/api/users")
async def list_users(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    conn = get_connection()
    users = conn.execute("SELECT id, username, bio, avatar_url, role, is_creator, custom_badge, can_edit_calendar, can_manage_chat FROM users").fetchall()
    conn.close()

    return web.json_response([dict(u) for u in users])

@routes.put("/api/users/profile")
async def update_profile(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    data = await request.json()
    bio = data.get("bio", user["bio"])
    avatar_url = data.get("avatar_url", user["avatar_url"])

    conn = get_connection()
    conn.execute("UPDATE users SET bio = ?, avatar_url = ? WHERE id = ?", (bio, avatar_url, user["id"]))
    conn.commit()
    updated = conn.execute("SELECT * FROM users WHERE id = ?", (user["id"],)).fetchone()
    conn.close()

    return web.json_response(dict(updated))

@routes.post("/api/admin/delegate")
async def delegate_permissions(request):
    user = get_current_user(request)
    if not user or not user["is_creator"]:
        return web.json_response({"error": "Forbidden: Owner access required"}, status=403)

    data = await request.json()
    target_user_id = data.get("user_id")
    custom_badge = data.get("custom_badge", "")
    can_edit_calendar = 1 if data.get("can_edit_calendar") else 0
    can_manage_chat = 1 if data.get("can_manage_chat") else 0

    conn = get_connection()
    conn.execute("""
        UPDATE users
        SET custom_badge = ?, can_edit_calendar = ?, can_manage_chat = ?
        WHERE id = ?
    """, (custom_badge, can_edit_calendar, can_manage_chat, target_user_id))
    conn.commit()
    updated = conn.execute("SELECT * FROM users WHERE id = ?", (target_user_id,)).fetchone()
    conn.close()

    return web.json_response(dict(updated))

@routes.post("/api/chats")
async def create_chat(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    data = await request.json()
    chat_type = data.get("type", "direct")
    target_user_id = data.get("target_user_id")

    if chat_type != "direct" and not user["is_creator"]:
        return web.json_response({"error": "Regular users are only allowed direct 1-on-1 chats"}, status=403)

    if chat_type == "direct" and not target_user_id:
        return web.json_response({"error": "Target user required for direct chat"}, status=400)

    chat_id = f"chat_{uuid.uuid4().hex[:12]}"
    title = data.get("title", "Диалог")

    conn = get_connection()
    conn.execute("""
        INSERT INTO chats (id, type, title, created_by, created_at)
        VALUES (?, ?, ?, ?, ?)
    """, (chat_id, chat_type, title, user["id"], time.time()))

    conn.execute("INSERT INTO chat_members (chat_id, user_id, joined_at) VALUES (?, ?, ?)", (chat_id, user["id"], time.time()))
    if chat_type == "direct":
        conn.execute("INSERT INTO chat_members (chat_id, user_id, joined_at) VALUES (?, ?, ?)", (chat_id, target_user_id, time.time()))

    conn.commit()
    conn.close()

    return web.json_response({"id": chat_id, "type": chat_type, "title": title})

@routes.get("/api/chats")
async def get_chats(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    conn = get_connection()
    chats = conn.execute("""
        SELECT c.*,
               (SELECT ciphertext FROM messages WHERE chat_id = c.id ORDER BY created_at DESC LIMIT 1) as last_message,
               (SELECT created_at FROM messages WHERE chat_id = c.id ORDER BY created_at DESC LIMIT 1) as last_time
        FROM chats c
        JOIN chat_members cm ON c.id = cm.chat_id
        WHERE cm.user_id = ?
        ORDER BY last_time DESC
    """, (user["id"],)).fetchall()
    conn.close()

    return web.json_response([dict(c) for c in chats])

@routes.get("/api/chats/{chat_id}/messages")
async def get_messages(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    chat_id = request.match_info["chat_id"]
    conn = get_connection()
    if not user["is_creator"]:
        member = conn.execute("SELECT 1 FROM chat_members WHERE chat_id = ? AND user_id = ?", (chat_id, user["id"])).fetchone()
        if not member:
            conn.close()
            return web.json_response({"error": "Forbidden"}, status=403)

    messages = conn.execute("""
        SELECT m.*, u.username as sender_username, u.custom_badge as sender_badge, u.is_creator as sender_is_creator
        FROM messages m
        JOIN users u ON m.sender_id = u.id
        WHERE m.chat_id = ?
        ORDER BY m.created_at ASC
    """, (chat_id,)).fetchall()
    conn.close()

    return web.json_response([dict(m) for m in messages])

@routes.post("/api/chats/{chat_id}/messages")
async def send_message(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    chat_id = request.match_info["chat_id"]
    data = await request.json()

    ciphertext = data.get("ciphertext", "")
    iv = data.get("iv", "")
    media_url = data.get("media_url")
    media_type = data.get("media_type")
    media_duration = data.get("media_duration", 0)

    msg_id = f"msg_{uuid.uuid4().hex[:12]}"
    created_at = time.time()

    conn = get_connection()
    conn.execute("""
        INSERT INTO messages (id, chat_id, sender_id, ciphertext, iv, media_url, media_type, media_duration, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (msg_id, chat_id, user["id"], ciphertext, iv, media_url, media_type, media_duration, created_at))
    conn.commit()
    conn.close()

    payload = {
        "event": "new_message",
        "message": {
            "id": msg_id,
            "chat_id": chat_id,
            "sender_id": user["id"],
            "sender_username": user["username"],
            "sender_badge": user["custom_badge"],
            "sender_is_creator": bool(user["is_creator"]),
            "ciphertext": ciphertext,
            "iv": iv,
            "media_url": media_url,
            "media_type": media_type,
            "created_at": created_at
        }
    }
    await broadcast_to_chat(chat_id, payload)

    return web.json_response(payload["message"])

@routes.delete("/api/messages/{message_id}")
async def delete_message(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    message_id = request.match_info["message_id"]
    conn = get_connection()
    msg = conn.execute("SELECT * FROM messages WHERE id = ?", (message_id,)).fetchone()
    if not msg:
        conn.close()
        return web.json_response({"error": "Message not found"}, status=404)

    if not user["is_creator"] and msg["sender_id"] != user["id"] and not user["can_manage_chat"]:
        conn.close()
        return web.json_response({"error": "Forbidden"}, status=403)

    conn.execute("DELETE FROM messages WHERE id = ?", (message_id,))
    conn.commit()
    conn.close()

    return web.json_response({"success": True, "deleted_id": message_id})

@routes.put("/api/messages/{message_id}")
async def edit_message(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    message_id = request.match_info["message_id"]
    data = await request.json()
    new_ciphertext = data.get("ciphertext", "")
    new_iv = data.get("iv", "")

    conn = get_connection()
    msg = conn.execute("SELECT * FROM messages WHERE id = ?", (message_id,)).fetchone()
    if not msg:
        conn.close()
        return web.json_response({"error": "Message not found"}, status=404)

    if not user["is_creator"] and msg["sender_id"] != user["id"] and not user["can_manage_chat"]:
        conn.close()
        return web.json_response({"error": "Forbidden"}, status=403)

    conn.execute("UPDATE messages SET ciphertext = ?, iv = ?, is_edited = 1 WHERE id = ?", (new_ciphertext, new_iv, message_id))
    conn.commit()
    conn.close()

    return web.json_response({"success": True, "id": message_id, "ciphertext": new_ciphertext, "is_edited": True})

@routes.post("/api/messages/{message_id}/pin")
async def pin_message(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    if not user["is_creator"] and not user["can_manage_chat"]:
        return web.json_response({"error": "Forbidden: Admin or Creator rights required to pin"}, status=403)

    message_id = request.match_info["message_id"]
    conn = get_connection()
    msg = conn.execute("SELECT * FROM messages WHERE id = ?", (message_id,)).fetchone()
    if not msg:
        conn.close()
        return web.json_response({"error": "Message not found"}, status=404)

    new_pin_status = 0 if msg["is_pinned"] else 1
    if new_pin_status == 1:
        conn.execute("UPDATE messages SET is_pinned = 0 WHERE chat_id = ?", (msg["chat_id"],))
    conn.execute("UPDATE messages SET is_pinned = ? WHERE id = ?", (new_pin_status, message_id))
    conn.execute("UPDATE chats SET pinned_message_id = ? WHERE id = ?", (message_id if new_pin_status == 1 else None, msg["chat_id"]))
    conn.commit()
    conn.close()

    return web.json_response({"success": True, "is_pinned": bool(new_pin_status)})

@routes.get("/api/calendar")
async def get_calendar(request):
    conn = get_connection()
    schedule = conn.execute("SELECT * FROM school_schedule ORDER BY day_of_week, lesson_number").fetchall()
    conn.close()
    return web.json_response([dict(s) for s in schedule])

@routes.put("/api/calendar/{lesson_id}")
async def update_calendar_lesson(request):
    user = get_current_user(request)
    if not user or (not user["is_creator"] and not user["can_edit_calendar"]):
        return web.json_response({"error": "Forbidden: Requires calendar editing permissions"}, status=403)

    lesson_id = request.match_info["lesson_id"]
    data = await request.json()
    homework = data.get("homework", "")

    conn = get_connection()
    conn.execute("""
        UPDATE school_schedule
        SET homework = ?, updated_by = ?, updated_at = ?
        WHERE id = ?
    """, (homework, user["username"], time.time(), lesson_id))
    conn.commit()
    updated = conn.execute("SELECT * FROM school_schedule WHERE id = ?", (lesson_id,)).fetchone()
    conn.close()

    return web.json_response(dict(updated))

@routes.post("/api/upload")
async def upload_media(request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)

    reader = await request.multipart()
    field = await reader.next()
    if not field:
        return web.json_response({"error": "No file uploaded"}, status=400)

    filename = field.filename or f"upload_{uuid.uuid4().hex}"
    ext = os.path.splitext(filename)[1].lower()
    raw_path = os.path.join(UPLOAD_DIR, f"raw_{uuid.uuid4().hex}{ext}")

    size = 0
    with open(raw_path, "wb") as f:
        while True:
            chunk = await field.read_chunk()
            if not chunk:
                break
            size += len(chunk)
            f.write(chunk)

    processed_name = f"{uuid.uuid4().hex}"
    media_type = "unknown"
    final_filename = ""
    thumbnail_url = None

    if ext in (".jpg", ".jpeg", ".png", ".webp"):
        media_type = "photo"
        final_filename = f"{processed_name}.jpg"
        final_path = os.path.join(UPLOAD_DIR, final_filename)
        compress_image(raw_path, final_path)
    elif ext in (".mp3", ".wav", ".m4a", ".ogg"):
        media_type = "audio"
        final_filename = f"{processed_name}.mp3"
        final_path = os.path.join(UPLOAD_DIR, final_filename)
        compress_audio(raw_path, final_path)
    elif ext in (".mp4", ".mov", ".mkv", ".webm"):
        media_type = "video"
        final_filename = f"{processed_name}.mp4"
        final_path = os.path.join(UPLOAD_DIR, final_filename)
        thumb_name = f"{processed_name}_thumb.jpg"
        thumb_path = os.path.join(THUMBNAILS_DIR, thumb_name)
        compress_video(raw_path, final_path, thumbnail_path=thumb_path)
        thumbnail_url = f"/uploads/thumbnails/{thumb_name}"
    else:
        final_filename = os.path.basename(raw_path)

    if os.path.exists(raw_path) and raw_path != os.path.join(UPLOAD_DIR, final_filename):
        try:
            os.remove(raw_path)
        except Exception:
            pass

    return web.json_response({
        "url": f"/uploads/{final_filename}",
        "thumbnail_url": thumbnail_url,
        "media_type": media_type
    })

@routes.get("/uploads/{filename}")
async def stream_media(request):
    filename = request.match_info["filename"]
    filepath = os.path.join(UPLOAD_DIR, filename)
    if not os.path.exists(filepath):
        return web.Response(status=404, text="Not Found")

    file_size = os.path.getsize(filepath)
    range_header = request.headers.get("Range")

    if range_header:
        ranges = range_header.replace("bytes=", "").split("-")
        start = int(ranges[0])
        end = int(ranges[1]) if ranges[1] else file_size - 1
        end = min(end, file_size - 1)
        length = end - start + 1

        with open(filepath, "rb") as f:
            f.seek(start)
            data = f.read(length)

        resp = web.Response(status=206, body=data)
        resp.headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"
        resp.headers["Accept-Ranges"] = "bytes"
        resp.headers["Content-Length"] = str(length)
        resp.headers["Content-Type"] = "video/mp4" if filename.endswith(".mp4") else "audio/mpeg"
        return resp
    else:
        return web.FileResponse(filepath)

@routes.get("/ws")
async def websocket_handler(request):
    ws = web.WebSocketResponse()
    await ws.prepare(request)

    user = get_current_user(request)
    user_id = user["id"] if user else f"anon_{uuid.uuid4().hex[:6]}"

    if user_id not in connected_clients:
        connected_clients[user_id] = set()
    connected_clients[user_id].add(ws)

    try:
        async for msg in ws:
            pass
    finally:
        connected_clients[user_id].discard(ws)
        if not connected_clients[user_id]:
            del connected_clients[user_id]

    return ws

async def broadcast_to_chat(chat_id: str, data: dict):
    conn = get_connection()
    members = conn.execute("SELECT user_id FROM chat_members WHERE chat_id = ?", (chat_id,)).fetchall()
    conn.close()

    text_data = json.dumps(data)
    for m in members:
        uid = m["user_id"]
        if uid in connected_clients:
            for ws in connected_clients[uid]:
                if not ws.closed:
                    try:
                        await ws.send_str(text_data)
                    except Exception:
                        pass

def create_app():
    init_db()
    app = web.Application(middlewares=[cors_middleware])
    app.add_routes(routes)
    return app

if __name__ == "__main__":
    app = create_app()
    web.run_app(app, host="0.0.0.0", port=8080)
