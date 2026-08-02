param(
    [switch]$SkipTests
)

$ErrorActionPreference = 'Stop'

$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$androidStudioJava = 'C:\Program Files\Android\Android Studio\jbr'
if (Test-Path -LiteralPath $androidStudioJava) {
    $env:JAVA_HOME = $androidStudioJava
}

$availableDrive = @('T', 'U', 'V', 'W', 'X') |
    Where-Object { -not (Get-PSDrive -Name $_ -ErrorAction SilentlyContinue) } |
    Select-Object -First 1

if (-not $availableDrive) {
    throw 'No temporary drive letter is available for the build.'
}

$mappedDrive = "${availableDrive}:"
& subst.exe $mappedDrive $projectRoot
if ($LASTEXITCODE -ne 0) {
    throw 'Could not create the temporary build drive.'
}

try {
    Push-Location "${mappedDrive}\"
    try {
        $tasks = if ($SkipTests) {
            @('assembleDebug')
        } else {
            @('testDebugUnitTest', 'assembleDebug')
        }

        & .\gradlew.bat $tasks --no-configuration-cache
        $gradleExitCode = $LASTEXITCODE
    } finally {
        Pop-Location
    }

    if ($gradleExitCode -ne 0) {
        exit $gradleExitCode
    }

    $apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
    Write-Host "APK: $apkPath"
} finally {
    & subst.exe $mappedDrive /D
}
