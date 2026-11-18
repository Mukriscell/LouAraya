[CmdletBinding()]
param(
    [int]$IdentityPort = 8081,
    [int]$VerificationPort = 18082,
    [string]$DemoPassword = 'LouDemo2026!',
    [string]$TallerJar = (Join-Path $PSScriptRoot 'taller/target/taller-1.0.0.jar')
)

$ErrorActionPreference = 'Stop'
$backend = $PSScriptRoot
if (-not (Test-Path -LiteralPath $TallerJar -PathType Leaf)) { throw 'Compila Taller con Maven antes de ejecutar la verificación.' }
$jar = (Resolve-Path -LiteralPath $TallerJar).Path
$envLine = Get-Content -LiteralPath (Join-Path $backend '.env') |
    Where-Object { $_ -like 'LOUARAYA_DB_PASSWORD=*' } | Select-Object -First 1
if ($envLine -notmatch '^LOUARAYA_DB_PASSWORD=([A-Za-z0-9_-]{16,})$') { throw 'Falta configurar backend/.env.' }

$previousPassword = $env:LOUARAYA_DB_PASSWORD
$env:LOUARAYA_DB_PASSWORD = $Matches[1]
$database = 'louaraya_verify_' + [guid]::NewGuid().ToString('N').Substring(0, 12)
$dbUser = if ($env:LOUARAYA_DB_USER) { $env:LOUARAYA_DB_USER } else { 'louaraya' }
$dbPort = if ($env:LOUARAYA_DB_PORT) { [int]$env:LOUARAYA_DB_PORT } else { 54329 }
$identity = "http://127.0.0.1:$IdentityPort"
$taller = "http://127.0.0.1:$VerificationPort"
$java = (Get-Command java -ErrorAction Stop).Source
$logs = New-Item -ItemType Directory -Path (Join-Path $backend ".tools/$database") -Force

function Send-Json([string]$url, [string]$method, [hashtable]$body, [hashtable]$headers = @{}) {
    $request = @{Uri=$url; Method=$method; Headers=$headers; ContentType='application/json'; TimeoutSec=15}
    $request.Body = $body | ConvertTo-Json -Depth 8 -Compress
    Invoke-RestMethod @request
}

function Assert-True([bool]$test, [string]$description) {
    if (-not $test) { throw "Falló la validación: $description" }
}

function Expect-Status([int]$status, [scriptblock]$action) {
    try {
        & $action | Out-Null
    } catch {
        if ([int]$_.Exception.Response.StatusCode -eq $status) { return }
        throw
    }
    throw "La petición debió devolver HTTP $status."
}

function Start-VerificationTaller {
    $javaArgs = '-Xms24m -Xmx224m -XX:MaxMetaspaceSize=160m -XX:ActiveProcessorCount=2 -jar "' + $jar + '"' +
        " --server.address=127.0.0.1 --server.port=$VerificationPort" +
        " --spring.datasource.url=jdbc:postgresql://127.0.0.1:$dbPort/${database}?currentSchema=taller" +
        " --louaraya.identity-url=$identity"
    $attempt = [guid]::NewGuid().ToString('N').Substring(0, 8)
    $start = @{
        FilePath = $java
        ArgumentList = $javaArgs
        WorkingDirectory = $backend
        WindowStyle = 'Hidden'
        PassThru = $true
        RedirectStandardOutput = Join-Path $logs "$attempt.out.log"
        RedirectStandardError = Join-Path $logs "$attempt.err.log"
    }
    $service = Start-Process @start
    $deadline = [DateTime]::UtcNow.AddSeconds(90)
    while (-not $service.HasExited -and [DateTime]::UtcNow -lt $deadline) {
        try {
            if ((Invoke-RestMethod "$taller/actuator/health" -TimeoutSec 3).status -eq 'UP') { return $service }
        } catch {
            Start-Sleep -Milliseconds 500
        }
    }
    if (-not $service.HasExited) { $service.Kill(); $service.WaitForExit() }
    throw "La instancia temporal de Taller no inició. Revisa $logs."
}

