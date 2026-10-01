<#
.SYNOPSIS
    Evaluates PresentMon release performance CSV captures against SC-004 criteria.
.DESCRIPTION
    Validates PresentMon CSV captures for LtiRomGui Setup Screen:
    - Accepts CSV path, expected PID, main-window swap-chain identifier, and marker file.
    - Validates required columns, numeric data integrity, and non-empty per-segment samples.
    - Excludes only initial pre-capture-start interval and out-of-marker intervals.
    - Preserves navigation intervals inside the whole-capture result.
    - Never trims slow frames as outliers.
    - Computes nearest-rank p95: index = ceil(0.95 * N) - 1 on sorted MsBetweenPresents.
    - Reports sample count, dropped frame count, and nearest-rank p95 per segment and whole capture.
    - Enforces SC-004 target: p95 <= 16.7 ms.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [Alias('Csv', 'Path')]
    [string]$CsvPath,

    [Parameter(Mandatory = $true, Position = 1)]
    [Alias('Pid', 'ExpectedPid', 'ProcessId')]
    [int]$ExpectedProcessId,

    [Parameter(Mandatory = $true, Position = 2)]
    [Alias('SwapChain', 'SwapChainAddress')]
    [string]$SwapChainId,

    [Parameter(Mandatory = $true, Position = 3)]
    [Alias('MarkerPath', 'MarkerFile', 'Markers')]
    [string]$MarkerFilePath,

    [Parameter(Mandatory = $false)]
    [double]$TargetP95Ms = 16.7
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-NearestRankP95 {
    param(
        [Parameter(Mandatory = $true)]
        [double[]]$Values
    )
    if ($null -eq $Values -or $Values.Count -eq 0) {
        throw "Cannot compute p95 on empty sample set."
    }
    $sorted = $Values | Sort-Object
    $N = $sorted.Count
    $index = [int][Math]::Ceiling(0.95 * $N) - 1
    if ($index -lt 0) { $index = 0 }
    if ($index -ge $N) { $index = $N - 1 }
    return [Math]::Round($sorted[$index], 3)
}

# 1. Validate File Existence and Non-Empty
if (-not (Test-Path -LiteralPath $CsvPath)) {
    throw "CSV file not found: '$CsvPath'"
}
$csvFileItem = Get-Item -LiteralPath $CsvPath
if ($csvFileItem.Length -eq 0) {
    throw "CSV file is empty: '$CsvPath'"
}

if (-not (Test-Path -LiteralPath $MarkerFilePath)) {
    throw "Marker file not found: '$MarkerFilePath'"
}

# 2. Parse Marker File
$markerRaw = Get-Content -LiteralPath $MarkerFilePath -Raw
try {
    $markerData = $markerRaw | ConvertFrom-Json
} catch {
    throw "Marker file is not valid JSON: $_"
}

if ($null -eq $markerData.segments -or $markerData.segments.Count -eq 0) {
    throw "Marker file contains no valid segments definition."
}

# 3. Read and Validate CSV Headers
$rawRows = @(Import-Csv -LiteralPath $CsvPath)
if ($null -eq $rawRows -or $rawRows.Count -eq 0) {
    throw "CSV contains no data rows: '$CsvPath'"
}

$sampleRow = $rawRows[0]
$properties = $sampleRow.PSObject.Properties.Name

function Find-ColumnName {
    param([string[]]$Candidates, [string[]]$Available)
    foreach ($cand in $Candidates) {
        $found = $Available | Where-Object { $_ -ieq $cand }
        if ($found) { return $found }
    }
    return $null
}

$colPid = Find-ColumnName -Candidates @('ProcessID', 'ProcessId', 'Process_Id', 'PID') -Available $properties
$colSwapChain = Find-ColumnName -Candidates @('SwapChainAddress', 'SwapChain', 'SwapChain_Address') -Available $properties
$colMsBetween = Find-ColumnName -Candidates @('MsBetweenPresents', 'msBetweenPresents', 'Ms_Between_Presents', 'FrameTime') -Available $properties
$colTimeInSec = Find-ColumnName -Candidates @('TimeInSeconds', 'timeInSeconds', 'Time_In_Seconds', 'Time') -Available $properties
$colDropped = Find-ColumnName -Candidates @('Dropped', 'dropped', 'IsDropped') -Available $properties

