#!/usr/bin/env python3
"""Private BrainSuite BIDS-App companion for NeuroView.

Run only behind an HTTPS reverse proxy (for example, Tailscale Serve).
The service listens on loopback; an explicit Android consent is required
before any image is uploaded. Not a clinical or multi-user service.
"""
from __future__ import annotations

import gzip
import hmac
import json
import os
import re
import secrets
import subprocess
import threading
import uuid
import zipfile
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

MAX_UPLOAD = 2 * 1024 * 1024 * 1024  # hard limit, bytes
MAX_JOBS = 24
DOCKER_IMAGE = "bids/brainsuite:v23a"
RUN_TIMEOUT = 12 * 60 * 60
JOB_RE = re.compile(r"^/v1/jobs/([0-9a-f-]{36})(?:/(results))?$")


@dataclass
class Job:
    identifier: str
    stages: str
    root: Path
    status: str = "queued"
    error: str | None = None


class Bridge:
    def __init__(self, root: Path, token: str, docker_image: str = DOCKER_IMAGE):
        if len(token) < 24:
            raise ValueError("NEUROVIEW_BRAINSUITE_TOKEN must be at least 24 characters")
        self.root = root.resolve()
        self.root.mkdir(parents=True, exist_ok=True, mode=0o700)
        self.token = token
        self.docker_image = docker_image
        self.jobs: dict[str, Job] = {}
        self.lock = threading.Lock()
        self.worker = ThreadPoolExecutor(max_workers=1, thread_name_prefix="brainsuite")

    def authenticated(self, value: str | None) -> bool:
        return bool(value) and hmac.compare_digest(value, "Bearer " + self.token)

    def create(self, source, length: int, extension: str, stages: str) -> Job:
        if extension not in {"nii", "nii.gz"} or stages not in {"CSE", "CSE+SVREG"}:
            raise ValueError("Only T1w .nii/.nii.gz with CSE or CSE+SVREG is supported")
        if not 0 < length <= MAX_UPLOAD:
            raise ValueError("Invalid or oversized NIfTI")
        with self.lock:
            if len(self.jobs) >= MAX_JOBS:
                raise RuntimeError("Job quota reached; remove old jobs on the host")
            identifier = str(uuid.uuid4())
            root = self.root / identifier
            root.mkdir(mode=0o700)
            job = Job(identifier, stages, root)
            self.jobs[identifier] = job

        source_path = root / "bids" / "sub-01" / "anat" / ("sub-01_T1w." + extension)
        source_path.parent.mkdir(parents=True)
        try:
            remaining = length
            with source_path.open("wb") as target:
                while remaining:
                    chunk = source.read(min(1024 * 1024, remaining))
                    if not chunk:
                        raise ValueError("Incomplete MRI upload")
                    target.write(chunk)
                    remaining -= len(chunk)
            # Do not unzip the MRI; inspect only its small NIfTI header.
            opener = gzip.open if extension == "nii.gz" else open
            with opener(source_path, "rb") as nifti:
                header = nifti.read(352)
            if len(header) < 352 or header[344:348] != b"n+1\\x00" or int.from_bytes(
                header[:4], "little"
            ) != 348 and int.from_bytes(header[:4], "big") != 348:
                raise ValueError("Input is not a valid single-file NIfTI-1 volume")
            (root / "bids" / "dataset_description.json").write_text(
                json.dumps({"Name": "NeuroView local input", "BIDSVersion": "1.8.0",
                            "DatasetType": "raw"}), encoding="utf-8"
            )
            self.worker.submit(self._run, job)
            return job
        except Exception:
            with self.lock:
                self.jobs.pop(identifier, None)
            source_path.unlink(missing_ok=True)
            raise

    def _run(self, job: Job):
        job.status = "running"
        input_dir = job.root / "bids"
        output_dir = job.root / "output"
        output_dir.mkdir()
        flags = ["CSE"] if job.stages == "CSE" else ["CSE", "SVREG"]
        command = [
            "docker", "run", "--rm", "--network=none", "--pids-limit=4096",
            "--security-opt=no-new-privileges",
            "--mount", f"type=bind,src={input_dir},dst=/data,readonly",
            "--mount", f"type=bind,src={output_dir},dst=/output",
            self.docker_image, "/data", "/output", "participant",
            "--participant_label", "01", "--stages", *flags,
        ]
        try:
            with (job.root / "processing.log").open("wb") as log:
                result = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT,
                                        timeout=RUN_TIMEOUT, check=False)
            if result.returncode:
                raise RuntimeError(f"BrainSuite container exited with code {result.returncode}")
            result_path = job.root / "brainsuite-results.zip"
            files = [p for p in output_dir.rglob("*") if p.is_file() and not p.is_symlink()]
            if not files:
                raise RuntimeError("BrainSuite returned no output files")
            with zipfile.ZipFile(result_path, "w", zipfile.ZIP_DEFLATED,
                                 compresslevel=4, allowZip64=True) as archive:
                for path in sorted(files):
                    archive.write(path, arcname=str(path.relative_to(output_dir)))
            job.status = "succeeded"
        except (OSError, RuntimeError, subprocess.TimeoutExpired) as exc:
            # Never send filenames, NIfTI metadata or processing logs to the phone.
            job.error = "Processing failed or timed out. Review processing.log on the host."
            job.status = "failed"

    def status(self, identifier: str) -> dict:
        with self.lock:
            job = self.jobs.get(identifier)
        if job is None:
            raise KeyError(identifier)
        return {"id": job.identifier, "stages": job.stages,
                "status": job.status, "message": job.error}


