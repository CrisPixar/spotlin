"""
Spotlin Media Processor:
High-performance compression for images, audio (music), and video.
"""

import os
import subprocess
import shutil
from typing import Tuple, Optional
from PIL import Image, ImageOps

class MediaProcessor:
    def __init__(self, upload_dir: str, thumb_dir: str):
        self.upload_dir = upload_dir
        self.thumb_dir = thumb_dir
        os.makedirs(self.upload_dir, exist_ok=True)
        os.makedirs(self.thumb_dir, exist_ok=True)
        self.ffmpeg_bin = shutil.which("ffmpeg") or "/data/data/com.termux/files/usr/bin/ffmpeg"
        if not os.path.exists(self.ffmpeg_bin):
            self.ffmpeg_bin = None

    def compress_image(self, input_path: str, output_path: str, max_dim: int = 1920, quality: int = 82) -> Tuple[int, int, int]:
        """
        Compress image (JPEG/PNG/WEBP):
        - Corrects EXIF orientation
        - Resizes down if max dimension > max_dim
        - Strips heavy EXIF tags for student privacy
        - Saves as optimized JPEG
        Returns: (width, height, file_size)
        """
        with Image.open(input_path) as img:
            # Handle orientation
            img = ImageOps.exif_transpose(img)
            if img.mode in ("RGBA", "P"):
                # Convert RGBA to RGB with white background
                rgb_img = Image.new("RGB", img.size, (255, 255, 255))
                if img.mode == "RGBA":
                    rgb_img.paste(img, mask=img.split()[3])
                else:
                    rgb_img.paste(img)
                img = rgb_img
            elif img.mode != "RGB":
                img = img.convert("RGB")

            orig_w, orig_h = img.size
            if max(orig_w, orig_h) > max_dim:
                scale = max_dim / float(max(orig_w, orig_h))
                new_w = int(orig_w * scale)
                new_h = int(orig_h * scale)
                img = img.resize((new_w, new_h), Image.Resampling.LANCZOS)
            else:
                new_w, new_h = orig_w, orig_h

            img.save(output_path, "JPEG", quality=quality, optimize=True)

            # Generate thumbnail
            thumb_path = os.path.join(self.thumb_dir, os.path.basename(output_path))
            thumb = img.copy()
            thumb.thumbnail((300, 300), Image.Resampling.LANCZOS)
            thumb.save(thumb_path, "JPEG", quality=75, optimize=True)

            file_size = os.path.getsize(output_path)
            return new_w, new_h, file_size

    def compress_audio(self, input_path: str, output_path: str, bitrate: str = "128k") -> Tuple[int, Optional[float]]:
        """
        Compress music/audio:
        - Re-encodes using AAC or MP3 at given bitrate (default 128 kbps)
        - Normalizes channels to stereo (2ch)
        Returns: (file_size, duration_seconds)
        """
        if self.ffmpeg_bin:
            cmd = [
                self.ffmpeg_bin,
                "-y",
                "-i", input_path,
                "-vn",
                "-c:a", "libmp3lame" if output_path.endswith(".mp3") else "aac",
                "-b:a", bitrate,
                "-ar", "44100",
                "-ac", "2",
                output_path
            ]
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if res.returncode == 0 and os.path.exists(output_path):
                return os.path.getsize(output_path), self.get_duration(output_path)

        # Fallback if ffmpeg is unavailable or fails
        shutil.copyfile(input_path, output_path)
        return os.path.getsize(output_path), None

    def compress_video(self, input_path: str, output_path: str, max_width: int = 1280) -> Tuple[int, Optional[float], str]:
        """
        Compress video:
        - H.264 video with crf 26 (high quality, low bitrate)
        - AAC 128k audio
        - Scale down if width > max_width
        - Faststart flag for instant streaming playback
        - Generates video thumbnail from 1st second
        Returns: (file_size, duration, thumbnail_path)
        """
        thumb_name = os.path.splitext(os.path.basename(output_path))[0] + "_thumb.jpg"
        thumb_path = os.path.join(self.thumb_dir, thumb_name)

        if self.ffmpeg_bin:
            scale_filter = f"scale='min({max_width},iw)':-2"
            cmd = [
                self.ffmpeg_bin,
                "-y",
                "-i", input_path,
                "-vf", scale_filter,
                "-c:v", "libx264",
                "-crf", "26",
                "-preset", "fast",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                output_path
            ]
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            if res.returncode == 0 and os.path.exists(output_path):
                # Generate video thumbnail
                thumb_cmd = [
                    self.ffmpeg_bin,
                    "-y",
                    "-ss", "00:00:01",
                    "-i", output_path,
                    "-vframes", "1",
                    "-q:v", "2",
                    thumb_path
                ]
                subprocess.run(thumb_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                return os.path.getsize(output_path), self.get_duration(output_path), thumb_path

        # Fallback
        shutil.copyfile(input_path, output_path)
        return os.path.getsize(output_path), None, thumb_path

    def get_duration(self, file_path: str) -> Optional[float]:
        ffprobe = shutil.which("ffprobe") or (os.path.join(os.path.dirname(self.ffmpeg_bin), "ffprobe") if self.ffmpeg_bin else None)
        if ffprobe and os.path.exists(ffprobe):
            cmd = [
                ffprobe,
                "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                file_path
            ]
            res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            if res.returncode == 0:
                try:
                    return float(res.stdout.strip())
                except ValueError:
                    pass
        return None
