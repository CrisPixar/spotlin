"""
Spotlin Server: High-performance async server for school messenger Spotlin.
Features:
- JWT Authentication & Session management
- WebSocket real-time messaging with DoubleRatchet E2EE
- Media upload with compression (photos, music/audio, video)
- Media streaming with HTTP 206 Partial Content
- Role & Permission system (Hardcoded Owner/Creator, Delegated Badges)
- Strict chat rules: regular users can only create 1-on-1 chats
- Owner absolute privileges (pin, edit, delete any message)
- School calendar API with role-based write access
"""

import os
import sys
import json
import time
import uuid
import hashlib
import aiohttp
from aiohttp import web
import jwt
from typing import Dict, Set, Optional, Any

from database import Database
from media_processor import MediaProcessor

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")
UPLOAD_DIR = os.path.join(BASE_DIR, "uploads")
THUMB_DIR = os.path.join(UPLOAD_DIR, "thumbnails")
DB_PATH = os.path.join(DATA_DIR, "spotlin.db")

JWT_SECRET = os.environ.get("JWT_SECRET", "spotlin_school_jwt_secret_key_2026_very_secure")
SALT = "spotlin_salt_2026"

db = Database(DB_PATH)
media_proc = MediaProcessor(UPLOAD_DIR, THUMB_DIR)

# Connected WebSocket clients: {user_id: set(web.WebSocketResponse)}
connected_sockets: Dict[str, Set[web.WebSocketResponse]] = {}

def hash_password(password: str) -> str:
    return hashlib.sha256((password + SALT).encode()).hexdigest()

def create_tokens(user_id: str, username: str, is_creator: bool, role: str) -> Dict[str, str]:
    now = int(time.time())
    access_token = jwt.encode({
        "sub": user_id,
        "username": username,
        "is_creator": is_creator,
        "role": role,
        "exp": now + 86400 * 7 # 7 days
    }, JWT_SECRET, algorithm="HS256")
    
    refresh_token = jwt.encode({
        "sub": user_id,
        "exp": now + 86400 * 30 # 30 days
    }, JWT_SECRET, algorithm="HS256")
    
    return {"accessToken": access_token, "refreshToken": refresh_token}

def decode_token(token: str) -> Optional[Dict]:
    try:
        return jwt.decode(token, JWT_SECRET, algorithms=["HS256"])
    except Exception:
        return None

def get_current_user(request: web.Request) -> Optional[Dict]:
    auth = request.headers.get("Authorization", "")
    if not auth.startswith("Bearer "):
        return None
    token = auth[7:].strip()
    payload = decode_token(token)
    if not payload:
        return None
    return db.get_user_by_id(payload.get("sub"))

# ==================== HTTP ROUTES ====================

routes = web.RouteTableDef()

@routes.get("/")
async def root(request: web.Request):
    return web.json_response({
        "app": "Spotlin Server",
        "name": "Spotlin School Messenger",
        "version": "2.0.0",
        "status": "online"
    })

# Auth
@routes.post("/api/auth/register")
async def register(request: web.Request):
    data = await request.json()
    username = data.get("username", "").strip()
    password = data.get("password", "").strip()
    if not username or len(username) < 3 or not password or len(password) < 4:
        return web.json_response({"error": "Логин от 3 символов, пароль от 4 символов"}, status=400)
    
    if db.get_user_by_username(username):
        return web.json_response({"error": "Пользователь с таким именем уже существует"}, status=409)
    
    pwd_hash = hash_password(password)
    user = db.create_user(username, pwd_hash)
    tokens = create_tokens(user["id"], user["username"], bool(user["is_creator"]), user["role"])
    
    return web.json_response({
        "user": {
            "id": user["id"],
            "username": user["username"],
            "bio": user["bio"],
            "avatarUrl": user["avatar_url"],
            "role": user["role"],
            "isCreator": bool(user["is_creator"]),
            "customBadge": user["custom_badge"],
            "canEditCalendar": bool(user["can_edit_calendar"]),
            "canManageChat": bool(user["can_manage_chat"]),
            "isVerified": bool(user["is_verified"])
        },
        **tokens
    })

