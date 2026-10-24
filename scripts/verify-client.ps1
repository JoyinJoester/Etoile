param([switch]$PublicRelease)

$ErrorActionPreference = 'Stop'
$workspace = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $workspace
try {
    # Source checks only: no assemble, bundle, install, signing or publication.
    & cargo test --locked --manifest-path native/code-reader/Cargo.toml
    if ($LASTEXITCODE -ne 0) { throw 'Rust tests failed.' }
    & node --test server/github-auth/server.test.mjs
    if ($LASTEXITCODE -ne 0) { throw 'Authorization service tests failed.' }
    & python -m unittest discover -s scripts -p 'test_verify_settings_device.py'
    if ($LASTEXITCODE -ne 0) { throw 'Device report parser tests failed.' }
    $gradleWrapper = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
    $verificationTasks = @(
        ':app:compileDebugKotlin', ':app:compileReleaseKotlin',
        ':app:compileDebugAndroidTestKotlin',
        ':app:testDebugUnitTest', ':app:testReleaseUnitTest',
        ':app:lintDebug', ':app:lintRelease', ':app:verifyReleaseSigningConfiguration',
        ':app:verifyReleaseAuthenticationConfiguration',
        ':app:writeReleaseDependencyInventory'
    )
    if ($PublicRelease) { $verificationTasks += ':app:verifyPublicReleaseConfiguration' }
    & $gradleWrapper @verificationTasks --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Android verification failed; inspect the task reports.' }
    & git diff --check
    if ($LASTEXITCODE -ne 0) { throw 'Whitespace validation failed.' }
} finally {
    Pop-Location
}
