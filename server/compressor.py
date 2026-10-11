import os
import subprocess
import shutil
from PIL import Image

UPLOAD_DIR = os.path.join(os.path.dirname(__file__), "uploads")
THUMBNAILS_DIR = os.path.join(UPLOAD_DIR, "thumbnails")

os.makedirs(UPLOAD_DIR, exist_ok=True)
os.makedirs(THUMBNAILS_DIR, exist_ok=True)

def compress_image(input_path: str, output_path: str, max_width=1280, max_height=1280, quality=82) -> bool:
    """
    Compresses an image using Pillow:
    - Strips EXIF metadata for privacy
    - Resizes to max bounding box while keeping aspect ratio
    - Saves with optimized JPEG compression
    """
    try:
        with Image.open(input_path) as img:
            # Convert RGBA to RGB for JPEG compression
            if img.mode in ("RGBA", "P"):
                img = img.convert("RGB")
            
            # Downscale if larger than max dimensions
            img.thumbnail((max_width, max_height), Image.Resampling.LANCZOS)
            
            # Save compressed
            img.save(output_path, "JPEG", optimize=True, quality=quality)
            return True
    except Exception as e:
        print(f"Image compression error: {e}")
        return False

def compress_audio(input_path: str, output_path: str, bitrate="128k") -> bool:
    """
    Compresses audio using ffmpeg:
    - Encodes with libmp3lame or aac
    - Sets bitrate to 128 kbps stereo
    """
    cmd = [
        "ffmpeg", "-y", "-i", input_path,
        "-vn",
        "-b:a", bitrate,
        "-ar", "44100",
        "-ac", "2",
        output_path
    ]
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
        return result.returncode == 0
    except Exception as e:
        print(f"Audio compression error: {e}")
        return False

def compress_video(input_path: str, output_path: str, thumbnail_path: str = None) -> bool:
    """
    Compresses video using ffmpeg:
    - H.264 video codec with CRF 26
    - AAC audio codec with 128k bitrate
    - +faststart flag for smooth web / mobile streaming
    - Generates thumbnail frame at 00:00:01
    """
    cmd = [
        "ffmpeg", "-y", "-i", input_path,
        "-vcodec", "libx264",
        "-crf", "26",
        "-preset", "faster",
        "-acodec", "aac",
        "-b:a", "128k",
        "-movflags", "+faststart",
        output_path
    ]
    try:
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=180)
        if result.returncode != 0:
            return False

        if thumbnail_path:
            thumb_cmd = [
                "ffmpeg", "-y", "-ss", "00:00:01",
                "-i", input_path,
                "-vframes", "1",
                "-q:v", "2",
                thumbnail_path
            ]
            subprocess.run(thumb_cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=30)
        return True
    except Exception as e:
        print(f"Video compression error: {e}")
        return False