@routes.post("/api/auth/login")
async def login(request: web.Request):
    data = await request.json()
    username = data.get("username", "").strip()
    password = data.get("password", "").strip()
    
    user = db.get_user_by_username(username)
    if not user or user["password_hash"] != hash_password(password):
        return web.json_response({"error": "Неверное имя пользователя или пароль"}, status=401)
    
    tokens = create_tokens(user["id"], user["username"], bool(user["is_creator"]), user["role"])
    return web.json_response({
        "user": {
            "id": user["id"],
            "username": user["username"],
            "bio": user["bio"],
            "avatarUrl": user["avatar_url"],
            "role": user["role"],
            "isCreator": bool(user["is_creator"]),
            "customBadge": user["custom_badge"],
            "canEditCalendar": bool(user["can_edit_calendar"]),
            "canManageChat": bool(user["can_manage_chat"]),
            "isVerified": bool(user["is_verified"])
        },
        **tokens
    })

@routes.post("/api/auth/refresh")
async def refresh(request: web.Request):
    data = await request.json()
    ref_token = data.get("refreshToken", "")
    payload = decode_token(ref_token)
    if not payload:
        return web.json_response({"error": "Invalid refresh token"}, status=401)
    
    user = db.get_user_by_id(payload.get("sub"))
    if not user:
        return web.json_response({"error": "User not found"}, status=401)
    
    tokens = create_tokens(user["id"], user["username"], bool(user["is_creator"]), user["role"])
    return web.json_response(tokens)

