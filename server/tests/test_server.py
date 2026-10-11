"""
Comprehensive Automated Test Suite for Spotlin Server.
Tests:
- Image, audio, video media compression
- Auth & Profiles (bio, avatar)
- Strict chat rules (1-on-1 only for normal users, groups forbidden)
- Owner creator status and hidden admin panel delegation
- Owner absolute privileges (pin, edit, delete any message)
- School calendar display, homework tracking, and role-based edit access
"""

import os
import sys
import json
import time
import unittest
import asyncio
from aiohttp.test_utils import AioHTTPTestCase, unittest_run_loop
from aiohttp import web
from PIL import Image

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
import server
from database import Database
from media_processor import MediaProcessor

TEST_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "test_data"))
os.makedirs(TEST_DIR, exist_ok=True)

class TestSpotlinServer(AioHTTPTestCase):

    async def get_application(self):
        server.DB_PATH = os.path.join(TEST_DIR, "test_spotlin.db")
        server.UPLOAD_DIR = os.path.join(TEST_DIR, "uploads")
        server.THUMB_DIR = os.path.join(server.UPLOAD_DIR, "thumbnails")
        if os.path.exists(server.DB_PATH):
            try:
                os.remove(server.DB_PATH)
            except Exception:
                pass
        server.db = Database(server.DB_PATH)
        server.media_proc = MediaProcessor(server.UPLOAD_DIR, server.THUMB_DIR)
        return server.create_app()

    async def register_user(self, username, password):
        resp = await self.client.post("/api/auth/register", json={
            "username": username,
            "password": password
        })
        self.assertEqual(resp.status, 200)
        return await resp.json()

    async def login_user(self, username, password):
        resp = await self.client.post("/api/auth/login", json={
            "username": username,
            "password": password
        })
        self.assertEqual(resp.status, 200)
        return await resp.json()

    @unittest_run_loop
    async def test_01_media_image_compression(self):
        img_path = os.path.join(TEST_DIR, "large_test.png")
        img = Image.new("RGBA", (2500, 2000), color=(100, 150, 200, 255))
        img.save(img_path, "PNG")
        
        out_jpg = os.path.join(TEST_DIR, "compressed.jpg")
        w, h, sz = server.media_proc.compress_image(img_path, out_jpg, max_dim=1920, quality=80)
        
        self.assertTrue(os.path.exists(out_jpg))
        self.assertLessEqual(max(w, h), 1920)
        self.assertGreater(sz, 0)
        
        thumb_path = os.path.join(server.THUMB_DIR, "compressed.jpg")
        self.assertTrue(os.path.exists(thumb_path))
        with Image.open(thumb_path) as thumb:
            self.assertLessEqual(max(thumb.size), 300)

    @unittest_run_loop
    async def test_02_auth_and_profiles(self):
        data = await self.register_user("ivan_student", "Password123!")
        student_token = data["accessToken"]
        self.assertFalse(data["user"]["isCreator"])
        self.assertEqual(data["user"]["role"], "user")

        resp = await self.client.get("/api/users/profile", headers={"Authorization": f"Bearer {student_token}"})
        self.assertEqual(resp.status, 200)
        prof = await resp.json()
        self.assertEqual(prof["username"], "ivan_student")

        resp = await self.client.put("/api/users/profile", headers={"Authorization": f"Bearer {student_token}"}, json={
            "bio": "Ученик 10-А класса, увлекаюсь математикой"
        })
        self.assertEqual(resp.status, 200)
        
        resp = await self.client.get("/api/users/profile", headers={"Authorization": f"Bearer {student_token}"})
        prof = await resp.json()
        self.assertEqual(prof["bio"], "Ученик 10-А класса, увлекаюсь математикой")

    @unittest_run_loop
    async def test_03_owner_creator_hardcoded_status(self):
        owner = server.db.get_user_by_username("CrisPixar")
        self.assertIsNotNone(owner)
        self.assertEqual(owner["is_creator"], 1)
        self.assertEqual(owner["role"], "owner")
        self.assertEqual(owner["custom_badge"], "Создатель")

        data = await self.login_user("CrisPixar", "Admin2026!#")
        self.assertTrue(data["user"]["isCreator"])
        self.assertEqual(data["user"]["customBadge"], "Создатель")

    @unittest_run_loop
    async def test_04_regular_user_cannot_create_groups(self):
        ivan_data = await self.register_user("ivan_test4", "Password123!")
        token = ivan_data["accessToken"]

        anna_data = await self.register_user("anna_test4", "Password123!")
        anna_id = anna_data["user"]["id"]

        # Student tries to create a GROUP chat -> MUST BE FORBIDDEN (403)!
        group_resp = await self.client.post("/api/chats", headers={"Authorization": f"Bearer {token}"}, json={
            "type": "group",
            "title": "Классный чат 10-А",
            "memberIds": [anna_id]
        })
        self.assertEqual(group_resp.status, 403)
        err_data = await group_resp.json()
        self.assertIn("Обычные пользователи", err_data["error"])

        # Student creates 1-on-1 direct chat -> MUST SUCCEED (200)!
        direct_resp = await self.client.post("/api/chats", headers={"Authorization": f"Bearer {token}"}, json={
            "type": "direct",
            "memberIds": [anna_id]
        })
        self.assertEqual(direct_resp.status, 200)
        chat = await direct_resp.json()
        self.assertEqual(chat["type"], "direct")

    @unittest_run_loop
    async def test_05_owner_delegation_custom_badges(self):
        owner_data = await self.login_user("CrisPixar", "Admin2026!#")
        owner_token = owner_data["accessToken"]

        anna_data = await self.register_user("anna_test5", "Password123!")
        anna_id = anna_data["user"]["id"]

        # Owner delegates badge "Староста" and permissions to Anna
        del_resp = await self.client.post("/api/admin/delegate", headers={"Authorization": f"Bearer {owner_token}"}, json={
            "userId": anna_id,
            "customBadge": "Староста",
            "canEditCalendar": True,
            "canManageChat": True,
            "isVerified": True
        })
        self.assertEqual(del_resp.status, 200)
        
        anna_updated = server.db.get_user_by_id(anna_id)
        self.assertEqual(anna_updated["custom_badge"], "Староста")
        self.assertEqual(anna_updated["can_edit_calendar"], 1)

        # Ivan (non-owner) tries to delegate rights -> MUST FAIL (403)
        ivan_data = await self.register_user("ivan_test5", "Password123!")
        ivan_token = ivan_data["accessToken"]
        
        del_fail = await self.client.post("/api/admin/delegate", headers={"Authorization": f"Bearer {ivan_token}"}, json={
            "userId": anna_id,
            "customBadge": "Учитель"
        })
        self.assertEqual(del_fail.status, 403)

    @unittest_run_loop
    async def test_06_school_calendar_crud_and_permissions(self):
        ivan_data = await self.register_user("ivan_test6", "Password123!")
        ivan_token = ivan_data["accessToken"]
        ivan_id = ivan_data["user"]["id"]

        anna_data = await self.register_user("anna_test6", "Password123!")
        anna_token = anna_data["accessToken"]
        anna_id = anna_data["user"]["id"]

        owner_data = await self.login_user("CrisPixar", "Admin2026!#")
        owner_token = owner_data["accessToken"]

        # Create direct chat
        direct_resp = await self.client.post("/api/chats", headers={"Authorization": f"Bearer {ivan_token}"}, json={
            "type": "direct",
            "memberIds": [anna_id]
        })
        chat_id = (await direct_resp.json())["id"]

        # Owner delegates canEditCalendar to Anna
        await self.client.post("/api/admin/delegate", headers={"Authorization": f"Bearer {owner_token}"}, json={
            "userId": anna_id,
            "customBadge": "Староста",
            "canEditCalendar": True,
            "canManageChat": True,
            "isVerified": True
        })
        # Re-login anna to refresh profile/token if needed
        anna_login = await self.login_user("anna_test6", "Password123!")
        anna_token = anna_login["accessToken"]

        # 1. Any participant can view calendar
        cal_resp = await self.client.get(f"/api/calendar/{chat_id}", headers={"Authorization": f"Bearer {ivan_token}"})
        self.assertEqual(cal_resp.status, 200)
        cal = await cal_resp.json()
        self.assertIn("schedule", cal)
        self.assertIn("homework", cal)

        # 2. Ivan (ordinary student without rights) tries to edit calendar -> MUST BE FORBIDDEN (403)
        edit_fail = await self.client.put(f"/api/calendar/{chat_id}", headers={"Authorization": f"Bearer {ivan_token}"}, json={
            "schedule": {"Понедельник": ["Математика"]},
            "homework": {"Математика": "Стр. 50"}
        })
        self.assertEqual(edit_fail.status, 403)

        # 3. Anna (delegated 'Староста' with canEditCalendar) edits calendar -> MUST SUCCEED (200)
        edit_success = await self.client.put(f"/api/calendar/{chat_id}", headers={"Authorization": f"Bearer {anna_token}"}, json={
            "schedule": {
                "Понедельник": ["Алгебра", "Геометрия", "Физика", "Информатика"],
                "Вторник": ["Химия", "Биология", "История", "Литература"]
            },
            "homework": {
                "Алгебра": "№ 205, 207",
                "Информатика": "Завершить проект Spotlin"
            }
        })
        self.assertEqual(edit_success.status, 200)
        updated_cal = await edit_success.json()
        self.assertEqual(updated_cal["homework"]["Информатика"], "Завершить проект Spotlin")

    @unittest_run_loop
    async def test_07_owner_absolute_rights_in_chat(self):
        owner_data = await self.login_user("CrisPixar", "Admin2026!#")
        owner_token = owner_data["accessToken"]

        ivan_data = await self.register_user("ivan_test7", "Password123!")
        ivan_token = ivan_data["accessToken"]
        ivan_id = ivan_data["user"]["id"]

        anna_data = await self.register_user("anna_test7", "Password123!")
        anna_id = anna_data["user"]["id"]

        # Ivan creates chat with Anna
        direct_resp = await self.client.post("/api/chats", headers={"Authorization": f"Bearer {ivan_token}"}, json={
            "type": "direct",
            "memberIds": [anna_id]
        })
        chat_id = (await direct_resp.json())["id"]

        # Ivan sends a message
        msg = server.db.add_message(chat_id, ivan_id, "EncryptedSecretPayload123")
        msg_id = msg["id"]

        # Owner pins Ivan's message
        pin_resp = await self.client.post(f"/api/chats/{chat_id}/pin/{msg_id}", headers={"Authorization": f"Bearer {owner_token}"})
        self.assertEqual(pin_resp.status, 200)

        # Owner edits Ivan's message
        edit_resp = await self.client.put(f"/api/chats/{chat_id}/messages/{msg_id}", headers={"Authorization": f"Bearer {owner_token}"}, json={
            "encryptedPayload": "OwnerEditedPayload"
        })
        self.assertEqual(edit_resp.status, 200)
        
        m = server.db.get_messages(chat_id)[0]
        self.assertEqual(m["encrypted_payload"], "OwnerEditedPayload")
        self.assertEqual(m["is_edited"], 1)

        # Owner deletes Ivan's message
        del_resp = await self.client.delete(f"/api/chats/{chat_id}/messages/{msg_id}", headers={"Authorization": f"Bearer {owner_token}"})
        self.assertEqual(del_resp.status, 200)

        all_msgs = server.db.get_messages(chat_id)
        self.assertEqual(len(all_msgs), 0)

    @unittest_run_loop
    async def test_08_media_streaming_range(self):
        # Create a sample media file
        sample_path = os.path.join(server.UPLOAD_DIR, "sample_stream.mp3")
        with open(sample_path, "wb") as f:
            f.write(b"ABCDEFGHIJ" * 100) # 1000 bytes
        
        # Test full get
        resp = await self.client.get("/api/media/sample_stream.mp3")
        self.assertEqual(resp.status, 200)
        self.assertEqual(len(await resp.read()), 1000)

        # Test Range header (partial content 206)
        resp_range = await self.client.get("/api/media/sample_stream.mp3", headers={"Range": "bytes=0-99"})
        self.assertEqual(resp_range.status, 206)
        data = await resp_range.read()
        self.assertEqual(len(data), 100)
        self.assertEqual(data, b"ABCDEFGHIJ" * 10)

    @unittest_run_loop
    async def test_09_e2ee_key_bundle(self):
        ivan_data = await self.register_user("ivan_crypto", "Password123!")
        token = ivan_data["accessToken"]
        uid = ivan_data["user"]["id"]

        # Upload keys
        up_resp = await self.client.post("/api/keys/upload", headers={"Authorization": f"Bearer {token}"}, json={
            "identityKey": "id_key_base64_abc",
            "signedPrekey": "signed_prekey_base64_xyz",
            "prekeySignature": "sig_base64_123",
            "oneTimePrekeys": ["ot_1", "ot_2", "ot_3"]
        })
        self.assertEqual(up_resp.status, 200)

        # Other user fetches key bundle
        anna_data = await self.register_user("anna_crypto", "Password123!")
        anna_token = anna_data["accessToken"]

        bundle_resp = await self.client.get(f"/api/keys/bundle/{uid}", headers={"Authorization": f"Bearer {anna_token}"})
        self.assertEqual(bundle_resp.status, 200)
        bundle = await bundle_resp.json()
        self.assertEqual(bundle["identityKey"], "id_key_base64_abc")
        self.assertEqual(bundle["oneTimePrekey"], "ot_1")


if __name__ == "__main__":
    unittest.main()
