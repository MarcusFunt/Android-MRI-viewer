param(
    [int]$Port = 8765,
    [switch]$ShowToken,
    [string]$DataRoot
)

$ErrorActionPreference = 'Stop'
$serviceName = 'NeuroView BrainSuite bridge'
$healthUri = "http://127.0.0.1:$Port/health"
$stateRoot = Join-Path $env:LOCALAPPDATA 'NeuroView'
if ([string]::IsNullOrWhiteSpace($DataRoot)) {
    $DataRoot = Join-Path $stateRoot 'BrainSuiteJobs'
}
$dataRoot = [System.IO.Path]::GetFullPath($DataRoot)
$tokenPath = Join-Path $stateRoot 'brainsuite-token.dpapi'
$serverPath = Join-Path $PSScriptRoot 'server.py'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

if (-not (Test-Path -LiteralPath $serverPath)) {
    throw "Cannot find bridge server: $serverPath"
}
New-Item -ItemType Directory -Path $stateRoot, $dataRoot -Force | Out-Null

$existingHealth = $null
try {
    $candidate = Invoke-RestMethod -Uri $healthUri -TimeoutSec 2
    if ($candidate.service -eq $serviceName) {
        $existingHealth = $candidate
    }
} catch {
    # No bridge is listening yet; proceed with startup.
}

$newToken = $false
if (Test-Path -LiteralPath $tokenPath) {
    $encrypted = Get-Content -LiteralPath $tokenPath -Raw
    $secureToken = ConvertTo-SecureString $encrypted
} elseif ($existingHealth) {
    throw "A bridge is already running, but its saved token is missing at $tokenPath. Stop it before creating a new token."
} else {
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $bytes = [byte[]]::new(32)
        $random.GetBytes($bytes)
        $token = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    } finally {
        $random.Dispose()
    }
    $secureToken = ConvertTo-SecureString -String $token -AsPlainText -Force
    $encrypted = ConvertFrom-SecureString $secureToken
    [System.IO.File]::WriteAllText($tokenPath, $encrypted, [System.Text.Encoding]::ASCII)
    $newToken = $true
}

$pointer = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureToken)
try {
    $token = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
} finally {
    [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
}

if ($existingHealth) {
    Write-Host "BrainSuite bridge is already running at http://127.0.0.1:$Port"
    Write-Host "Docker image ready: $($existingHealth.ready)"
    if ($ShowToken) {
        Write-Host "Access token: $token"
    }
    return
}

$python = Get-Command python -ErrorAction Stop
$stdoutPath = Join-Path $stateRoot 'brainsuite-bridge.stdout.log'
$stderrPath = Join-Path $stateRoot 'brainsuite-bridge.stderr.log'
$pidPath = Join-Path $stateRoot 'brainsuite-bridge.pid'
$variables = @{
    NEUROVIEW_BRAINSUITE_TOKEN = $token
    NEUROVIEW_BRAINSUITE_DATA = $dataRoot
    NEUROVIEW_BRAINSUITE_PORT = [string]$Port
}
$previous = @{}
foreach ($name in $variables.Keys) {
    $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    [Environment]::SetEnvironmentVariable($name, $variables[$name], 'Process')
}

try {
    $process = Start-Process -FilePath $python.Source `
        -ArgumentList ('-u "{0}"' -f $serverPath) `
        -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
}

$deadline = (Get-Date).AddSeconds(15)
$health = $null
do {
    if ($process.HasExited) {
        throw "Bridge exited during startup. Check $stderrPath"
    }
    try {
        $candidate = Invoke-RestMethod -Uri $healthUri -TimeoutSec 2
        if ($candidate.service -eq $serviceName) {
            $health = $candidate
            break
        }
    } catch {
        # The server may still be binding its loopback socket.
    }
    Start-Sleep -Milliseconds 250
} while ((Get-Date) -lt $deadline)

if (-not $health) {
    throw "Bridge did not become available. Check $stderrPath"
}
[System.IO.File]::WriteAllText($pidPath, [string]$process.Id, [System.Text.Encoding]::ASCII)

Write-Host "BrainSuite bridge started (PID $($process.Id)) at http://127.0.0.1:$Port"
Write-Host "Docker image ready: $($health.ready)"
Write-Host "Job files: $dataRoot"
if ($newToken -or $ShowToken) {
    Write-Host "Access token: $token"
}
