[CmdletBinding()]
param([switch]$SkipBuild, [switch]$WithPush)

$ErrorActionPreference = 'Stop'
$backend = $PSScriptRoot
$envPath = Join-Path $backend '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    $random = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($random)
    $password = [Convert]::ToHexString($random)
    Set-Content -LiteralPath $envPath -Value "LOUARAYA_DB_PASSWORD=$password" -Encoding ascii
}
$passwordLine = Get-Content -LiteralPath $envPath | Where-Object { $_ -like 'LOUARAYA_DB_PASSWORD=*' } | Select-Object -First 1
if ($passwordLine -notmatch '^LOUARAYA_DB_PASSWORD=([A-Za-z0-9_-]{16,})$') {
    throw 'Define LOUARAYA_DB_PASSWORD en backend/.env con al menos 16 letras, números, guiones o guiones bajos.'
}
$env:LOUARAYA_DB_PASSWORD = $Matches[1]
if (-not $env:LOUARAYA_DEMO_PASSWORD) { $env:LOUARAYA_DEMO_PASSWORD = 'LouDemo2026!' }
$env:LOUARAYA_BIND = '127.0.0.1'

$identityPort = if ($env:LOUARAYA_IDENTITY_PORT) { [int]$env:LOUARAYA_IDENTITY_PORT } else { 8081 }
$tallerPort = if ($env:LOUARAYA_TALLER_PORT) { [int]$env:LOUARAYA_TALLER_PORT } else { 8082 }
$ports = @($identityPort, $tallerPort)
if ($identityPort -eq $tallerPort) { throw 'Identidad y Taller deben usar puertos distintos.' }
foreach ($port in $ports) {
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "El puerto $port ya está ocupado. Configura otro o detén la instancia anterior."
    }
}
$env:LOUARAYA_IDENTITY_URL = "http://127.0.0.1:$identityPort"

function Assert-NativeSuccess([string]$task) {
    if ($LASTEXITCODE -ne 0) { throw "$task falló (código $LASTEXITCODE)." }
}

$composeArgs = @('compose', '--project-directory', $backend)
if ($WithPush) {
    $composeArgs += @('--profile', 'push')
    $env:LOUARAYA_PUSH_ORIGIN = 'http://10.0.2.2:8090'
    $env:LOUARAYA_PUSH_TRANSPORT = 'http://127.0.0.1:8090'
}
docker @composeArgs up -d --wait
Assert-NativeSuccess 'Inicio de los contenedores locales'

$java = (Get-Command java -ErrorAction Stop).Source
if (-not $SkipBuild) {
    Push-Location $backend
    try {
        if (-not $env:MAVEN_OPTS) {
            $env:MAVEN_OPTS = '-Xmx256m -XX:MaxMetaspaceSize=160m -XX:ActiveProcessorCount=2'
        }
        & .\mvnw.cmd -B -ntp -DskipTests package
        Assert-NativeSuccess 'Compilación de Spring Boot'
    } finally {
        Pop-Location
    }
}

function Start-ServiceJar([string]$module, [int]$port) {
    $jar = Join-Path $backend "$module/target/$module-1.0.0.jar"
    if (-not (Test-Path -LiteralPath $jar)) { throw "Falta compilar $module." }
    if (Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue) {
        throw "El puerto $port ya está ocupado. Configura otro para $module o detén la instancia anterior."
    }
    $javaArgs = '-Xms24m -Xmx256m -XX:MaxMetaspaceSize=160m -XX:ActiveProcessorCount=2 -jar "' + $jar + '"'
    Start-Process -FilePath $java -ArgumentList $javaArgs -WorkingDirectory $backend -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $backend "$module.out.log") `
        -RedirectStandardError (Join-Path $backend "$module.err.log")
}

$started = [System.Collections.Generic.List[System.Diagnostics.Process]]::new()
try {
    $started.Add((Start-ServiceJar 'identity' $identityPort))
    $started.Add((Start-ServiceJar 'taller' $tallerPort))
    for ($index = 0; $index -lt $ports.Count; $index++) {
        $port = $ports[$index]
        $service = $started[$index]
        $healthy = $false
        $deadline = [DateTime]::UtcNow.AddSeconds(60)
        while (-not $healthy -and -not $service.HasExited -and [DateTime]::UtcNow -lt $deadline) {
            try {
                $health = Invoke-RestMethod "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2
                $healthy = $health.status -eq 'UP'
            } catch {
                Start-Sleep -Milliseconds 500
            }
        }
        if (-not $healthy) { throw "El servicio en $port no inició. Revisa los archivos *.out.log y *.err.log." }
    }
} catch {
    $started | Where-Object { -not $_.HasExited } | Stop-Process
    throw
}

Write-Output "Identidad y Taller están activos en http://127.0.0.1:$identityPort y http://127.0.0.1:$tallerPort."
Write-Output "Procesos Java: $($started.Id -join ', '). Los registros están en backend/*.log."
