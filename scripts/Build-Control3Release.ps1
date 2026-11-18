param(
    [Parameter(Mandatory)][uri]$IdentityUrl,
    [Parameter(Mandatory)][uri]$TallerUrl,
    [string]$EmulatorTlsResources,
    [switch]$IncludeDebugTests
)

$ErrorActionPreference = 'Stop'
if ($IdentityUrl.Scheme -ne 'https' -or $TallerUrl.Scheme -ne 'https') {
    throw 'Las URL del APK release deben usar HTTPS.'
}
if (-not $env:JAVA_HOME) { throw 'Configura JAVA_HOME con el JDK de Android Studio.' }
$repository = Split-Path -Parent $PSScriptRoot
$privateDirectory = Join-Path $repository 'output/control3/private'
$configuration = Join-Path $privateDirectory 'keystore.properties'
$keyStore = Join-Path $privateDirectory 'louaraya-control3.jks'
$keytool = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
$alias = 'louaraya-control3'

if ((Test-Path -LiteralPath $configuration) -ne (Test-Path -LiteralPath $keyStore)) {
    throw 'La llave académica y su configuración deben estar juntas en output/control3/private.'
}
if (-not (Test-Path -LiteralPath $configuration)) {
    New-Item -ItemType Directory -Path $privateDirectory -Force | Out-Null
    $password = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    try {
        $env:LOUARAYA_KEYTOOL_PASSWORD = $password
        & $keytool -genkeypair -keystore $keyStore -storetype JKS -alias $alias `
            -dname 'CN=LouAraya Control 3, O=Proyecto academico, C=CL' -validity 3650 `
            -keyalg RSA -keysize 3072 -storepass:env LOUARAYA_KEYTOOL_PASSWORD `
            -keypass:env LOUARAYA_KEYTOOL_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la llave académica.' }
        @(
            'storeFile=output/control3/private/louaraya-control3.jks'
            "storePassword=$password"
            "keyAlias=$alias"
            "keyPassword=$password"
        ) | Set-Content -LiteralPath $configuration -Encoding ascii
    } finally {
        [Environment]::SetEnvironmentVariable('LOUARAYA_KEYTOOL_PASSWORD', $null, 'Process')
    }
}

$gradleOptions = @(
    ':app:assembleRelease', '--console=plain', '--max-workers=1', '--no-daemon'
    '-Dorg.gradle.jvmargs=-Xmx1400m -Dfile.encoding=UTF-8'
    '-Pkotlin.compiler.execution.strategy=in-process'
    "-PlouArayaIdentityUrl=$IdentityUrl"
    "-PlouArayaTallerUrl=$TallerUrl"
)
if ($EmulatorTlsResources) { $gradleOptions += "-PlouArayaEmulatorTlsResources=$EmulatorTlsResources" }
if ($IncludeDebugTests) { $gradleOptions += ':app:assembleDebug', ':app:assembleDebugAndroidTest' }
Push-Location -LiteralPath $repository
try {
    & .\gradlew.bat @gradleOptions
    if ($LASTEXITCODE -ne 0) { throw 'Falló la compilación del APK release.' }
    $sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
    $apksigner = Get-ChildItem -LiteralPath (Join-Path $sdk 'build-tools') -Filter 'apksigner.bat' -File -Recurse |
        Sort-Object FullName -Descending | Select-Object -First 1
    if (-not $apksigner) { throw 'No se encontró apksigner en Android SDK Build Tools.' }
    $apk = Get-Item -LiteralPath 'app/build/outputs/apk/release/app-release.apk'
    & $apksigner.FullName verify --print-certs $apk.FullName
    if ($LASTEXITCODE -ne 0) { throw 'La firma de release no es válida.' }
    $delivery = Join-Path $repository 'output/control3/LouAraya-Control3.apk'
    Copy-Item -LiteralPath $apk.FullName -Destination $delivery -Force
    Get-FileHash -LiteralPath $delivery -Algorithm SHA256
} finally {
    Pop-Location
}
