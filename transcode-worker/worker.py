#!/usr/bin/env python3

import argparse
import json
import logging
import math
import os
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Any
from uuid import UUID

import boto3
from botocore.config import Config as BotoConfig


LOGGER = logging.getLogger("groovsta-transcode-worker")


class ApiError(RuntimeError):
    def __init__(self, status: int | None, message: str):
        super().__init__(message)
        self.status = status


@dataclass(frozen=True)
class Settings:
    api_url: str
    callback_secret: str
    bucket: str
    region: str
    s3_endpoint: str | None
    s3_path_style_access: bool
    poll_seconds: float
    request_timeout_seconds: float
    transcode_timeout_seconds: int

    @classmethod
    def from_environment(cls) -> "Settings":
        return cls(
            api_url=_required("GROOVSTA_API_URL").rstrip("/"),
            callback_secret=_required("GROOVSTA_TRANSCODE_CALLBACK_SECRET"),
            bucket=_required("GROOVSTA_S3_BUCKET"),
            region=os.getenv("AWS_REGION", "ap-south-1"),
            s3_endpoint=os.getenv("GROOVSTA_S3_ENDPOINT") or None,
            s3_path_style_access=_boolean("GROOVSTA_S3_PATH_STYLE_ACCESS", False),
            poll_seconds=_positive_float("GROOVSTA_TRANSCODE_POLL_SECONDS", 5.0),
            request_timeout_seconds=_positive_float("GROOVSTA_TRANSCODE_REQUEST_TIMEOUT_SECONDS", 30.0),
            transcode_timeout_seconds=_positive_int("GROOVSTA_TRANSCODE_TIMEOUT_SECONDS", 1800),
        )


@dataclass(frozen=True)
class TranscodeJob:
    job_id: UUID
    target_type: str
    target_id: UUID
    source_object_key: str
    output_prefix: str

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> "TranscodeJob":
        try:
            target_type = payload.get("targetType", "REEL")
            target_id = payload.get("targetId") or payload.get("reelId")
            job = cls(
                job_id=UUID(payload["jobId"]),
                target_type=target_type,
                target_id=UUID(target_id),
                source_object_key=payload["sourceObjectKey"],
                output_prefix=payload["outputPrefix"],
            )
        except (KeyError, TypeError, ValueError) as error:
            raise ValueError("Claim response did not contain a valid transcode job") from error
        if job.target_type not in {"REEL", "STORY", "CHAT_ATTACHMENT"}:
            raise ValueError("Claim response contained an invalid transcode target type")
        if not job.source_object_key or not job.output_prefix.endswith("/"):
            raise ValueError("Claim response contained invalid S3 object keys")
        return job


@dataclass(frozen=True)
class TranscodeResult:
    published_object_key: str
    thumbnail_object_key: str
    duration_seconds: int
    width: int
    height: int


class BackendClient:
    def __init__(self, settings: Settings):
        self.api_url = settings.api_url
        self.secret = settings.callback_secret
        self.timeout = settings.request_timeout_seconds

    def claim(self) -> TranscodeJob | None:
        status, body = self._post("/api/v1/internal/transcode/jobs/claim")
        if status == 204:
            return None
        if status != 200:
            raise ApiError(status, f"Unexpected claim response status {status}")
        try:
            payload = json.loads(body)
        except json.JSONDecodeError as error:
            raise ApiError(status, "Claim response was not valid JSON") from error
        return TranscodeJob.from_json(payload)

    def complete(self, job: TranscodeJob, result: TranscodeResult) -> None:
        payload = {
            "publishedObjectKey": result.published_object_key,
            "thumbnailObjectKey": result.thumbnail_object_key,
            "durationSeconds": result.duration_seconds,
            "width": result.width,
            "height": result.height,
        }
        try:
            status, _ = self._post(
                f"/api/v1/internal/transcode/jobs/{job.job_id}/complete",
                payload,
            )
        except ApiError as error:
            if error.status != 404 or job.target_type != "REEL":
                raise
            status, _ = self._post(
                f"/api/v1/internal/transcode/reels/{job.target_id}/complete",
                payload,
            )
        if status < 200 or status >= 300:
            raise ApiError(status, f"Unexpected completion response status {status}")

    def fail(self, job: TranscodeJob, reason: str) -> None:
        encoded_reason = urllib.parse.urlencode({"reason": reason[:1000]})
        try:
            status, _ = self._post(
                f"/api/v1/internal/transcode/jobs/{job.job_id}/fail?{encoded_reason}"
            )
        except ApiError as error:
            if error.status != 404 or job.target_type != "REEL":
                raise
            status, _ = self._post(
                f"/api/v1/internal/transcode/reels/{job.target_id}/fail?{encoded_reason}"
            )
        if status < 200 or status >= 300:
            raise ApiError(status, f"Unexpected failure response status {status}")

    def _post(self, path: str, payload: dict[str, Any] | None = None) -> tuple[int, str]:
        body = None if payload is None else json.dumps(payload).encode("utf-8")
        headers = {"X-Transcode-Secret": self.secret}
        if body is not None:
            headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            f"{self.api_url}{path}",
            data=body,
            headers=headers,
            method="POST",
        )
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                return response.status, response.read().decode("utf-8")
        except urllib.error.HTTPError as error:
            response_body = error.read(2048).decode("utf-8", errors="replace")
            raise ApiError(error.code, f"Backend returned HTTP {error.code}: {response_body}") from error
        except urllib.error.URLError as error:
            raise ApiError(None, f"Could not reach the backend: {error.reason}") from error


