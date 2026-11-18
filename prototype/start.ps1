$ErrorActionPreference = 'Stop'

$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (!(Test-Path -LiteralPath $adb)) {
    $adb = (Get-Command adb.exe -ErrorAction Stop).Source
}

$managedPython = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe'
$python = if (Test-Path -LiteralPath $managedPython) {
    $managedPython
} else {
    (Get-Command python.exe -ErrorAction Stop).Source
}

Write-Host 'Conecta el teléfono por USB, activa la depuración y acepta la autorización en Android.'
$devices = @(& $adb devices | ForEach-Object {
    if ($_ -match '^([^\s]+)\s+device$') { $Matches[1] }
})
if ($devices.Count -eq 0) {
    Write-Host 'ADB aún no detecta un dispositivo autorizado. Podrás ejecutar adb reverse tcp:8765 tcp:8765 en otra terminal cuando lo conectes.'
}
foreach ($serial in $devices) {
    & $adb -s $serial reverse tcp:8765 tcp:8765
    if ($LASTEXITCODE -ne 0) { throw "ADB no pudo configurar la conexión con $serial." }
}

Write-Host 'En el APK usa localhost. Mantén esta ventana abierta durante la prueba.'
& $python (Join-Path $PSScriptRoot 'server.py')
exit $LASTEXITCODE
