"""
Spotlin Database Layer (SQLite with WAL mode)
Thread-safe, robust, production schema with auto-closing connections.
"""

import sqlite3
import os
import json
import time
import hashlib
import uuid
from typing import Dict, List, Optional, Any

class Database:
    def __init__(self, db_path: str):
        self.db_path = db_path
        os.makedirs(os.path.dirname(db_path), exist_ok=True)
        self.init_schema()

    def _open(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self.db_path)
        conn.row_factory = sqlite3.Row
        conn.execute("PRAGMA journal_mode=WAL;")
        conn.execute("PRAGMA foreign_keys=ON;")
        return conn

    def init_schema(self):
        conn = self._open()
        try:
            conn.executescript("""
            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                username TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                bio TEXT DEFAULT '',
                avatar_url TEXT DEFAULT '',
                role TEXT DEFAULT 'user',
                is_creator INTEGER DEFAULT 0,
                custom_badge TEXT DEFAULT '',
                can_edit_calendar INTEGER DEFAULT 0,
                can_manage_chat INTEGER DEFAULT 0,
                is_verified INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                refresh_token TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                expires_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS chats (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL, -- 'direct' or 'group'
                title TEXT DEFAULT '',
                creator_id TEXT NOT NULL,
                pinned_message_id TEXT DEFAULT NULL,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS chat_members (
                chat_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                role TEXT DEFAULT 'member',
                PRIMARY KEY (chat_id, user_id),
                FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                chat_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                encrypted_payload TEXT NOT NULL,
                ratchet_header TEXT DEFAULT '',
                signature TEXT DEFAULT '',
                media_url TEXT DEFAULT '',
                media_type TEXT DEFAULT '',
                media_thumbnail TEXT DEFAULT '',
                media_duration REAL DEFAULT 0,
                is_edited INTEGER DEFAULT 0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS calendar (
                chat_id TEXT PRIMARY KEY,
                schedule_json TEXT NOT NULL DEFAULT '{}',
                homework_json TEXT NOT NULL DEFAULT '{}',
                updated_at INTEGER NOT NULL,
                updated_by TEXT NOT NULL,
                FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE
            );

            CREATE TABLE IF NOT EXISTS crypto_keys (
                user_id TEXT PRIMARY KEY,
                identity_key TEXT DEFAULT '',
                signed_prekey TEXT DEFAULT '',
                prekey_signature TEXT DEFAULT '',
                one_time_prekeys TEXT DEFAULT '[]',
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
            """)

            owner = conn.execute("SELECT * FROM users WHERE is_creator = 1").fetchone()
            if not owner:
                salt = "spotlin_salt_2026"
                pwd_hash = hashlib.sha256(("Admin2026!#" + salt).encode()).hexdigest()
                owner_id = "user_creator_01"
                now = int(time.time() * 1000)
                conn.execute("""
                INSERT OR IGNORE INTO users (id, username, password_hash, bio, role, is_creator, custom_badge, can_edit_calendar, can_manage_chat, is_verified, created_at)
                VALUES (?, ?, ?, ?, ?, 1, ?, 1, 1, 1, ?)
                """, (owner_id, "CrisPixar", pwd_hash, "Создатель мессенджера Spotlin", "owner", "Создатель", now))
                conn.commit()
        finally:
            conn.close()

    def create_user(self, username: str, password_hash: str) -> Dict[str, Any]:
        user_id = str(uuid.uuid4())
        now = int(time.time() * 1000)
        is_creator = 1 if username.lower() == "crispixar" else 0
        role = "owner" if is_creator else "user"
        badge = "Создатель" if is_creator else ""
        can_calendar = 1 if is_creator else 0
        can_manage = 1 if is_creator else 0
        
        conn = self._open()
        try:
            conn.execute("""
            INSERT INTO users (id, username, password_hash, role, is_creator, custom_badge, can_edit_calendar, can_manage_chat, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (user_id, username, password_hash, role, is_creator, badge, can_calendar, can_manage, now))
            conn.commit()
        finally:
            conn.close()
        return self.get_user_by_id(user_id)

    def get_user_by_id(self, user_id: str) -> Optional[Dict[str, Any]]:
        conn = self._open()
        try:
            row = conn.execute("SELECT * FROM users WHERE id = ?", (user_id,)).fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def get_user_by_username(self, username: str) -> Optional[Dict[str, Any]]:
        conn = self._open()
        try:
            row = conn.execute("SELECT * FROM users WHERE username = ?", (username,)).fetchone()
            return dict(row) if row else None
        finally:
            conn.close()

    def update_profile(self, user_id: str, bio: Optional[str] = None, avatar_url: Optional[str] = None):
        conn = self._open()
        try:
            if bio is not None and avatar_url is not None:
                conn.execute("UPDATE users SET bio = ?, avatar_url = ? WHERE id = ?", (bio, avatar_url, user_id))
            elif bio is not None:
                conn.execute("UPDATE users SET bio = ? WHERE id = ?", (bio, user_id))
            elif avatar_url is not None:
                conn.execute("UPDATE users SET avatar_url = ? WHERE id = ?", (avatar_url, user_id))
            conn.commit()
        finally:
            conn.close()

    def search_users(self, query: str) -> List[Dict[str, Any]]:
        conn = self._open()
        try:
            rows = conn.execute("""
            SELECT id, username, bio, avatar_url, role, is_creator, custom_badge, is_verified 
            FROM users WHERE username LIKE ? LIMIT 20
            """, (f"%{query}%",)).fetchall()
            return [dict(r) for r in rows]
        finally:
            conn.close()

    def delegate_rights(self, target_user_id: str, badge: str, can_calendar: bool, can_manage: bool, is_verified: bool):
        conn = self._open()
        try:
            conn.execute("""
            UPDATE users SET custom_badge = ?, can_edit_calendar = ?, can_manage_chat = ?, is_verified = ?
            WHERE id = ? AND is_creator = 0
            """, (badge, 1 if can_calendar else 0, 1 if can_manage else 0, 1 if is_verified else 0, target_user_id))
            conn.commit()
        finally:
            conn.close()

    def create_chat(self, chat_type: str, creator_id: str, member_ids: List[str], title: str = "") -> Dict[str, Any]:
        chat_id = str(uuid.uuid4())
        now = int(time.time() * 1000)
        conn = self._open()
        try:
            conn.execute("""
            INSERT INTO chats (id, type, title, creator_id, created_at)
            VALUES (?, ?, ?, ?, ?)
            """, (chat_id, chat_type, title, creator_id, now))
            
            all_members = set(member_ids + [creator_id])
            for m in all_members:
                role = "creator" if m == creator_id else "member"
                conn.execute("INSERT INTO chat_members (chat_id, user_id, role) VALUES (?, ?, ?)", (chat_id, m, role))
            
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
            conn.execute("""
            INSERT OR IGNORE INTO calendar (chat_id, schedule_json, homework_json, updated_at, updated_by)
            VALUES (?, ?, ?, ?, ?)
            """, (chat_id, json.dumps(init_schedule, ensure_ascii=False), json.dumps(init_hw, ensure_ascii=False), now, creator_id))
            conn.commit()
        finally:
            conn.close()
        return self.get_chat(chat_id)

    def get_chat(self, chat_id: str) -> Optional[Dict[str, Any]]:
        conn = self._open()
        try:
            c = conn.execute("SELECT * FROM chats WHERE id = ?", (chat_id,)).fetchone()
            if not c:
                return None
            res = dict(c)
            members = conn.execute("""
            SELECT u.id, u.username, u.avatar_url, u.bio, u.custom_badge, u.is_creator, cm.role
            FROM chat_members cm
            JOIN users u ON cm.user_id = u.id
            WHERE cm.chat_id = ?
            """, (chat_id,)).fetchall()
            res["members"] = [dict(m) for m in members]
            return res
        finally:
            conn.close()

    def get_user_chats(self, user_id: str) -> List[Dict[str, Any]]:
        conn = self._open()
        try:
            rows = conn.execute("""
            SELECT c.* FROM chats c
            JOIN chat_members cm ON c.id = cm.chat_id
            WHERE cm.user_id = ?
            ORDER BY c.created_at DESC
            """, (user_id,)).fetchall()
            chats = []
            for r in rows:
                cd = dict(r)
                members = conn.execute("""
                SELECT u.id, u.username, u.avatar_url, u.custom_badge, u.is_creator, cm.role
                FROM chat_members cm
                JOIN users u ON cm.user_id = u.id
                WHERE cm.chat_id = ?
                """, (cd["id"],)).fetchall()
                cd["members"] = [dict(m) for m in members]
                chats.append(cd)
            return chats
        finally:
            conn.close()

    def add_message(self, chat_id: str, sender_id: str, encrypted_payload: str, ratchet_header: str = "",
                    signature: str = "", media_url: str = "", media_type: str = "",
                    media_thumbnail: str = "", media_duration: float = 0.0) -> Dict[str, Any]:
        msg_id = str(uuid.uuid4())
        now = int(time.time() * 1000)
        conn = self._open()
        try:
            conn.execute("""
            INSERT INTO messages (id, chat_id, sender_id, encrypted_payload, ratchet_header, signature,
                                 media_url, media_type, media_thumbnail, media_duration, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """, (msg_id, chat_id, sender_id, encrypted_payload, ratchet_header, signature,
                  media_url, media_type, media_thumbnail, media_duration, now))
            conn.commit()
            return {
                "id": msg_id,
                "chat_id": chat_id,
                "sender_id": sender_id,
                "encrypted_payload": encrypted_payload,
                "ratchet_header": ratchet_header,
                "signature": signature,
                "media_url": media_url,
                "media_type": media_type,
                "media_thumbnail": media_thumbnail,
                "media_duration": media_duration,
                "is_edited": 0,
                "created_at": now
            }
        finally:
            conn.close()

    def get_messages(self, chat_id: str, limit: int = 50, before: Optional[int] = None) -> List[Dict[str, Any]]:
        conn = self._open()
        try:
            if before:
                rows = conn.execute("""
                SELECT m.*, u.username as sender_username, u.custom_badge as sender_badge, u.is_creator as sender_is_creator
                FROM messages m
                JOIN users u ON m.sender_id = u.id
                WHERE m.chat_id = ? AND m.created_at < ?
                ORDER BY m.created_at ASC LIMIT ?
                """, (chat_id, before, limit)).fetchall()
            else:
                rows = conn.execute("""
                SELECT m.*, u.username as sender_username, u.custom_badge as sender_badge, u.is_creator as sender_is_creator
                FROM messages m
                JOIN users u ON m.sender_id = u.id
                WHERE m.chat_id = ?
                ORDER BY m.created_at ASC LIMIT ?
                """, (chat_id, limit)).fetchall()
            return [dict(r) for r in rows]
        finally:
            conn.close()

    def pin_message(self, chat_id: str, message_id: Optional[str]):
        conn = self._open()
        try:
            conn.execute("UPDATE chats SET pinned_message_id = ? WHERE id = ?", (message_id, chat_id))
            conn.commit()
        finally:
            conn.close()

    def edit_message(self, message_id: str, new_payload: str):
        conn = self._open()
        try:
            conn.execute("UPDATE messages SET encrypted_payload = ?, is_edited = 1 WHERE id = ?", (new_payload, message_id))
            conn.commit()
        finally:
            conn.close()

    def delete_message(self, message_id: str):
        conn = self._open()
        try:
            conn.execute("DELETE FROM messages WHERE id = ?", (message_id,))
            conn.commit()
        finally:
            conn.close()

    def get_calendar(self, chat_id: str) -> Optional[Dict[str, Any]]:
        conn = self._open()
        try:
            row = conn.execute("SELECT * FROM calendar WHERE chat_id = ?", (chat_id,)).fetchone()
            if row:
                d = dict(row)
                d["schedule"] = json.loads(d["schedule_json"])
                d["homework"] = json.loads(d["homework_json"])
                return d
            return None
        finally:
            conn.close()

    def update_calendar(self, chat_id: str, schedule: Dict, homework: Dict, updated_by: str) -> Dict[str, Any]:
        now = int(time.time() * 1000)
        conn = self._open()
        try:
            conn.execute("""
            INSERT INTO calendar (chat_id, schedule_json, homework_json, updated_at, updated_by)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(chat_id) DO UPDATE SET
                schedule_json = excluded.schedule_json,
                homework_json = excluded.homework_json,
                updated_at = excluded.updated_at,
                updated_by = excluded.updated_by
            """, (chat_id, json.dumps(schedule, ensure_ascii=False), json.dumps(homework, ensure_ascii=False), now, updated_by))
            conn.commit()
        finally:
            conn.close()
        return self.get_calendar(chat_id)

    def set_keys(self, user_id: str, identity_key: str, signed_prekey: str, signature: str, one_time: List[str]):
        conn = self._open()
        try:
            conn.execute("""
            INSERT INTO crypto_keys (user_id, identity_key, signed_prekey, prekey_signature, one_time_prekeys)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT(user_id) DO UPDATE SET
                identity_key = excluded.identity_key,
                signed_prekey = excluded.signed_prekey,
                prekey_signature = excluded.prekey_signature,
                one_time_prekeys = excluded.one_time_prekeys
            """, (user_id, identity_key, signed_prekey, signature, json.dumps(one_time)))
            conn.commit()
        finally:
            conn.close()

    def get_key_bundle(self, user_id: str) -> Optional[Dict[str, Any]]:
        conn = self._open()
        try:
            row = conn.execute("SELECT * FROM crypto_keys WHERE user_id = ?", (user_id,)).fetchone()
            if not row:
                return None
            d = dict(row)
            ot = json.loads(d["one_time_prekeys"])
            prekey = ot.pop(0) if ot else None
            conn.execute("UPDATE crypto_keys SET one_time_prekeys = ? WHERE user_id = ?", (json.dumps(ot), user_id))
            conn.commit()
            return {
                "userId": user_id,
                "identityKey": d["identity_key"],
                "signedPrekey": d["signed_prekey"],
                "prekeySignature": d["prekey_signature"],
                "oneTimePrekey": prekey
            }
        finally:
            conn.close()
