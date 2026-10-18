"""Bridge protocol and input-validation tests; no BrainSuite image is required."""
from __future__ import annotations

import gzip
import http.client
import io
import json
import tempfile
import threading
import unittest
from pathlib import Path
from unittest.mock import patch

from server import Bridge, BridgeHTTPServer

TOKEN = "local-dev-token-of-at-least-24-characters"


def nii() -> bytes:
    header = bytearray(352)
    header[:4] = (348).to_bytes(4, "little")
    header[344:348] = b"n+1\x00"
    return bytes(header)


class BridgeTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.bridge = Bridge(Path(self.directory.name), TOKEN, "bids/brainsuite:v23a")

    def tearDown(self):
        self.bridge.worker.shutdown(wait=True)
        self.directory.cleanup()

    def test_input_validation_and_bids_layout(self):
        with patch("server.subprocess.run") as run:
            run.return_value.returncode = 3  # failures must be reflected in status
            data = gzip.compress(nii())
            job = self.bridge.create(io.BytesIO(data), len(data), "nii.gz", "CSE")
            self.bridge.worker.shutdown(wait=True)
        self.assertEqual(job.status, "failed")
        path = job.root / "bids" / "sub-01" / "anat" / "sub-01_T1w.nii.gz"
        self.assertEqual(path.read_bytes(), data)
        metadata = json.loads((job.root / "bids" / "dataset_description.json").read_text())
        self.assertEqual(metadata["BIDSVersion"], "1.8.0")
        run.assert_called_once()
        command = run.call_args.args[0]
        self.assertEqual(command[-4:], ["--participant_label", "01", "--stages", "CSE"])
        self.assertIn("--network=none", command)

    def test_reject_invalid_nifti_and_stage(self):
        for payload, extension, stages in [
            (b"garbage", "nii", "CSE"),
            (gzip.compress(b"garbage"), "nii.gz", "CSE"),
            (nii(), "nii", "BFP"),
        ]:
            with self.assertRaises(ValueError):
                self.bridge.create(io.BytesIO(payload), len(payload), extension, stages)

    def test_cse_svreg_and_zip_results(self):
        def fake_docker(command, **kwargs):
            output = Path(self.directory.name) / next(iter(self.bridge.jobs)) / "output"
            (output / "test.dfs").write_bytes(b"surface")
            return type("Result", (), {"returncode": 0})()
        with patch("server.subprocess.run", side_effect=fake_docker):
            job = self.bridge.create(io.BytesIO(nii()), len(nii()), "nii", "CSE+SVREG")
            self.bridge.worker.shutdown(wait=True)
        self.assertEqual(job.status, "succeeded")
        self.assertTrue((job.root / "brainsuite-results.zip").is_file())



    def test_http_auth_and_upload(self):
        server = BridgeHTTPServer(("127.0.0.1", 0), self.bridge)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            with http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5) as client:
                client.request("GET", "/v1/jobs/00000000-0000-0000-0000-000000000000")
                response = client.getresponse()
                self.assertEqual(response.status, 401)
                response.read()

            with patch("server.subprocess.run") as runner:
                runner.return_value.returncode = 1
                with http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5) as client:
                    payload = nii()
                    client.request("POST", "/v1/jobs", body=payload, headers={
                        "Authorization": "Bearer " + TOKEN,
                        "X-NeuroView-Format": "nii",
                        "X-BrainSuite-Stages": "CSE",
                    })
                    response = client.getresponse()
                    self.assertEqual(response.status, 202)
                    job = json.loads(response.read())
                    self.assertIn(job["status"], ("queued", "running", "failed"))
                    self.assertEqual(job["stages"], "CSE")
                self.bridge.worker.shutdown(wait=True)

            with http.client.HTTPConnection("127.0.0.1", server.server_port, timeout=5) as client:
                client.request("GET", "/v1/jobs/" + job["id"], headers={
                    "Authorization": "Bearer " + TOKEN,
                })
                response = client.getresponse()
                self.assertEqual(response.status, 200)
                self.assertEqual(json.loads(response.read())["status"], "failed")
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    unittest.main()
