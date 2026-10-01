<#
.SYNOPSIS
    Unit test suite for scripts/setup-performance.ps1.
.DESCRIPTION
    Validates that setup-performance.ps1 correctly handles all edge cases and error conditions:
    1. Rejection of non-existent CSV.
    2. Rejection of 0-byte empty CSV.
    3. Rejection of CSV missing required columns.
    4. Rejection of malformed numeric data.
    5. Rejection of PID mismatch.
    6. Rejection of SwapChain mismatch.
    7. Rejection of empty per-segment samples.
    8. Successful calculation of nearest-rank p95, sample counts, and dropped counts on valid data.
#>
[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$scriptUnderTesting = Join-Path $scriptDir 'setup-performance.ps1'

if (-not (Test-Path -LiteralPath $scriptUnderTesting)) {
    throw "Script under test not found: $scriptUnderTesting"
}

$tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("lti-perf-test-" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tempDir -Force | Out-Null

$testResults = [System.Collections.Generic.List[PSObject]]::new()

function Assert-Test {
    param(
        [string]$TestName,
        [scriptblock]$Block,
        [string]$ExpectedErrorPattern = $null
    )

    $passed = $false
    $errorMessage = ""

    try {
        $result = & $Block
        if (-not [string]::IsNullOrEmpty($ExpectedErrorPattern)) {
            $passed = $false
            $errorMessage = "Expected error matching '$ExpectedErrorPattern', but script succeeded."
        } else {
            $passed = $true
        }
    } catch {
        $actualError = $_.Exception.Message
        if (-not [string]::IsNullOrEmpty($ExpectedErrorPattern)) {
            if ($actualError -match $ExpectedErrorPattern) {
                $passed = $true
            } else {
                $passed = $false
                $errorMessage = "Error '$actualError' did not match expected pattern '$ExpectedErrorPattern'."
            }
        } else {
            $passed = $false
            $errorMessage = "Unexpected error: $actualError"
        }
    }

    $statusStr = $(if ($passed) { "PASS" } else { "FAIL" })
    $statusColor = $(if ($passed) { [System.ConsoleColor]::Green } else { [System.ConsoleColor]::Red })
    Write-Host "[$statusStr] $TestName" -ForegroundColor $statusColor
    if (-not $passed) {
        Write-Host "    Reason: $errorMessage" -ForegroundColor DarkRed
    }

    $testResults.Add([PSCustomObject]@{
        Name    = $TestName
        Passed  = $passed
        Message = $errorMessage
    })
}

try {
    # Prepare standard test marker file
    $markerFile = Join-Path $tempDir "test-markers.json"
    @{
        captureStart = 0.0
        captureEnd   = 30.0
        segments     = @(
            @{ name = "Workspaces";  start = 0.0;  end = 10.0 },
            @{ name = "Tools";       start = 10.0; end = 20.0 },
            @{ name = "Environment"; start = 20.0; end = 30.0 }
        )
    } | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $markerFile -Encoding utf8

    # Test 1: Non-existent CSV
    Assert-Test -TestName "Reject non-existent CSV" -ExpectedErrorPattern "CSV file not found" -Block {
        $nonExistent = Join-Path $tempDir "missing.csv"
        & $scriptUnderTesting -CsvPath $nonExistent -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
    }

    # Test 2: 0-byte empty CSV
    $emptyCsv = Join-Path $tempDir "empty.csv"
    New-Item -ItemType File -Path $emptyCsv -Force | Out-Null
    Assert-Test -TestName "Reject empty (0-byte) CSV" -ExpectedErrorPattern "CSV file is empty" -Block {
        & $scriptUnderTesting -CsvPath $emptyCsv -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
    }

    # Test 3: CSV missing required columns
    $missingColCsv = Join-Path $tempDir "missing-cols.csv"
    @"
ProcessID,SwapChainAddress,TimeInSeconds
1234,0x12345678,1.0
"@ | Set-Content -LiteralPath $missingColCsv -Encoding utf8
    Assert-Test -TestName "Reject CSV missing required columns" -ExpectedErrorPattern "CSV missing required column\(s\): MsBetweenPresents" -Block {
        & $scriptUnderTesting -CsvPath $missingColCsv -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
    }

    # Test 4: Malformed numeric data in CSV
    $malformedCsv = Join-Path $tempDir "malformed-data.csv"
    @"
ProcessID,SwapChainAddress,TimeInSeconds,MsBetweenPresents,Dropped
1234,0x12345678,0.5,16.2,0
1234,0x12345678,INVALID_NUM,16.5,0
"@ | Set-Content -LiteralPath $malformedCsv -Encoding utf8
    Assert-Test -TestName "Reject malformed numeric data" -ExpectedErrorPattern "Malformed numeric data" -Block {
        & $scriptUnderTesting -CsvPath $malformedCsv -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
    }

    # Test 5: PID mismatch
    $pidMismatchCsv = Join-Path $tempDir "pid-mismatch.csv"
    @"
ProcessID,SwapChainAddress,TimeInSeconds,MsBetweenPresents,Dropped
9999,0x12345678,1.0,16.2,0
9999,0x12345678,2.0,15.8,0
"@ | Set-Content -LiteralPath $pidMismatchCsv -Encoding utf8
    Assert-Test -TestName "Reject PID mismatch" -ExpectedErrorPattern "PID mismatch: expected PID 1234" -Block {
        & $scriptUnderTesting -CsvPath $pidMismatchCsv -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
    }

    # Test 6: SwapChain mismatch
    $swapMismatchCsv = Join-Path $tempDir "swap-mismatch.csv"
    @"
ProcessID,SwapChainAddress,TimeInSeconds,MsBetweenPresents,Dropped
1234,0xDEADBEEF,1.0,16.2,0
1234,0xDEADBEEF,2.0,15.8,0
"@ | Set-Content -LiteralPath $swapMismatchCsv -Encoding utf8
    Assert-Test -TestName "Reject SwapChain mismatch" -ExpectedErrorPattern "Swap-chain mismatch" -Block {
        & $scriptUnderTesting -CsvPath $swapMismatchCsv -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
    }

    # Test 7: Empty per-segment samples
    # In this CSV, we have samples for [0, 10] (Workspaces) and [20, 30] (Environment), but none for [10, 20] (Tools)
    $emptySegCsv = Join-Path $tempDir "empty-segment.csv"
    @"
ProcessID,SwapChainAddress,TimeInSeconds,MsBetweenPresents,Dropped
1234,0x12345678,2.0,16.0,0
1234,0x12345678,5.0,16.1,0
1234,0x12345678,25.0,15.9,0
"@ | Set-Content -LiteralPath $emptySegCsv -Encoding utf8
    Assert-Test -TestName "Reject empty per-segment samples" -ExpectedErrorPattern "Empty samples for segment 'Tools'" -Block {
        & $scriptUnderTesting -CsvPath $emptySegCsv -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
    }

    # Test 8: Valid capture verification and p95 nearest-rank accuracy
    $validCsv = Join-Path $tempDir "valid-capture.csv"
    $rows = [System.Collections.Generic.List[string]]::new()
    $rows.Add("ProcessID,SwapChainAddress,TimeInSeconds,MsBetweenPresents,Dropped")

    # Generate 100 samples per segment (300 total)
    # Segment 1: Workspaces (t=0..10) -> ms values around 14.0 - 16.0, 1 dropped
    for ($i = 0; $i -lt 100; $i++) {
        $t = [Math]::Round(0.1 + ($i * 0.098), 3)
        $ms = 14.0 + ($i * 0.02) # 14.0 to 15.98
        $dropped = $(if ($i -eq 99) { 1 } else { 0 })
        $rows.Add("1234,0x12345678,$t,$ms,$dropped")
    }

    # Segment 2: Tools (t=10..20) -> ms values around 15.0 - 16.5, 2 dropped
    for ($i = 0; $i -lt 100; $i++) {
        $t = [Math]::Round(10.1 + ($i * 0.098), 3)
        $ms = 15.0 + ($i * 0.015) # 15.0 to 16.485
        $dropped = $(if ($i -ge 98) { 1 } else { 0 })
        $rows.Add("1234,0x12345678,$t,$ms,$dropped")
    }

    # Segment 3: Environment (t=20..30) -> ms values around 13.0 - 16.2, 0 dropped
    for ($i = 0; $i -lt 100; $i++) {
        $t = [Math]::Round(20.1 + ($i * 0.098), 3)
        $ms = 13.0 + ($i * 0.032) # 13.0 to 16.168
        $dropped = 0
        $rows.Add("1234,0x12345678,$t,$ms,$dropped")
    }

    $rows | Set-Content -LiteralPath $validCsv -Encoding utf8

    Assert-Test -TestName "Verify valid capture nearest-rank p95 and dropped counts" -Block {
        $res = & $scriptUnderTesting -CsvPath $validCsv -ExpectedProcessId 1234 -SwapChainId "0x12345678" -MarkerFilePath $markerFile
        if (-not $res.OverallPass) {
            throw "Expected OverallPass to be true."
        }
        if ($res.WholeCapture.SampleCount -ne 300) {
            throw "Expected 300 samples, got $($res.WholeCapture.SampleCount)"
        }
        if ($res.WholeCapture.DroppedCount -ne 3) {
            throw "Expected 3 dropped frames, got $($res.WholeCapture.DroppedCount)"
        }
        # Nearest rank p95 for 100 samples: ceil(0.95 * 100) - 1 = index 94 (95th item in 0-indexed array)
        $ws = $res.Segments | Where-Object { $_.SegmentName -eq 'Workspaces' }
        if ($ws.SampleCount -ne 100) { throw "Expected 100 Workspaces samples" }
        if ($ws.DroppedCount -ne 1) { throw "Expected 1 Workspaces dropped frame" }
        # 14.0 + (94 * 0.02) = 14.0 + 1.88 = 15.88
        if ([Math]::Abs($ws.P95Ms - 15.88) -gt 0.01) {
            throw "Expected Workspaces p95 around 15.88 ms, got $($ws.P95Ms)"
        }
        $true
    }

} finally {
    if (Test-Path -LiteralPath $tempDir) {
        Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}

$failedCount = @($testResults | Where-Object { -not $_.Passed }).Count
Write-Host ""
Write-Host "========================================"
Write-Host "Test Summary: $($testResults.Count) tests, $failedCount failures."
Write-Host "========================================"

if ($failedCount -gt 0) {
    exit 1
} else {
    exit 0
}
