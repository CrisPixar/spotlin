import unittest
import os
import sys
import tempfile
import asyncio
from aiohttp.test_utils import AioHTTPTestCase, unittest_run_loop
from aiohttp import web
from PIL import Image

# Add server directory to path
server_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
if server_dir not in sys.path:
    sys.path.insert(0, server_dir)

import db
from app import create_app
from compressor import compress_image, compress_audio, compress_video

class TestSpotlinMessenger(AioHTTPTestCase):
    def setUp(self):
        self.temp_db_fd, self.temp_db_path = tempfile.mkstemp(suffix=".db")
        db.DB_PATH = self.temp_db_path
        db.init_db(self.temp_db_path)
        super().setUp()

    def tearDown(self):
        super().tearDown()
        os.close(self.temp_db_fd)
        if os.path.exists(self.temp_db_path):
            os.remove(self.temp_db_path)

    async def get_application(self):
        return create_app()

    @unittest_run_loop
    async def test_owner_seed_and_login(self):
        """Owner CrisPixar must be pre-seeded with Creator status"""
        resp = await self.client.post("/api/auth/login", json={
            "username": "CrisPixar",
            "password": "spotlin_creator_2026"
        })
        self.assertEqual(resp.status, 200)
        data = await resp.json()
        self.assertTrue(data["user"]["is_creator"])
        self.assertEqual(data["user"]["custom_badge"], "Создатель")
        self.assertEqual(data["user"]["role"], "owner")

    @unittest_run_loop
    async def test_user_registration_and_direct_chat_only(self):
        """Regular users can only chat 1-on-1 (тет-а-тет) and cannot create groups"""
        # Register user 1
        r1 = await self.client.post("/api/auth/register", json={
            "username": "alice_school",
            "password": "alice_password123",
            "bio": "Ученица 10-А"
        })
        self.assertEqual(r1.status, 200)
        u1_data = await r1.json()
        token1 = u1_data["token"]
        self.assertFalse(u1_data["user"]["is_creator"])

        # Register user 2
        r2 = await self.client.post("/api/auth/register", json={
            "username": "bob_school",
            "password": "bob_password123",
            "bio": "Ученик 10-А"
        })
        self.assertEqual(r2.status, 200)
        u2_data = await r2.json()
        u2_id = u2_data["user"]["id"]

        # Attempt to create group chat as regular user -> must return 403 Forbidden
        grp_resp = await self.client.post("/api/chats", headers={"Authorization": f"Bearer {token1}"}, json={
            "type": "group",
            "title": "Запрещенная группа"
        })
        self.assertEqual(grp_resp.status, 403)

        # Create 1-on-1 direct chat -> must succeed (200)
        direct_resp = await self.client.post("/api/chats", headers={"Authorization": f"Bearer {token1}"}, json={
            "type": "direct",
            "target_user_id": u2_id,
            "title": "Диалог с Бобом"
        })
        self.assertEqual(direct_resp.status, 200)
        chat = await direct_resp.json()
        self.assertEqual(chat["type"], "direct")

        # Send encrypted message in chat
        msg_resp = await self.client.post(f"/api/chats/{chat['id']}/messages", headers={"Authorization": f"Bearer {token1}"}, json={
            "ciphertext": "BASE64_CIPHERTEXT_TEST",
            "iv": "BASE64_IV_TEST"
        })
        self.assertEqual(msg_resp.status, 200)
        msg_data = await msg_resp.json()
        self.assertEqual(msg_data["ciphertext"], "BASE64_CIPHERTEXT_TEST")

    @unittest_run_loop
    async def test_owner_absolute_powers_and_delegation(self):
        """Owner can edit/delete/pin any message and delegate badges to other users"""
        # Login owner
        owner_resp = await self.client.post("/api/auth/login", json={
            "username": "CrisPixar",
            "password": "spotlin_creator_2026"
        })
        owner_data = await owner_resp.json()
        owner_token = owner_data["token"]

        # Register normal student
        s_resp = await self.client.post("/api/auth/register", json={
            "username": "student_timur",
            "password": "password789"
        })
        s_data = await s_resp.json()
        s_token = s_data["token"]
        s_id = s_data["user"]["id"]

        # Delegate badge and calendar rights to timur
        del_resp = await self.client.post("/api/admin/delegate", headers={"Authorization": f"Bearer {owner_token}"}, json={
            "user_id": s_id,
            "custom_badge": "Староста",
            "can_edit_calendar": True,
            "can_manage_chat": True
        })
        self.assertEqual(del_resp.status, 200)
        del_data = await del_resp.json()
        self.assertEqual(del_data["custom_badge"], "Староста")
        self.assertTrue(del_data["can_edit_calendar"])

        # Create chat and message by timur
        chat_resp = await self.client.post("/api/chats", headers={"Authorization": f"Bearer {s_token}"}, json={
            "type": "direct",
            "target_user_id": owner_data["user"]["id"]
        })
        chat_id = (await chat_resp.json())["id"]

        msg_resp = await self.client.post(f"/api/chats/{chat_id}/messages", headers={"Authorization": f"Bearer {s_token}"}, json={
            "ciphertext": "STUDENT_MESSAGE_ORIGINAL",
            "iv": "IV"
        })
        msg_id = (await msg_resp.json())["id"]

        # Owner pins student message -> 200
        pin_resp = await self.client.post(f"/api/messages/{msg_id}/pin", headers={"Authorization": f"Bearer {owner_token}"})
        self.assertEqual(pin_resp.status, 200)
        self.assertTrue((await pin_resp.json())["is_pinned"])

        # Owner edits student message -> 200
        edit_resp = await self.client.put(f"/api/messages/{msg_id}", headers={"Authorization": f"Bearer {owner_token}"}, json={
            "ciphertext": "EDITED_BY_OWNER",
            "iv": "IV2"
        })
        self.assertEqual(edit_resp.status, 200)

        # Owner deletes student message -> 200
        del_msg_resp = await self.client.delete(f"/api/messages/{msg_id}", headers={"Authorization": f"Bearer {owner_token}"})
        self.assertEqual(del_msg_resp.status, 200)

    @unittest_run_loop
    async def test_school_calendar_schedule(self):
        """School calendar returns schedule, and only permitted users can edit homework"""
        # Get calendar
        cal_resp = await self.client.get("/api/calendar")
        self.assertEqual(cal_resp.status, 200)
        schedule = await cal_resp.json()
        self.assertTrue(len(schedule) > 0)
        lesson_id = schedule[0]["id"]

        # Register unauthorized student
        st_resp = await self.client.post("/api/auth/register", json={
            "username": "student_no_rights",
            "password": "password123"
        })
        st_token = (await st_resp.json())["token"]

        # Unauthorized student cannot edit homework
        unauth_update = await self.client.put(f"/api/calendar/{lesson_id}", headers={"Authorization": f"Bearer {st_token}"}, json={
            "homework": "Взломанное ДЗ"
        })
        self.assertEqual(unauth_update.status, 403)

        # Owner can edit homework
        owner_resp = await self.client.post("/api/auth/login", json={
            "username": "CrisPixar",
            "password": "spotlin_creator_2026"
        })
        owner_token = (await owner_resp.json())["token"]

        owner_update = await self.client.put(f"/api/calendar/{lesson_id}", headers={"Authorization": f"Bearer {owner_token}"}, json={
            "homework": "Новое задание: Тест по главе 3"
        })
        self.assertEqual(owner_update.status, 200)
        self.assertEqual((await owner_update.json())["homework"], "Новое задание: Тест по главе 3")

    def test_media_compression_algorithms(self):
        """Real compression algorithms: Pillow LANCZOS photo, ffmpeg audio and video"""
        # Test Image Compression
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as raw_img:
            # Create a large 2000x2000 image
            img = Image.new("RGBA", (2000, 2000), color=(255, 100, 100, 255))
            img.save(raw_img.name, "PNG")
            raw_img_path = raw_img.name

        out_img_path = raw_img_path + "_compressed.jpg"
        self.assertTrue(compress_image(raw_img_path, out_img_path))
        self.assertTrue(os.path.exists(out_img_path))
        # Verify resized dimensions
        with Image.open(out_img_path) as c_img:
            self.assertTrue(c_img.width <= 1280 and c_img.height <= 1280)
        os.remove(raw_img_path)
        os.remove(out_img_path)

        # Test Audio Compression (synthetic audio tone via ffmpeg)
        raw_audio_path = tempfile.mktemp(suffix=".wav")
        out_audio_path = tempfile.mktemp(suffix=".mp3")
        # Generate 1 sec sine wave
        import subprocess
        subprocess.run(["ffmpeg", "-y", "-f", "lavfi", "-i", "sine=frequency=1000:duration=1", raw_audio_path],
                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertTrue(os.path.exists(raw_audio_path))
        self.assertTrue(compress_audio(raw_audio_path, out_audio_path))
        self.assertTrue(os.path.exists(out_audio_path))
        self.assertTrue(os.path.getsize(out_audio_path) > 0)
        os.remove(raw_audio_path)
        os.remove(out_audio_path)

        # Test Video Compression (synthetic 1 sec video via ffmpeg)
        raw_vid_path = tempfile.mktemp(suffix=".mp4")
        out_vid_path = tempfile.mktemp(suffix=".mp4")
        thumb_vid_path = tempfile.mktemp(suffix=".jpg")
        subprocess.run([
            "ffmpeg", "-y", "-f", "lavfi", "-i", "testsrc=duration=2:size=640x360:rate=30",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=2",
            "-c:v", "libx264", "-c:a", "aac", raw_vid_path
        ], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        self.assertTrue(os.path.exists(raw_vid_path))
        self.assertTrue(compress_video(raw_vid_path, out_vid_path, thumb_vid_path))
        self.assertTrue(os.path.exists(out_vid_path))
        self.assertTrue(os.path.exists(thumb_vid_path))
        os.remove(raw_vid_path)
        os.remove(out_vid_path)
        os.remove(thumb_vid_path)

if __name__ == "__main__":
    unittest.main()
