# stop-local.ps1 — Dừng toàn bộ stack PM Daily (GIỮ lại dữ liệu trong volume pgdata)
# Cách dùng:  powershell -ExecutionPolicy Bypass -File scripts/stop-local.ps1

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host '[stop-local] docker compose down (giu du lieu volume)...'
docker compose down

Write-Host '[stop-local] Hoan tat. Du lieu vẫn nam trong volume pmdaily_pgdata.'
Write-Host '  Khoi dong lai : scripts/start-local.ps1'
Write-Host '  Reset toan bo: scripts/reset-local.ps1'
