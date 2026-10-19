[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,

    [ValidateRange(1, 100)]
    [int]$Samples = 10,

    [ValidateRange(0, 120)]
    [int]$FirstGlDrawTimeoutSeconds = 15,

    [string]$ApplicationId = 'com.marcusfunt.neuroview',

    [string]$SdkRoot = $(if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { 'D:\Android\Sdk' }),

    [string]$ApkPath = 'app/build/outputs/apk/debug/app-debug.apk',

    [string]$SourceCommit,

    [string]$OutputCsv = 'app/build/reports/performance/home-startup.csv'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$adb = Join-Path $SdkRoot 'platform-tools/adb.exe'
if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb was not found at '$adb'. Pass -SdkRoot with the Android SDK path."
}

function Invoke-Adb {
    param([string[]]$Arguments)
    $result = & $script:adb -s $script:Serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "adb $($Arguments -join ' ') failed: $($result -join ' ')"
    }
    return @($result)
}

$connected = & $adb devices
if ($LASTEXITCODE -ne 0 -or -not ($connected | Where-Object { $_ -match "^$([regex]::Escape($Serial))\s+device(?:\s|$)" })) {
    throw "Android device '$Serial' is not connected and ready."
}

$installedPackages = Invoke-Adb -Arguments @('shell', 'pm', 'list', 'packages', $ApplicationId)
if (-not ($installedPackages -match "^package:$([regex]::Escape($ApplicationId))$")) {
    throw "Application '$ApplicationId' is not installed on '$Serial'. Install a build on a dedicated QA device first."
}