# Users & Profiles
@routes.get("/api/users/profile")
async def get_profile(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    return web.json_response({
        "id": user["id"],
        "username": user["username"],
        "bio": user["bio"],
        "avatarUrl": user["avatar_url"],
        "role": user["role"],
        "isCreator": bool(user["is_creator"]),
        "customBadge": user["custom_badge"],
        "canEditCalendar": bool(user["can_edit_calendar"]),
        "canManageChat": bool(user["can_manage_chat"]),
        "isVerified": bool(user["is_verified"])
    })

@routes.put("/api/users/profile")
async def update_profile(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    data = await request.json()
    bio = data.get("bio")
    avatar_url = data.get("avatarUrl")
    db.update_profile(user["id"], bio=bio, avatar_url=avatar_url)
    updated = db.get_user_by_id(user["id"])
    return web.json_response(updated)

@routes.get("/api/users/search")
async def search_users(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    q = request.query.get("q", "")
    users = db.search_users(q)
    return web.json_response(users)

# Chats
@routes.get("/api/chats")
async def get_chats(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    chats = db.get_user_chats(user["id"])
    return web.json_response(chats)

@routes.post("/api/chats")
async def create_chat(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    data = await request.json()
    chat_type = data.get("type", "direct") # "direct" or "group"
    member_ids = data.get("memberIds", [])
    title = data.get("title", "").strip()
    
    # RULE ENFORCEMENT:
    # "обычные пользователи могут переписываться только тет-а-тет и лишены прав на создание групп"
    if chat_type == "group":
        if not (user["is_creator"] or user["role"] == "admin"):
            return web.json_response({
                "error": "Обычные пользователи могут переписываться только тет-а-тет и не могут создавать группы."
            }, status=403)
    
    if chat_type == "direct":
        if len(member_ids) != 1:
            return web.json_response({"error": "Прямой чат должен содержать ровно одного собеседника"}, status=400)
    
    chat = db.create_chat(chat_type, user["id"], member_ids, title=title)
    return web.json_response(chat)

@routes.get("/api/chats/{chat_id}")
async def get_chat(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    chat_id = request.match_info["chat_id"]
    chat = db.get_chat(chat_id)
    if not chat:
        return web.json_response({"error": "Chat not found"}, status=404)
    return web.json_response(chat)

@routes.get("/api/chats/{chat_id}/messages")
async def get_messages(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    chat_id = request.match_info["chat_id"]
    limit = int(request.query.get("limit", 50))
    before = request.query.get("before")
    before_ts = int(before) if before else None
    
    messages = db.get_messages(chat_id, limit=limit, before=before_ts)
    return web.json_response(messages)

# Messages & Absolute Owner Rights
@routes.post("/api/chats/{chat_id}/pin/{message_id}")
async def pin_message(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    chat_id = request.match_info["chat_id"]
    message_id = request.match_info["message_id"]
    
    # Owner has absolute rights, or user with can_manage_chat
    if not (user["is_creator"] or user["can_manage_chat"] or user["role"] == "admin"):
        return web.json_response({"error": "Недостаточно прав для закрепления сообщений"}, status=403)
    
    db.pin_message(chat_id, message_id)
    # Broadcast to all chat members
    await broadcast_to_chat(chat_id, {
        "type": "message_pinned",
        "chatId": chat_id,
        "messageId": message_id
    })
    return web.json_response({"status": "pinned", "chatId": chat_id, "messageId": message_id})

@routes.delete("/api/chats/{chat_id}/messages/{message_id}")
async def delete_message(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    chat_id = request.match_info["chat_id"]
    message_id = request.match_info["message_id"]
    
    # Check permission: Creator/Owner has ABSOLUTE right in all chats!
    is_owner = bool(user["is_creator"])
    is_admin = (user["role"] == "admin") or bool(user["can_manage_chat"])
    
    # If not owner/admin, verify if caller is sender
    if not (is_owner or is_admin):
        # Must be sender
        with db.get_conn() as conn:
            m = conn.execute("SELECT sender_id FROM messages WHERE id = ?", (message_id,)).fetchone()
            if not m or m["sender_id"] != user["id"]:
                return web.json_response({"error": "Недостаточно прав для удаления сообщения"}, status=403)
    
    db.delete_message(message_id)
    await broadcast_to_chat(chat_id, {
        "type": "message_deleted",
        "chatId": chat_id,
        "messageId": message_id
    })
    return web.json_response({"status": "deleted", "messageId": message_id})

@routes.put("/api/chats/{chat_id}/messages/{message_id}")
async def edit_message(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    chat_id = request.match_info["chat_id"]
    message_id = request.match_info["message_id"]
    data = await request.json()
    new_payload = data.get("encryptedPayload", "")
    
    is_owner = bool(user["is_creator"])
    is_admin = (user["role"] == "admin") or bool(user["can_manage_chat"])
    
    if not (is_owner or is_admin):
        with db.get_conn() as conn:
            m = conn.execute("SELECT sender_id FROM messages WHERE id = ?", (message_id,)).fetchone()
            if not m or m["sender_id"] != user["id"]:
                return web.json_response({"error": "Недостаточно прав для редактирования сообщения"}, status=403)
    
    db.edit_message(message_id, new_payload)
    await broadcast_to_chat(chat_id, {
        "type": "message_edited",
        "chatId": chat_id,
        "messageId": message_id,
        "newPayload": new_payload
    })
    return web.json_response({"status": "edited", "messageId": message_id})

# School Calendar
@routes.get("/api/calendar/{chat_id}")
async def get_calendar(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    chat_id = request.match_info["chat_id"]
    cal = db.get_calendar(chat_id)
    if not cal:
        # Default calendar if none exists yet
        init_schedule = {
            "Понедельник": ["Алгебра", "Русский язык", "Литература", "История", "Физика"],
            "Вторник": ["Геометрия", "Английский язык", "Информатика", "Химия", "Биология"],
            "Среда": ["Алгебра", "Обществознание", "География", "Физкультура", "Физика"],
            "Четверг": ["Русский язык", "Литература", "Английский язык", "История", "ОБЖ"],
            "Пятница": ["Информатика", "Химия", "Геометрия", "Биология", "Физкультура"]
        }
        init_hw = {
            "Алгебра": "№ 142, 145 (стр. 67)",
            "Физика": "Параграф 12, вопросы 1-5",
            "Литература": "Читать главы 3-4",
            "Информатика": "Практикум в среде разработки"
        }
        cal = db.update_calendar(chat_id, init_schedule, init_hw, user["id"])
    
    return web.json_response(cal)

@routes.put("/api/calendar/{chat_id}")
async def update_calendar(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    # Permission check:
    # "изменять эти данные могут только администраторы или пользователи с выданными правами"
    can_edit = bool(user["is_creator"]) or (user["role"] == "admin") or bool(user["can_edit_calendar"])
    if not can_edit:
        return web.json_response({
            "error": "Редактирование расписания и домашних заданий разрешено только администраторам и уполномоченным лицам."
        }, status=403)
    
    chat_id = request.match_info["chat_id"]
    data = await request.json()
    schedule = data.get("schedule", {})
    homework = data.get("homework", {})
    
    cal = db.update_calendar(chat_id, schedule, homework, user["id"])
    await broadcast_to_chat(chat_id, {
        "type": "calendar_updated",
        "chatId": chat_id,
        "calendar": cal
    })
    return web.json_response(cal)

# Delegation & Admin Panel
@routes.post("/api/admin/delegate")
async def delegate_rights(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    # ONLY owner/creator can delegate custom rights
    if not user["is_creator"]:
        return web.json_response({"error": "Только создатель имеет доступ к делегированию прав"}, status=403)
    
    data = await request.json()
    target_user_id = data.get("userId")
    custom_badge = data.get("customBadge", "")
    can_calendar = data.get("canEditCalendar", False)
    can_manage = data.get("canManageChat", False)
    is_verified = data.get("isVerified", False)
    
    db.delegate_rights(target_user_id, custom_badge, can_calendar, can_manage, is_verified)
    updated_user = db.get_user_by_id(target_user_id)
    return web.json_response({"status": "delegated", "user": updated_user})

@routes.get("/api/admin/users")
async def get_all_users(request: web.Request):
    user = get_current_user(request)
    if not user or not user["is_creator"]:
        return web.json_response({"error": "Unauthorized"}, status=403)
    
    with db.get_conn() as conn:
        rows = conn.execute("SELECT id, username, bio, avatar_url, role, is_creator, custom_badge, can_edit_calendar, can_manage_chat, is_verified, created_at FROM users").fetchall()
        return web.json_response([dict(r) for r in rows])

# Media Upload & Compression
@routes.post("/api/media/upload")
async def upload_media(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    reader = await request.multipart()
    field = await reader.next()
    if not field or field.name != "file":
        return web.json_response({"error": "File field missing"}, status=400)
    
    filename = field.filename or f"upload_{uuid.uuid4().hex}"
    ext = os.path.splitext(filename)[1].lower()
    file_id = f"{uuid.uuid4().hex}{ext}"
    temp_path = os.path.join(UPLOAD_DIR, f"temp_{file_id}")
    final_path = os.path.join(UPLOAD_DIR, file_id)
    
    with open(temp_path, "wb") as f:
        while True:
            chunk = await field.read_chunk()
            if not chunk:
                break
            f.write(chunk)
    
    # Process & Compress based on type
    media_type = "file"
    thumb_url = ""
    duration = 0.0
    
    if ext in (".jpg", ".jpeg", ".png", ".webp", ".bmp", ".gif"):
        media_type = "photo"
        out_jpg = os.path.join(UPLOAD_DIR, f"{uuid.uuid4().hex}.jpg")
        media_proc.compress_image(temp_path, out_jpg)
        if os.path.exists(temp_path):
            os.remove(temp_path)
        final_filename = os.path.basename(out_jpg)
        thumb_name = final_filename
        thumb_url = f"/api/media/thumb/{thumb_name}"
    elif ext in (".mp3", ".wav", ".ogg", ".m4a", ".aac", ".flac"):
        media_type = "audio"
        out_audio = os.path.join(UPLOAD_DIR, f"{uuid.uuid4().hex}.mp3")
        _, dur = media_proc.compress_audio(temp_path, out_audio)
        if os.path.exists(temp_path):
            os.remove(temp_path)
        final_filename = os.path.basename(out_audio)
        duration = dur or 0.0
    elif ext in (".mp4", ".mov", ".mkv", ".avi", ".webm"):
        media_type = "video"
        out_video = os.path.join(UPLOAD_DIR, f"{uuid.uuid4().hex}.mp4")
        _, dur, thumb_p = media_proc.compress_video(temp_path, out_video)
        if os.path.exists(temp_path):
            os.remove(temp_path)
        final_filename = os.path.basename(out_video)
        duration = dur or 0.0
        if thumb_p and os.path.exists(thumb_p):
            thumb_url = f"/api/media/thumb/{os.path.basename(thumb_p)}"
    else:
        os.rename(temp_path, final_path)
        final_filename = file_id

    return web.json_response({
        "mediaType": media_type,
        "mediaUrl": f"/api/media/{final_filename}",
        "thumbnailUrl": thumb_url,
        "duration": duration,
        "originalName": filename
    })

# Streaming Media Endpoint (supports HTTP 206 for video and audio playback)
@routes.get("/api/media/{filename}")
async def serve_media(request: web.Request):
    filename = request.match_info["filename"]
    file_path = os.path.join(UPLOAD_DIR, filename)
    if not os.path.exists(file_path):
        return web.json_response({"error": "Media not found"}, status=404)
    
    file_size = os.path.getsize(file_path)
    range_header = request.headers.get("Range")
    
    # Determine Content-Type
    ext = os.path.splitext(filename)[1].lower()
    content_types = {
        ".mp4": "video/mp4",
        ".webm": "video/webm",
        ".mp3": "audio/mpeg",
        ".m4a": "audio/mp4",
        ".aac": "audio/aac",
        ".jpg": "image/jpeg",
        ".jpeg": "image/jpeg",
        ".png": "image/png",
        ".webp": "image/webp"
    }
    content_type = content_types.get(ext, "application/octet-stream")
    
    if range_header:
        # e.g. "bytes=0-1024"
        ranges = range_header.replace("bytes=", "").split("-")
        start = int(ranges[0])
        end = int(ranges[1]) if ranges[1] else file_size - 1
        length = (end - start) + 1
        
        response = web.StreamResponse(status=206, reason="Partial Content")
        response.headers["Content-Type"] = content_type
        response.headers["Accept-Ranges"] = "bytes"
        response.headers["Content-Range"] = f"bytes {start}-{end}/{file_size}"
        response.headers["Content-Length"] = str(length)
        await response.prepare(request)
        
        with open(file_path, "rb") as f:
            f.seek(start)
            bytes_left = length
            while bytes_left > 0:
                chunk = f.read(min(bytes_left, 65536))
                if not chunk:
                    break
                await response.write(chunk)
                bytes_left -= len(chunk)
        await response.write_eof()
        return response
    else:
        return web.FileResponse(file_path, headers={"Accept-Ranges": "bytes", "Content-Type": content_type})

@routes.get("/api/media/thumb/{filename}")
async def serve_thumbnail(request: web.Request):
    filename = request.match_info["filename"]
    thumb_path = os.path.join(THUMB_DIR, filename)
    if not os.path.exists(thumb_path):
        return web.json_response({"error": "Thumbnail not found"}, status=404)
    return web.FileResponse(thumb_path)

# Keys for E2EE
@routes.get("/api/keys/bundle/{user_id}")
async def get_key_bundle(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    user_id = request.match_info["user_id"]
    bundle = db.get_key_bundle(user_id)
    if not bundle:
        return web.json_response({"error": "Keys not found"}, status=404)
    return web.json_response(bundle)

@routes.post("/api/keys/upload")
async def upload_keys(request: web.Request):
    user = get_current_user(request)
    if not user:
        return web.json_response({"error": "Unauthorized"}, status=401)
    
    data = await request.json()
    identity_key = data.get("identityKey", "")
    signed_prekey = data.get("signedPrekey", "")
    sig = data.get("prekeySignature", "")
    one_time = data.get("oneTimePrekeys", [])
    
    db.set_keys(user["id"], identity_key, signed_prekey, sig, one_time)
    return web.json_response({"status": "keys_saved"})

# ==================== WEBSOCKET ====================

@routes.get("/ws")
async def websocket_handler(request: web.Request):
    ws = web.WebSocketResponse(heartbeat=30.0)
    await ws.prepare(request)
    
    current_uid = None
    try:
        async for msg in ws:
            if msg.type == aiohttp.WSMsgType.TEXT:
                data = json.loads(msg.data)
                mtype = data.get("type")
                
                if mtype == "auth":
                    token = data.get("token")
                    payload = decode_token(token)
                    if not payload:
                        await ws.send_json({"type": "error", "message": "Invalid token"})
                        await ws.close(code=4001)
                        break
                    current_uid = payload.get("sub")
                    if current_uid not in connected_sockets:
                        connected_sockets[current_uid] = set()
                    connected_sockets[current_uid].add(ws)
                    await ws.send_json({"type": "auth_ok", "userId": current_uid})
                
                elif mtype == "message":
                    if not current_uid:
                        continue
                    chat_id = data.get("chatId")
                    payload = data.get("encryptedPayload", "")
                    ratchet_hdr = data.get("ratchetHeader", "")
                    sig = data.get("signature", "")
                    media_url = data.get("mediaUrl", "")
                    media_type = data.get("mediaType", "")
                    media_thumb = data.get("mediaThumbnail", "")
                    media_dur = float(data.get("mediaDuration", 0.0))
                    
                    saved = db.add_message(
                        chat_id=chat_id,
                        sender_id=current_uid,
                        encrypted_payload=payload,
                        ratchet_header=ratchet_hdr,
                        signature=sig,
                        media_url=media_url,
                        media_type=media_type,
                        media_thumbnail=media_thumb,
                        media_duration=media_dur
                    )
                    
                    sender_user = db.get_user_by_id(current_uid)
                    broadcast_payload = {
                        "type": "message",
                        **saved,
                        "sender_username": sender_user["username"],
                        "sender_badge": sender_user["custom_badge"],
                        "sender_is_creator": sender_user["is_creator"]
                    }
                    await broadcast_to_chat(chat_id, broadcast_payload)
                
                elif mtype == "typing":
                    chat_id = data.get("chatId")
                    if chat_id and current_uid:
                        await broadcast_to_chat(chat_id, {
                            "type": "typing",
                            "chatId": chat_id,
                            "userId": current_uid
                        }, exclude_user=current_uid)
                
                elif mtype == "ping":
                    await ws.send_json({"type": "pong"})
    finally:
        if current_uid and current_uid in connected_sockets:
            connected_sockets[current_uid].discard(ws)
            if not connected_sockets[current_uid]:
                del connected_sockets[current_uid]
    
    return ws

async def broadcast_to_chat(chat_id: str, data: Dict, exclude_user: Optional[str] = None):
    chat = db.get_chat(chat_id)
    if not chat:
        return
    for member in chat.get("members", []):
        uid = member["id"]
        if uid == exclude_user:
            continue
        if uid in connected_sockets:
            for s in list(connected_sockets[uid]):
                try:
                    await s.send_json(data)
                except Exception:
                    pass

def create_app() -> web.Application:
    app = web.Application(client_max_size=1024 * 1024 * 100) # 100 MB max file upload
    app.add_routes(routes)
    return app

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 3000))
    app = create_app()
    web.run_app(app, host="0.0.0.0", port=port)