class VideoTranscoder:
    def __init__(self, settings: Settings):
        addressing_style = "path" if settings.s3_path_style_access else "auto"
        self.bucket = settings.bucket
        self.timeout = settings.transcode_timeout_seconds
        self.s3 = boto3.client(
            "s3",
            region_name=settings.region,
            endpoint_url=settings.s3_endpoint,
            config=BotoConfig(
                retries={"max_attempts": 4, "mode": "standard"},
                s3={"addressing_style": addressing_style},
            ),
        )

    def transcode(self, job: TranscodeJob) -> TranscodeResult:
        with tempfile.TemporaryDirectory(prefix=f"groovsta-{job.target_id}-") as directory:
            work_directory = Path(directory)
            source_suffix = Path(job.source_object_key).suffix[:10] or ".video"
            source_path = work_directory / f"source{source_suffix}"
            video_path = work_directory / "video.mp4"
            thumbnail_path = work_directory / "thumbnail.webp"

            LOGGER.info("Downloading source for %s %s", job.target_type.lower(), job.target_id)
            self.s3.download_file(self.bucket, job.source_object_key, str(source_path))

            duration = self._duration(source_path)
            self._encode_video(source_path, video_path)
            self._encode_thumbnail(video_path, thumbnail_path, duration)
            width, height = self._dimensions(video_path)

            published_key = f"{job.output_prefix}video.mp4"
            thumbnail_key = f"{job.output_prefix}thumbnail.webp"
            LOGGER.info("Uploading outputs for %s %s", job.target_type.lower(), job.target_id)
            self.s3.upload_file(
                str(video_path),
                self.bucket,
                published_key,
                ExtraArgs={"ContentType": "video/mp4"},
            )
            self.s3.upload_file(
                str(thumbnail_path),
                self.bucket,
                thumbnail_key,
                ExtraArgs={"ContentType": "image/webp"},
            )
            return TranscodeResult(
                published_object_key=published_key,
                thumbnail_object_key=thumbnail_key,
                duration_seconds=max(1, math.ceil(duration)),
                width=width,
                height=height,
            )

    def _duration(self, source_path: Path) -> float:
        completed = self._run(
            [
                "ffprobe",
                "-v",
                "error",
                "-show_entries",
                "format=duration",
                "-of",
                "json",
                str(source_path),
            ]
        )
        try:
            duration = float(json.loads(completed.stdout)["format"]["duration"])
        except (KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            raise RuntimeError("ffprobe did not return a valid video duration") from error
        if not math.isfinite(duration) or duration <= 0:
            raise RuntimeError("Uploaded video has an invalid duration")
        return duration

    def _dimensions(self, video_path: Path) -> tuple[int, int]:
        completed = self._run(
            [
                "ffprobe",
                "-v",
                "error",
                "-select_streams",
                "v:0",
                "-show_entries",
                "stream=width,height",
                "-of",
                "json",
                str(video_path),
            ]
        )
        try:
            stream = json.loads(completed.stdout)["streams"][0]
            width = int(stream["width"])
            height = int(stream["height"])
        except (IndexError, KeyError, TypeError, ValueError, json.JSONDecodeError) as error:
            raise RuntimeError("ffprobe did not return valid video dimensions") from error
        if width <= 0 or height <= 0:
            raise RuntimeError("Transcoded video has invalid dimensions")
        return width, height

    def _encode_video(self, source_path: Path, output_path: Path) -> None:
        video_filter = (
            "scale=w='trunc(min(1080,iw)/2)*2':h=-2:"
            "force_original_aspect_ratio=decrease,format=yuv420p"
        )
        self._run(
            [
                "ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-i",
                str(source_path),
                "-map",
                "0:v:0",
                "-map",
                "0:a:0?",
                "-vf",
                video_filter,
                "-r",
                "30",
                "-c:v",
                "libx264",
                "-preset",
                "medium",
                "-crf",
                "23",
                "-c:a",
                "aac",
                "-b:a",
                "128k",
                "-ac",
                "2",
                "-movflags",
                "+faststart",
                "-sn",
                "-dn",
                str(output_path),
            ]
        )

    def _encode_thumbnail(self, video_path: Path, output_path: Path, duration: float) -> None:
        capture_at = min(1.0, duration / 2.0)
        thumbnail_filter = "scale=w='trunc(min(720,iw)/2)*2':h=-2:force_original_aspect_ratio=decrease"
        self._run(
            [
                "ffmpeg",
                "-nostdin",
                "-hide_banner",
                "-loglevel",
                "error",
                "-y",
                "-ss",
                f"{capture_at:.3f}",
                "-i",
                str(video_path),
                "-frames:v",
                "1",
                "-vf",
                thumbnail_filter,
                "-c:v",
                "libwebp",
                "-quality",
                "82",
                str(output_path),
            ]
        )

    def _run(self, command: list[str]) -> subprocess.CompletedProcess[str]:
        try:
            return subprocess.run(
                command,
                check=True,
                capture_output=True,
                text=True,
                timeout=self.timeout,
            )
        except subprocess.TimeoutExpired as error:
            raise RuntimeError("Video processing exceeded the configured timeout") from error
        except subprocess.CalledProcessError as error:
            detail = (error.stderr or "FFmpeg command failed").strip()[-2000:]
            raise RuntimeError(detail) from error


def process_job(client: BackendClient, transcoder: VideoTranscoder, job: TranscodeJob) -> None:
    LOGGER.info("Claimed job %s for %s %s", job.job_id, job.target_type.lower(), job.target_id)
    try:
        result = transcoder.transcode(job)
    except Exception as error:
        LOGGER.exception("Transcoding failed for %s %s", job.target_type.lower(), job.target_id)
        reason = _failure_reason(error)
        try:
            client.fail(job, reason)
        except Exception:
            LOGGER.exception("Could not report failure for %s %s", job.target_type.lower(), job.target_id)
        return

    for attempt in range(1, 4):
        try:
            client.complete(job, result)
            LOGGER.info("Completed %s %s", job.target_type.lower(), job.target_id)
            return
        except Exception:
            LOGGER.exception(
                "Completion callback attempt %s failed for %s %s",
                attempt,
                job.target_type.lower(),
                job.target_id,
            )
            if attempt < 3:
                time.sleep(2 ** attempt)

    LOGGER.error(
        "Outputs for %s %s were uploaded, but completion could not be confirmed; manual recovery is required",
        job.target_type.lower(),
        job.target_id,
    )


def run_worker(settings: Settings, run_once: bool) -> None:
    client = BackendClient(settings)
    transcoder = VideoTranscoder(settings)
    LOGGER.info("Worker started; polling %s", settings.api_url)
    while True:
        try:
            job = client.claim()
        except Exception:
            LOGGER.exception("Could not claim a transcode job")
            if run_once:
                raise
            time.sleep(settings.poll_seconds)
            continue

        if job is None:
            if run_once:
                LOGGER.info("No transcode job is queued")
                return
            time.sleep(settings.poll_seconds)
            continue

        process_job(client, transcoder, job)
        if run_once:
            return


def check_tools() -> None:
    for executable in ("ffmpeg", "ffprobe"):
        completed = subprocess.run(
            [executable, "-version"],
            check=True,
            capture_output=True,
            text=True,
        )
        version = completed.stdout.splitlines()[0]
        print(version)


def _required(name: str) -> str:
    value = os.getenv(name, "").strip()
    if not value:
        raise ValueError(f"{name} is required")
    return value


def _boolean(name: str, default: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return default
    normalized = value.strip().lower()
    if normalized in {"true", "1", "yes"}:
        return True
    if normalized in {"false", "0", "no"}:
        return False
    raise ValueError(f"{name} must be true or false")


def _positive_float(name: str, default: float) -> float:
    value = float(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


def _positive_int(name: str, default: int) -> int:
    value = int(os.getenv(name, str(default)))
    if value <= 0:
        raise ValueError(f"{name} must be positive")
    return value


def _failure_reason(error: Exception) -> str:
    message = " ".join(str(error).split())
    return (message or error.__class__.__name__)[:1000]


def main() -> int:
    parser = argparse.ArgumentParser(description="Groovsta FFmpeg transcode worker")
    parser.add_argument("--check", action="store_true", help="verify that FFmpeg and ffprobe are installed")
    parser.add_argument("--once", action="store_true", help="claim at most one job, then exit")
    arguments = parser.parse_args()

    logging.basicConfig(
        level=os.getenv("GROOVSTA_TRANSCODE_LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    if arguments.check:
        check_tools()
        return 0

    try:
        run_worker(Settings.from_environment(), arguments.once)
        return 0
    except KeyboardInterrupt:
        LOGGER.info("Worker stopped")
        return 0
    except Exception:
        LOGGER.exception("Worker stopped because of an unrecoverable error")
        return 1


if __name__ == "__main__":
    sys.exit(main())
