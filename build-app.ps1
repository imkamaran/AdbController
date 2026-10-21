if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk" }

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $scriptDir

Write-Host "Building ADB Controller..." -ForegroundColor Cyan
& .\gradlew.bat assembleDebug

if ($LASTEXITCODE -eq 0) {
    $apk = Get-ChildItem -Path "app\build\outputs\apk\debug\*.apk" -ErrorAction SilentlyContinue | Select-Object -First 1
    Write-Host "BUILD SUCCESSFUL: $($apk.FullName)" -ForegroundColor Green

    $adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
    if ($apk -and (Test-Path $adb)) {
        Write-Host "Installing..." -ForegroundColor Cyan
        & $adb install -r $apk.FullName
        if ($LASTEXITCODE -eq 0) {
            & $adb shell am start -n "com.adbcontroller/.MainActivity"
        }
    }
} else {
    Write-Host "BUILD FAILED" -ForegroundColor Red
}

Pop-Location
