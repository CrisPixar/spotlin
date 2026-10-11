import sqlite3
import os
import hashlib
import time

DB_PATH = os.path.join(os.path.dirname(__file__), "data", "spotlin.db")

def init_db(db_path=None):
    if db_path is None:
        db_path = DB_PATH
    os.makedirs(os.path.dirname(db_path), exist_ok=True)
    conn = sqlite3.connect(db_path)
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA foreign_keys=ON;")
    with conn:
        # Users
        conn.execute("""
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
                created_at REAL NOT NULL
            );
        """)

        # Chats (Direct chats only for regular users; group allowed only for creator)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS chats (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL, -- 'direct' or 'group'
                title TEXT NOT NULL,
                created_by TEXT NOT NULL,
                created_at REAL NOT NULL,
                pinned_message_id TEXT,
                FOREIGN KEY (created_by) REFERENCES users(id)
            );
        """)

        # Chat participants
        conn.execute("""
            CREATE TABLE IF NOT EXISTS chat_members (
                chat_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                joined_at REAL NOT NULL,
                PRIMARY KEY (chat_id, user_id),
                FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """)

        # Messages (with E2EE ciphertext, media info)
        conn.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                id TEXT PRIMARY KEY,
                chat_id TEXT NOT NULL,
                sender_id TEXT NOT NULL,
                ciphertext TEXT NOT NULL,
                iv TEXT NOT NULL,
                media_url TEXT,
                media_type TEXT,
                media_duration REAL DEFAULT 0,
                is_pinned INTEGER DEFAULT 0,
                is_edited INTEGER DEFAULT 0,
                created_at REAL NOT NULL,
                FOREIGN KEY (chat_id) REFERENCES chats(id) ON DELETE CASCADE,
                FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """)

        # School Calendar Schedule
        conn.execute("""
            CREATE TABLE IF NOT EXISTS school_schedule (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                day_of_week INTEGER NOT NULL, -- 1=Monday, 2=Tuesday, etc.
                lesson_number INTEGER NOT NULL,
                subject TEXT NOT NULL,
                time_range TEXT NOT NULL,
                homework TEXT DEFAULT '',
                updated_by TEXT,
                updated_at REAL NOT NULL,
                UNIQUE(day_of_week, lesson_number)
            );
        """)

    # Seed Creator user: CrisPixar
    seed_owner(conn)
    seed_initial_schedule(conn)
    conn.close()

def hash_password(password: str) -> str:
    return hashlib.sha256(password.encode("utf-8")).hexdigest()

def seed_owner(conn):
    owner_id = "owner_cris_pixar"
    owner_username = "CrisPixar"
    owner_pw_hash = hash_password("spotlin_creator_2026")
    with conn:
        conn.execute("""
            INSERT OR IGNORE INTO users (
                id, username, password_hash, bio, avatar_url, role,
                is_creator, custom_badge, can_edit_calendar, can_manage_chat, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """, (
            owner_id, owner_username, owner_pw_hash,
            "Создатель школьного мессенджера Spotlin",
            "/uploads/creator.png",
            "owner", 1, "Создатель", 1, 1, time.time()
        ))

def seed_initial_schedule(conn):
    default_lessons = [
        (1, "Алгебра", "08:30 - 09:15", "Параграф 14, № 14.5, 14.8"),
        (2, "Геометрия", "09:25 - 10:10", "Теорема о трех перпендикулярах"),
        (3, "Физика", "10:30 - 11:15", "Доклад: Законы термодинамики"),
        (4, "Русский язык", "11:35 - 12:20", "Упражнение 218"),
        (5, "Литература", "12:35 - 13:20", "Война и мир (тома 1-2)"),
        (6, "Информатика", "13:30 - 14:15", "Spotlin Android App Development")
    ]
    with conn:
        for day in range(1, 7): # Monday to Saturday
            for num, sub, trange, hw in default_lessons:
                conn.execute("""
                    INSERT OR IGNORE INTO school_schedule (
                        day_of_week, lesson_number, subject, time_range, homework, updated_by, updated_at
                    ) VALUES (?, ?, ?, ?, ?, 'system', ?)
                """, (day, num, sub, trange, hw, time.time()))

def get_connection(db_path=None):
    if db_path is None:
        db_path = DB_PATH
    conn = sqlite3.connect(db_path)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA foreign_keys=ON;")
    return conn