$missingCols = @()
if (-not $colPid) { $missingCols += 'ProcessID' }
if (-not $colSwapChain) { $missingCols += 'SwapChainAddress' }
if (-not $colMsBetween) { $missingCols += 'MsBetweenPresents' }
if (-not $colTimeInSec) { $missingCols += 'TimeInSeconds' }

if ($missingCols.Count -gt 0) {
    throw "CSV missing required column(s): $($missingCols -join ', ')"
}

# 4. Filter by Expected PID and SwapChainId
$pidRows = @($rawRows | Where-Object {
    $val = $_.$colPid
    [int]$val -eq $ExpectedProcessId
})

if ($null -eq $pidRows -or $pidRows.Count -eq 0) {
    $foundPids = (($rawRows | ForEach-Object { $_.$colPid } | Select-Object -Unique) -join ', ')
    throw "PID mismatch: expected PID $ExpectedProcessId, but CSV only contains PID(s): [$foundPids]"
}

$swapChainRows = @($pidRows | Where-Object {
    $val = $_.$colSwapChain
    if ($val -ieq $SwapChainId) { return $true }
    # Also support prefix or hex normalized matching (e.g. 0x00000... vs 0x...)
    $norm1 = ($val -replace '^0x0*', '0x').ToLower()
    $norm2 = ($SwapChainId -replace '^0x0*', '0x').ToLower()
    return ($norm1 -eq $norm2)
})

if ($null -eq $swapChainRows -or $swapChainRows.Count -eq 0) {
    $foundChains = ($pidRows | ForEach-Object { $_.$colSwapChain } | Select-Object -Unique) -join ', '
    throw "Swap-chain mismatch: expected SwapChain '$SwapChainId', but found: [$foundChains]"
}

# 5. Numeric Data Validation
$parsedSamples = [System.Collections.Generic.List[PSObject]]::new()
$rowIdx = 1

