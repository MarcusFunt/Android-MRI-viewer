# BrainSuite anatomical processing (optional companion)

NeuroView's **Viewer → Process** panel supports the official
[BrainSuite BIDS App](https://github.com/bids-apps/BrainSuite), version 23a,
for T1-weighted MRI. This is **not an Android implementation of BrainSuite**.
The full upstream pipeline runs in a Docker container on a workstation;
the Android app only transfers the selected NIfTI to that workstation,
checks progress, and downloads a ZIP of derivatives.

## Supported operations

| Edit action | Official BrainSuite stages | Input |
| --- | --- | --- |
| Surface extraction | CSE | One T1w NIfTI-1 `.nii` or `.nii.gz` |
| Extract + atlas | CSE SVREG | One T1w NIfTI-1 `.nii` or `.nii.gz` |

CSE includes brain/cortical surface extraction and cortical-thickness steps.
SVReg adds surface-constrained atlas alignment. A one-file picker is *not*
sufficient to safely invoke the upstream diffusion (BDP) or functional
(BFP) BIDS pipelines; those remain unavailable here.

**No original MRI is modified**. Bridge outputs are held under its private
work directory until you save the results ZIP. NeuroView does not claim to
validate a BrainSuite result or display DFS meshes yet.

## Prepare a computer

Use Python 3.10+ and Docker Engine or Docker Desktop with enough disk space,
memory and CPU for BrainSuite. The BrainSuite 23a image is large; budget substantial download space and additional disk capacity for per-job inputs and derivatives. On the host:

```bash
docker pull bids/brainsuite:v23a
export NEUROVIEW_BRAINSUITE_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
export NEUROVIEW_BRAINSUITE_DATA="$HOME/neuroview-brainsuite-jobs"
printf 'BrainSuite access token: %s\n' "$NEUROVIEW_BRAINSUITE_TOKEN"
python3 tools/brainsuite_bridge/server.py
```

Save that access token securely; enter it on the phone. The bridge does not
store the token on Android, and the desktop process needs the environment
variable every time it starts.

The bridge listens **only on `127.0.0.1:8765` over local HTTP**. Do NOT
forward this raw local HTTP server to the public internet. Use a private
trusted HTTPS reverse proxy. For example, if your computer and Android
device are in the same Tailscale tailnet, run on the computer:

```bash
tailscale serve --bg http://127.0.0.1:8765
tailscale serve status
```

Tailscale Serve supplies a private tailnet HTTPS URL with a trusted TLS
certificate. Use the displayed **`https://....ts.net`** address in the
NeuroView processing panel (not the local HTTP URL). Do not use Tailscale Funnel.
Make sure the Android device is joined to the tailnet and your ACLs restrict
access to trusted devices.

## Run a job

1. Import and open a valid **T1-weighted** NIfTI in NeuroView.
2. Select **Process**, then choose **Surface extraction** or
   **Extract + atlas**.
3. Enter your own HTTPS host address and the bridge access token.
4. Read and explicitly accept the scan-transfer privacy notice before
   pressing **Send T1w scan for processing**. The app does not silently upload.
5. The app keeps a small local list of recent job references (job ID, host URL,
   stage and submission timestamp; no access token or MRI file data). Jobs
   are checked periodically while the panel remains visible. Paste a job ID
   or select a recent job to resume monitoring. Processing can take substantial time.
6. When status is `succeeded`, use **Save results ZIP**. Extract it outside
   NeuroView and, when relevant, import any NIfTI derivatives into the Library.

The upstream stages are called as:

```text
docker run --rm --network=none --pids-limit=4096 --security-opt=no-new-privileges \
  --mount type=bind,src=<job>/bids,dst=/data,readonly \
  --mount type=bind,src=<job>/output,dst=/output \
  bids/brainsuite:v23a \
  /data /output participant --participant_label 01 --stages CSE [SVREG]
```

Each submitted image is wrapped in a minimal BIDS single-subject T1w dataset.
This **cannot reconstruct missing protocol/participant information**, and it
is not a general multi-session BIDS importer.

## Privacy and limitations

- BrainSuite does not run on the phone. HTTPS and ordinary platform TLS
  validation are mandatory; HTTP and cross-host redirects are refused by
  the Android client. Images can contain identifiable facial anatomy.
- The bridge should be run by a trusted user on their own computer with
  a strong token and a private tailnet. It has **no multi-user isolation**,
  persistence database, resumable uploads, or rate-limiting suitable for
  public hosting. It accepts at most 2 GiB per upload and limits the number
  of jobs per service process.
- Work folders may contain sensitive original scans, BrainSuite derivatives,
  and `processing.log`. They are stored under
  `NEUROVIEW_BRAINSUITE_DATA/<job-id>` and are **not automatically deleted**.
  Manually remove obsolete folders securely. If the bridge restarts, its
  in-memory status index is lost, though the files remain.
- Jobs are serialized (only one Docker run at a time), with a 12-hour
  execution timeout. A job may require significant system resources.
- No client progress percentages, remote cancellation, desktop auto-start,
  DSM/clinical validation or automatic viewing of cortical-surface meshes.
- Not for diagnosis, therapy, or clinical treatment decisions.

## Troubleshooting and service lifecycle

- **Connection rejected:** verify that the Android device can reach the **private HTTPS** tailnet address (not the loopback HTTP service), that Tailscale Serve is active, and that the token matches the host environment. The app does not allow insecure HTTP or redirect-based uploads.
- **A job disappeared after restart:** the bridge stores job state in memory only. Existing files may remain under `NEUROVIEW_BRAINSUITE_DATA`, but their job IDs are no longer retrievable from the restarted service; the phone's recent-job list does not restore server state.
- **Job failed or appears stuck:** review the matching host-side `processing.log` and Docker status. Check CPU, RAM, disk space and the 12-hour timeout. Do not share host logs containing sensitive MRI metadata publicly.
- **Result not visible in the app:** the current client downloads a ZIP. Extract it using a trusted tool; supported NIfTI derivatives can be imported separately, but BrainSuite DFS meshes and direct ZIP ingestion are not implemented.
- **Disk usage keeps increasing:** clean obsolete job folders on your host after verifying that needed derivative ZIPs are safely retained. Stopping the service does not automatically erase originals or outputs.

## Development checks

```bash
python3 -m unittest discover -s tools/brainsuite_bridge -p 'test_*.py'
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Python tests use a mocked Docker invocation and cover BIDS layout, stage
arguments, invalid inputs and ZIP output. **They do not validate BrainSuite
scientific outputs.** A real host-to-Android integration test with the
actual `bids/brainsuite:v23a` image and an anatomical test scan is still
required before calling the workflow experimentally validated.

Upstream references:
- https://brainsuite.org/BIDS/participant-level.html
- https://brainsuite.org/BIDS/walkthrough.html