$component = "$ApplicationId/.MainActivity"
$device = [ordered]@{
    Manufacturer = ((Invoke-Adb -Arguments @('shell', 'getprop', 'ro.product.manufacturer')) -join '').Trim()
    Model = ((Invoke-Adb -Arguments @('shell', 'getprop', 'ro.product.model')) -join '').Trim()
    AndroidVersion = ((Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.release')) -join '').Trim()
    Api = ((Invoke-Adb -Arguments @('shell', 'getprop', 'ro.build.version.sdk')) -join '').Trim()
    RamKb = ((Invoke-Adb -Arguments @('shell', 'grep', 'MemTotal', '/proc/meminfo')) -join '').Trim()
    Display = ((Invoke-Adb -Arguments @('shell', 'wm', 'size')) -join '').Trim()
    Density = ((Invoke-Adb -Arguments @('shell', 'wm', 'density')) -join '').Trim()
    FontScale = ((Invoke-Adb -Arguments @('shell', 'settings', 'get', 'system', 'font_scale')) -join '').Trim()
    RefreshHz = ''
}
$displayDump = (Invoke-Adb -Arguments @('shell', 'dumpsys', 'display')) -join "`n"
$refreshMatch = [regex]::Match($displayDump, '(?:renderFrameRate\s+|fps=)([\d.]+)')
if ($refreshMatch.Success) { $device.RefreshHz = $refreshMatch.Groups[1].Value }

$resolvedSourceCommit = if ($SourceCommit) {
    $SourceCommit
} else {
    (& git -C $repoRoot rev-parse HEAD 2>$null | Out-String).Trim()
}
$resolvedApk = if ([IO.Path]::IsPathRooted($ApkPath)) { $ApkPath } else { Join-Path $repoRoot $ApkPath }
$apkSha256 = if (Test-Path -LiteralPath $resolvedApk) {
    (Get-FileHash -LiteralPath $resolvedApk -Algorithm SHA256).Hash.ToLowerInvariant()
} else {
    ''
}
$signingCertificateSha256 = ''
if ($apkSha256) {
    $apksigner = Get-ChildItem -LiteralPath (Join-Path $SdkRoot 'build-tools') -Directory -ErrorAction SilentlyContinue |
        Sort-Object { try { [version]$_.Name } catch { [version]'0.0' } } -Descending |
        ForEach-Object { Join-Path $_.FullName 'apksigner.bat' } |
        Where-Object { Test-Path -LiteralPath $_ } |
        Select-Object -First 1
    if ($apksigner -and (Test-Path -LiteralPath $apksigner)) {
        $signerOutput = & $apksigner verify --print-certs $resolvedApk 2>&1
        if ($LASTEXITCODE -eq 0) {
            $signerMatch = [regex]::Match(($signerOutput -join "`n"), '(?im)^Signer #\d+ certificate SHA-256 digest:\s*([0-9a-f:]+)\s*$')
            if ($signerMatch.Success) {
                $signingCertificateSha256 = $signerMatch.Groups[1].Value.Replace(':', '').ToLowerInvariant()
            }
        }
    }
}

$rows = for ($sample = 1; $sample -le $Samples; $sample++) {
    # Force-stop restarts the process but deliberately preserves app data.
    $null = Invoke-Adb -Arguments @('shell', 'am', 'force-stop', $ApplicationId)
    $startUptimeText = ((Invoke-Adb -Arguments @('shell', 'cat', '/proc/uptime')) -join '').Trim()
    $startUptimeSeconds = [double](($startUptimeText -split '\s+')[0])
    $launchOutput = Invoke-Adb -Arguments @('shell', 'am', 'start', '-W', '-n', $component)
    $launchText = $launchOutput -join "`n"
    $totalTimeMatch = [regex]::Match($launchText, '(?m)^TotalTime:\s*(\d+)\s*$')
    if (-not $totalTimeMatch.Success) {
        throw "am start -W did not return TotalTime for sample $sample`: $launchText"
    }
    $totalTimeMs = [int]$totalTimeMatch.Groups[1].Value

    $firstGlDrawMs = $null
    $drawDeadline = (Get-Date).AddSeconds($FirstGlDrawTimeoutSeconds)
    do {
        $logLines = Invoke-Adb -Arguments @('logcat', '-d', '-v', 'monotonic', '-s', 'NeuroViewBrain:I', '*:S')
        $drawLine = $logLines | Where-Object { $_ -match 'First GL draw' } | Select-Object -Last 1
        if ($drawLine -and $drawLine -match '^\s*\[?\s*(\d+\.\d+)') {
            $drawUptimeSeconds = [double]$Matches[1]
            if ($drawUptimeSeconds -ge $startUptimeSeconds) {
                $firstGlDrawMs = [math]::Round(($drawUptimeSeconds - $startUptimeSeconds) * 1000)
            }
        }
        if ($null -eq $firstGlDrawMs -and (Get-Date) -lt $drawDeadline) { Start-Sleep -Seconds 1 }
    } while ($null -eq $firstGlDrawMs -and (Get-Date) -lt $drawDeadline)

    [pscustomobject]@{
        Sample = $sample
        TotalTimeMs = $totalTimeMs
        FirstGlDrawMs = $firstGlDrawMs
        SourceCommit = $resolvedSourceCommit
        ApkSha256 = $apkSha256
        SigningCertificateSha256 = $signingCertificateSha256
        Serial = $Serial
        Manufacturer = $device.Manufacturer
        Model = $device.Model
        AndroidVersion = $device.AndroidVersion
        Api = $device.Api
        RamKb = $device.RamKb
        Display = $device.Display
        Density = $device.Density
        FontScale = $device.FontScale
        RefreshHz = $device.RefreshHz
    }
}

function Get-Median {
    param([double[]]$Values)
    $ordered = @($Values | Sort-Object)
    if ($ordered.Count -eq 0) { return $null }
    $middle = [int][math]::Floor($ordered.Count / 2)
    if (($ordered.Count % 2) -eq 1) { return $ordered[$middle] }
    return ($ordered[$middle - 1] + $ordered[$middle]) / 2
}

$outputPath = if ([IO.Path]::IsPathRooted($OutputCsv)) { $OutputCsv } else { Join-Path $repoRoot $OutputCsv }
$outputDirectory = Split-Path -Parent $outputPath
New-Item -ItemType Directory -Force -Path $outputDirectory | Out-Null
$rows | Export-Csv -LiteralPath $outputPath -NoTypeInformation -Encoding utf8

"Saved $Samples samples to $outputPath"
"Device: $($device.Manufacturer) $($device.Model), Android $($device.AndroidVersion) / API $($device.Api), $($device.Display), density $($device.Density)"
"Refresh rate: $(if ($device.RefreshHz) { $device.RefreshHz + ' Hz' } else { 'unavailable' })"
"Source commit: $(if ($resolvedSourceCommit) { $resolvedSourceCommit } else { 'unavailable' })"
"APK SHA-256: $(if ($apkSha256) { $apkSha256 } else { 'not found at ' + $resolvedApk })"
"Signing certificate SHA-256: $(if ($signingCertificateSha256) { $signingCertificateSha256 } else { 'unavailable' })"
$glDrawTimes = @($rows | Where-Object { $null -ne $_.FirstGlDrawMs } | ForEach-Object { [double]$_.FirstGlDrawMs })
"TotalTimeMs median: $(Get-Median -Values $rows.TotalTimeMs); FirstGlDrawMs median: $(Get-Median -Values $glDrawTimes)"
"This captures process-start and first-GL-draw proxies only. It does not measure first usable interaction, touch latency, jank, memory, thermal behavior, or physical-device performance."
