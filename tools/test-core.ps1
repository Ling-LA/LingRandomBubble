$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    New-Item -ItemType Directory -Force -Path 'out\core-tests' | Out-Null
    $sources = @(Get-ChildItem 'app\src\main\java\io\github\ling\randombubble\core','tests\fixtures' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    $sources += (Join-Path $root 'tests\CoreSelfTest.java')
    # Direct arguments preserve paths with spaces and Chinese characters.
    & javac --release 8 -encoding UTF-8 -d 'out\core-tests' $sources
    if ($LASTEXITCODE -ne 0) { throw 'Core test javac failed.' }
    & java -ea -cp 'out\core-tests' CoreSelfTest
    if ($LASTEXITCODE -ne 0) { throw 'Core test execution failed.' }
} finally { Pop-Location }