foreach ($row in $swapChainRows) {
    $rowIdx++
    $timeRaw = $row.$colTimeInSec
    $msRaw = $row.$colMsBetween

    $timeVal = 0.0
    $msVal = 0.0

    if (-not [double]::TryParse($timeRaw, [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$timeVal)) {
        throw "Malformed numeric data in row ${rowIdx}: column '$colTimeInSec' value '$timeRaw' is not a valid number."
    }
    if (-not [double]::TryParse($msRaw, [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$msVal)) {
        throw "Malformed numeric data in row ${rowIdx}: column '$colMsBetween' value '$msRaw' is not a valid number."
    }

    $isDropped = 0
    if ($colDropped -and ($row.$colDropped)) {
        $dropVal = ($row.$colDropped).ToString().Trim().ToLower()
        if ($dropVal -eq '1' -or $dropVal -eq 'true') {
            $isDropped = 1
        }
    }

    $parsedSamples.Add([PSCustomObject]@{
        TimeInSeconds     = $timeVal
        MsBetweenPresents = $msVal
        Dropped           = $isDropped
    })
}

if ($parsedSamples.Count -eq 0) {
    throw "No valid numeric samples found for PID $ExpectedProcessId and SwapChain $SwapChainId."
}

# 6. Marker Alignment & Interval Exclusion
$csvMinTime = ($parsedSamples | Measure-Object -Property TimeInSeconds -Minimum).Minimum
$csvMaxTime = ($parsedSamples | Measure-Object -Property TimeInSeconds -Maximum).Maximum

# Determine if markers use relative seconds or absolute timestamps
$markerCaptureStart = if ($markerData.captureStart -ne $null) { [double]$markerData.captureStart } else { 0.0 }
$markerCaptureEnd = if ($markerData.captureEnd -ne $null) { [double]$markerData.captureEnd } else { 30.0 }

$timeOffset = 0.0
if ($markerCaptureStart -lt 1.0 -and $csvMinTime -ge 1.0) {
    # Relative marker offsets aligned to CSV capture start
    $timeOffset = $csvMinTime
}

$effectiveCaptureStart = $markerCaptureStart + $timeOffset
$effectiveCaptureEnd = $markerCaptureEnd + $timeOffset

# Exclude initial pre-capture-start interval and out-of-marker intervals
$activeCaptureSamples = @($parsedSamples | Where-Object {
    $_.TimeInSeconds -ge $effectiveCaptureStart -and $_.TimeInSeconds -le $effectiveCaptureEnd
})

if ($null -eq $activeCaptureSamples -or $activeCaptureSamples.Count -eq 0) {
    throw "No samples found within active capture window [$effectiveCaptureStart, $effectiveCaptureEnd]. CSV times: [$csvMinTime, $csvMaxTime]."
}

# 7. Evaluate Segments and Verify Non-Empty Per-Segment Samples
$segmentResults = [System.Collections.Generic.List[PSObject]]::new()

foreach ($seg in $markerData.segments) {
    $segName = $seg.name
    $segStart = [double]$seg.start + $timeOffset
    $segEnd = [double]$seg.end + $timeOffset

    $segSamples = @($activeCaptureSamples | Where-Object {
        $_.TimeInSeconds -ge $segStart -and $_.TimeInSeconds -le $segEnd
    })

    if ($null -eq $segSamples -or $segSamples.Count -eq 0) {
        throw "Empty samples for segment '$segName': no records found between $segStart and $segEnd seconds."
    }

    $msValues = [double[]]($segSamples | ForEach-Object { $_.MsBetweenPresents })
    $segP95 = Get-NearestRankP95 -Values $msValues
    $segDropped = @($segSamples | Where-Object { $_.Dropped -eq 1 }).Count
    $segPass = ($segP95 -le $TargetP95Ms)

    $segmentResults.Add([PSCustomObject]@{
        SegmentName  = $segName
        SampleCount  = $segSamples.Count
        DroppedCount = $segDropped
        P95Ms        = $segP95
        Pass         = $segPass
        Status       = $(if ($segPass) { 'PASS' } else { 'FAIL' })
    })
}

# 8. Compute Whole Active Capture p95
$wholeMsValues = [double[]]($activeCaptureSamples | ForEach-Object { $_.MsBetweenPresents })
$wholeP95 = Get-NearestRankP95 -Values $wholeMsValues
$wholeDropped = @($activeCaptureSamples | Where-Object { $_.Dropped -eq 1 }).Count
$wholePass = ($wholeP95 -le $TargetP95Ms)

$wholeResult = [PSCustomObject]@{
    SegmentName  = 'Whole Capture'
    SampleCount  = $activeCaptureSamples.Count
    DroppedCount = $wholeDropped
    P95Ms        = $wholeP95
    Pass         = $wholePass
    Status       = $(if ($wholePass) { 'PASS' } else { 'FAIL' })
}

# 9. Output Formatted Report
Write-Host ""
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "           LTI ROM GUI SETUP PERFORMANCE AUDIT (SC-004)" -ForegroundColor Cyan
Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host "CSV Path        : $CsvPath"
Write-Host "Expected PID    : $ExpectedProcessId (Matched)"
Write-Host "Swap Chain ID   : $SwapChainId (Matched)"
Write-Host "Marker File     : $MarkerFilePath"
Write-Host "Capture Window  : [$effectiveCaptureStart s to $effectiveCaptureEnd s]"
Write-Host "Target p95 SLO  : <= $TargetP95Ms ms"
Write-Host "--------------------------------------------------------------------------------"

$allResults = @($segmentResults) + @($wholeResult)

$formatString = "{0,-16} | {1,10} | {2,10} | {3,10} | {4,8}"
Write-Host ($formatString -f "Segment", "Samples (N)", "Dropped", "p95 (ms)", "SC-004") -ForegroundColor Yellow
Write-Host ("-" * 66)

foreach ($res in $allResults) {
    $color = if ($res.Pass) { [System.ConsoleColor]::Green } else { [System.ConsoleColor]::Red }
    $line = $formatString -f $res.SegmentName, $res.SampleCount, $res.DroppedCount, ("{0:F2}" -f $res.P95Ms), $res.Status
    Write-Host $line -ForegroundColor $color
}

Write-Host "================================================================================" -ForegroundColor Cyan
Write-Host ""

$outputObj = [PSCustomObject]@{
    CsvPath         = $CsvPath
    ProcessId       = $ExpectedProcessId
    SwapChainId     = $SwapChainId
    TargetP95Ms     = $TargetP95Ms
    WholeCapture    = $wholeResult
    Segments        = $segmentResults
    OverallPass     = $wholePass -and (-not ($segmentResults | Where-Object { -not $_.Pass }))
}

return $outputObj
