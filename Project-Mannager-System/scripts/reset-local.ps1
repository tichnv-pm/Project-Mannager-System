# reset-local.ps1 — Xóa dữ liệu (volume) và dựng lại từ đầu (migration V1+V2 tự chạy)
# Cảnh báo: XÓA TOÀN BỘ dữ liệu local trong volume pgdata!
# Cách dùng:  powershell -ExecutionPolicy Bypass -File scripts/reset-local.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host '[reset-local] CANH BAO: se xoa toan bo du lieu (volume pgdata) va dung lai tu dau.' -ForegroundColor Yellow
$ans = Read-Host 'Nhap "RESET" de xac nhan'
if ($ans -ne 'RESET') {
    Write-Host '[reset-local] Huy bo.' -ForegroundColor Yellow
    exit 0
}

Write-Host '[reset-local] docker compose down -v ...'
docker compose down -v
if ($LASTEXITCODE -ne 0) { Write-Error '[reset-local] down -v that bai.' }

Write-Host '[reset-local] docker compose up -d --build ...'
docker compose up -d --build
if ($LASTEXITCODE -ne 0) { Write-Error '[reset-local] up that bai.' }

Write-Host '[reset-local] Cho backend healthy (toi da 120s)...'
$ok = $false
for ($i = 0; $i -lt 24; $i++) {
    Start-Sleep -Seconds 5
    try {
        $h = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 5
        if ($h.Content -match 'UP') { $ok = $true; break }
    } catch { }
}
if (-not $ok) { Write-Warning '[reset-local] Backend chua UP sau 120s - xem: docker compose logs backend' }

Write-Host ''
Write-Host '[reset-local] Xong. Schema + seed (V1/V2) da tao lai.' -ForegroundColor Green
Write-Host '  Frontend : http://localhost:4200  (admin / Admin@123)'