class Handler(BaseHTTPRequestHandler):
    server: "BridgeHTTPServer"

    def log_message(self, format, *args):
        # Intentionally do not log URLs, credentials or MRI-related input.
        pass

    def _send_json(self, status: int, value: dict):
        payload = json.dumps(value).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def _auth(self) -> bool:
        if self.server.bridge.authenticated(self.headers.get("Authorization")):
            return True
        self._send_json(401, {"error": "Unauthorized"})
        return False

    def do_POST(self):
        if urlsplit(self.path).path != "/v1/jobs":
            self._send_json(404, {"error": "Not found"})
            return
        if not self._auth():
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if not 0 < length <= MAX_UPLOAD:
            self._send_json(413, {"error": "Upload size must be 1 byte to 2 GiB"})
            return
        try:
            job = self.server.bridge.create(
                self.rfile, length,
                self.headers.get("X-NeuroView-Format", ""),
                self.headers.get("X-BrainSuite-Stages", ""),
            )
            self._send_json(202, self.server.bridge.status(job.identifier))
        except ValueError:
            self._send_json(400, {"error": "Invalid NIfTI or processing selection"})
        except (OSError, RuntimeError):
            self._send_json(503, {"error": "Processing service cannot accept this job"})

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == "/health":
            self._send_json(200, {"service": "NeuroView BrainSuite bridge", "ready": True})
            return
        if not self._auth():
            return
        match = JOB_RE.fullmatch(path)
        if not match:
            self._send_json(404, {"error": "Not found"})
            return
        identifier, action = match.groups()
        try:
            status = self.server.bridge.status(identifier)
        except KeyError:
            self._send_json(404, {"error": "Unknown job"})
            return
        if action != "results":
            self._send_json(200, status)
            return
        if status["status"] != "succeeded":
            self._send_json(409, {"error": "Results not yet available"})
            return
        archive = self.server.bridge.root / identifier / "brainsuite-results.zip"
        if not archive.is_file():
            self._send_json(404, {"error": "Results missing"})
            return
        self.send_response(200)
        self.send_header("Content-Type", "application/zip")
        self.send_header("Content-Disposition", "attachment; filename=brainsuite-results.zip")
        self.send_header("Content-Length", str(archive.stat().st_size))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        with archive.open("rb") as source:
            while data := source.read(1024 * 1024):
                self.wfile.write(data)


class BridgeHTTPServer(ThreadingHTTPServer):
    def __init__(self, address, bridge: Bridge):
        super().__init__(address, Handler)
        self.bridge = bridge


def main():
    token = os.environ.get("NEUROVIEW_BRAINSUITE_TOKEN", "")
    root = Path(os.environ.get("NEUROVIEW_BRAINSUITE_DATA", "./brainsuite-jobs"))
    port = int(os.environ.get("NEUROVIEW_BRAINSUITE_PORT", "8765"))
    bridge = Bridge(root, token)
    print(f"BrainSuite bridge listening on 127.0.0.1:{port} (HTTPS proxy required)")
    with BridgeHTTPServer(("127.0.0.1", port), bridge) as http:
        http.serve_forever()


if __name__ == "__main__":
    main()
