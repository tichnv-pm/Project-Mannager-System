# start-local.ps1 — Khởi động toàn bộ stack PM Daily qua Docker Compose
# Cách dùng:  powershell -ExecutionPolicy Bypass -File scripts/start-local.ps1
# Yêu cầu: Docker Desktop daemon đang chạy; file .env có JWT_SECRET.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path '.env')) {
    Write-Host '[start-local] Chua co .env - tao tu .env.example (nho sua JWT_SECRET)!' -ForegroundColor Yellow
    Copy-Item '.env.example' '.env'
    Write-Host '[start-local] Da tao .env. SUA JWT_SECRET truoc khi chay lai.' -ForegroundColor Yellow
    exit 1
}

Write-Host '[start-local] Kiem tra Docker daemon...'
$up = docker version --format "{{.Server.Version}}" 2>$null
if (-not $up) {
    Write-Host '[start-local] Docker daemon chua chay. Dang khoi dong Docker Desktop...' -ForegroundColor Yellow
    $dd = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
    if (Test-Path $dd) { Start-Process $dd }
    for ($i = 0; $i -lt 30; $i++) {
        Start-Sleep -Seconds 5
        if (docker version --format "{{.Server.Version}}" 2>$null) { break }
    }
    if (-not (docker version --format "{{.Server.Version}}" 2>$null)) {
        Write-Error '[start-local] Docker daemon khong san sang sau 150s. Khoi dong Docker Desktop thu cong roi chay lai.'
    }
    Write-Host '[start-local] Docker daemon san sang.' -ForegroundColor Green
}

Write-Host '[start-local] docker compose up -d --build ...'
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Write-Error '[start-local] compose up that bai.' }

Write-Host '[start-local] Cho backend healthy (toi da 120s)...'
$ok = $false
for ($i = 0; $i -lt 24; $i++) {
    Start-Sleep -Seconds 5
    try {
        $h = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 5
        if ($h.Content -match 'UP') { $ok = $true; break }
    } catch { }
}
if (-not $ok) { Write-Warning '[start-local] Backend chua UP sau 120s - xem: docker compose logs backend' }

Write-Host ''
Write-Host '[start-local] Hoan tat.' -ForegroundColor Green
Write-Host '  Frontend : http://localhost:4200  (admin / Admin@123)'
Write-Host '  Swagger  : http://localhost:8080/swagger-ui/index.html'
Write-Host '  Stop     : scripts/stop-local.ps1'
Write-Host '  Reset DB : scripts/reset-local.ps1'
Write-Host '  Smoke    : scripts/smoke-test.ps1'
