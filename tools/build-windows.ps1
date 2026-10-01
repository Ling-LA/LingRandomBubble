# Scope: this PowerShell process and this source directory only.
# No admin access, no device changes, no disabling security, no uploads.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$transcribing = $false
try {
    Start-Transcript -Path (Join-Path $root 'build.log') -Force | Out-Null
    $transcribing = $true
    Write-Host 'Ling Random Bubble 0.1.49 - Android debug build' -ForegroundColor Cyan
    Write-Host 'Requires JDK 17/21 and an installed Android SDK. No APK has been prebuilt in this archive.'
    $jdkCandidates = @($env:JAVA_HOME)
    if ($env:ProgramFiles) { $jdkCandidates += (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr') }
    if ($env:LOCALAPPDATA) { $jdkCandidates += (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr') }
    $jc = Get-Command javac.exe -ErrorAction SilentlyContinue
    if ($jc) { $jdkCandidates += (Split-Path -Parent (Split-Path -Parent $jc.Source)) }
    $jdk = $null
    foreach ($p in $jdkCandidates) {
        if ($p -and (Test-Path (Join-Path $p 'bin\javac.exe'))) { $jdk = $p; break }
    }
    if (-not $jdk) { throw 'JDK not found. Install Android Studio or JDK 17/21, then set JAVA_HOME.' }
    $env:JAVA_HOME = $jdk
    $env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path
    & (Join-Path $jdk 'bin\javac.exe') -version
    if ($LASTEXITCODE -ne 0) { throw 'javac cannot run.' }
    $sdkCandidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
    if ($env:LOCALAPPDATA) { $sdkCandidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
    $sdk = $null
    foreach ($p in $sdkCandidates) {
        if ($p -and (Test-Path (Join-Path $p 'platforms\android-35\android.jar'))) { $sdk = $p; break }
    }
    if (-not $sdk) {
        throw 'Android SDK API 35 not found. In Android Studio > SDK Manager install Android 15 / API 35 and Android SDK Build-Tools 35.0.0; then rerun.'
    }
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    Write-Host ('JDK: ' + $jdk)
    Write-Host ('SDK: ' + $sdk)
    $tools = Join-Path $root '.tools'
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $gradle = Join-Path $tools 'gradle-8.9\bin\gradle.bat'
    $zip = Join-Path $tools 'gradle-8.9-bin.zip'
    $sha = 'd725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab'
    if (-not (Test-Path $gradle)) {
        if (-not (Test-Path $zip)) {
            Write-Host 'Downloading Gradle 8.9 from services.gradle.org (about 130 MB)...'
            [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
            Invoke-WebRequest -UseBasicParsing -Uri 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile ($zip + '.part')
            Move-Item -Force ($zip + '.part') $zip
        }
        $actual = (Get-FileHash -Algorithm SHA256 $zip).Hash.ToLowerInvariant()
        if ($actual -ne $sha) { throw 'Gradle SHA-256 mismatch. Delete only .tools\gradle-8.9-bin.zip and try again.' }
        Expand-Archive -Path $zip -DestinationPath $tools -Force
    }
    Write-Host 'Running core policy tests...'
    & (Join-Path $PSScriptRoot 'test-core.ps1')
    if ($LASTEXITCODE -ne 0) { throw 'Core tests failed.' }
    Write-Host 'Compiling Android APK and running Android Lint (first run downloads build dependencies)...'
    & $gradle --no-daemon --console=plain --stacktrace '-Dorg.gradle.project.android.overridePathCheck=true' :app:assembleDebug :app:lintDebug
    if ($LASTEXITCODE -ne 0) { throw 'Android build/Lint failed. The full output is saved in build.log.' }
    $apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $apk)) { throw 'Build ended without an APK. Do not install a placeholder file.' }
    $out = Join-Path $root 'out'
    New-Item -ItemType Directory -Force -Path $out | Out-Null
    $target = Join-Path $out 'LingRandomBubble-0.1.49-debug.apk'
    Copy-Item -Force $apk $target
    $sum = (Get-FileHash -Algorithm SHA256 $target).Hash.ToLowerInvariant()
    Set-Content -Path (Join-Path $out 'SHA256SUMS.txt') -Encoding ascii -Value ($sum + '  LingRandomBubble-0.1.49-debug.apk')
    Write-Host ('APK created: ' + $target) -ForegroundColor Green
    Write-Host 'QQ -> Settings -> Modules -> Ling Random Bubble. Companion app is optional; timer and per-message modes default to off.'
    Write-Host 'Build success does not confirm device behavior. See docs/VALIDATION-2026-10-02-0.1.49.md for actual results.'
    if ($transcribing) { Stop-Transcript | Out-Null; $transcribing = $false }
    exit 0
} catch {
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    if ($transcribing) { Stop-Transcript | Out-Null }
    exit 1
}
