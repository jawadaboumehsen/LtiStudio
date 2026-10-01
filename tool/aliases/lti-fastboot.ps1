$dir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ltiBin = Join-Path $dir "lti"
if (-not (Test-Path $ltiBin)) {
    $ltiBin = "lti"
}
& $ltiBin tools fastboot @args
exit $LASTEXITCODE