$process = $null
$databaseCreated = $false
$sessions = [System.Collections.Generic.List[hashtable]]::new()
try {
    $check = [Net.Sockets.TcpClient]::new()
    try {
        try { $check.Connect('127.0.0.1', $VerificationPort) } catch [Net.Sockets.SocketException] {}
        if ($check.Connected) { throw "Ya hay un servicio usando el puerto $VerificationPort." }
    } finally { $check.Dispose() }

    docker compose --project-directory $backend exec -T postgres createdb -U $dbUser $database
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo crear la base temporal PostgreSQL.' }
    $databaseCreated = $true

    foreach ($alias in @('admin.demo', 'cliente.demo', 'cliente2.demo')) {
        $session = Send-Json "$identity/auth/login" Post @{alias=$alias;password=$DemoPassword}
        $sessions.Add(@{Authorization=('Bearer ' + $session.token)})
    }
    $admin = $sessions[0]
    $client = $sessions[1]
    $other = $sessions[2]
    $process = Start-VerificationTaller
    $firstProcessId = $process.Id

    $draft = @{name='Pieza de verificación';widthCm=20;lengthCm=40;material='Algodón';price=25000;campaignCategory='WEDDINGS'}
    Expect-Status 403 { Send-Json "$taller/products" Post $draft $client }
    $new = Send-Json "$taller/products" Post $draft $admin
    $product = "$taller/products/$($new.id)"
    Assert-True ((Invoke-RestMethod $product -TimeoutSec 5).price -eq 25000) 'consulta del producto creado'
    $updated = Send-Json $product Patch @{price=27000} $admin
    Assert-True ($updated.price -eq 27000) 'edición del precio remoto'

    $order = @{productId=$new.id;displayedPrice=27000;quantity=2;intention='PURCHASE';idempotencyKey=$database}
    $saved = Send-Json "$taller/requests" Post $order $client
    $replay = Send-Json "$taller/requests" Post $order $client
    Assert-True ($saved.id -eq $replay.id -and $saved.total -eq 54000) 'total del servidor y reintento idempotente'
    $requestUrl = "$taller/requests/$($saved.id)"
    Expect-Status 404 { Invoke-RestMethod $requestUrl -Headers $other -TimeoutSec 5 }
    Expect-Status 409 { Send-Json "$requestUrl/status" Patch @{status='DELIVERED'} $admin }
    $movementBody = @{quantity=4;type='ENTRY';idempotencyKey=$database}
    $movement = Send-Json "$product/stock-movements" Post $movementBody $admin
    $movementReplay = Send-Json "$product/stock-movements" Post $movementBody $admin
    Assert-True ($movement.stockAfter -eq 4 -and $movement.id -eq $movementReplay.id) 'entrada idempotente de bodega'
    $null = Send-Json "$requestUrl/status" Patch @{status='CONFIRMED'} $admin
    $null = Send-Json "$requestUrl/status" Patch @{status='DELIVERED'} $admin
    $null = Send-Json "$requestUrl/status" Patch @{status='DELIVERED'} $admin
    $null = Invoke-RestMethod $product -Method Delete -Headers $admin -TimeoutSec 10
    Expect-Status 404 { Invoke-RestMethod $product -TimeoutSec 5 }

    $process.Kill()
    $process.WaitForExit()
    $process = Start-VerificationTaller
    $after = Invoke-RestMethod $requestUrl -Headers $client -TimeoutSec 10
    $stored = Invoke-RestMethod $product -Headers $admin -TimeoutSec 10
    $snapshot = Invoke-RestMethod "$taller/snapshot" -Headers $admin -TimeoutSec 10
    $sale = @($snapshot.stockMovements | Where-Object { $_.requestId -eq $saved.id })
    Assert-True ($after.status -eq 'DELIVERED' -and $after.total -eq 54000) 'estado e importe persistidos tras reiniciar'
    Assert-True ($stored.price -eq 27000 -and $stored.stock -eq 2 -and -not $stored.active) 'precio, saldo y baja persistidos'
    Assert-True ($sale.Count -eq 1 -and $sale[0].quantityDelta -eq -2) 'hubo una sola salida de dos unidades'
    Write-Output "PostgreSQL verificado. Producto $($stored.id), precio 27000, stock 2, baja lógica. Solicitud $($after.id) DELIVERED por 54000."
    Write-Output "CRUD, roles, reintentos y venta única aprobados. Taller reinició del proceso $firstProcessId al $($process.Id)."
} finally {
    if ($process -and -not $process.HasExited) { $process.Kill(); $process.WaitForExit() }
    foreach ($headers in $sessions) {
        try { $null = Invoke-RestMethod "$identity/auth/logout" -Method Post -Headers $headers -TimeoutSec 5 } catch { }
    }
    if ($databaseCreated) {
        docker compose --project-directory $backend exec -T postgres dropdb -U $dbUser $database
        if ($LASTEXITCODE -ne 0) { Write-Warning "No se pudo eliminar la base temporal $database." }
    }
    $env:LOUARAYA_DB_PASSWORD = $previousPassword
}
