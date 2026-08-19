# smoke-test.ps1 — Smoke test E2E qua Nginx proxy (luồng trình duyệt thật)
# Kiểm tra: health, login sai -> 401, login admin, me, dashboard, users, roles,
# reports, audit-logs, notifications. Yêu cầu stack đang chạy (scripts/start-local.ps1).
# Cách dùng:  powershell -ExecutionPolicy Bypass -File scripts/smoke-test.ps1

$ErrorActionPreference = 'Continue'
$BASE = 'http://localhost:4200'
$API = "$BASE/api/v1"
$USER = 'admin'
$PASS = 'Admin@123'
$passCount = 0
$failCount = 0

function Get-ContentText($response) {
    if ($response.Content -is [byte[]]) {
        return [System.Text.Encoding]::UTF8.GetString($response.Content)
    }
    return [string]$response.Content
}

function Check($name, $scriptBlock) {
    try {
        $null = & $scriptBlock
        Write-Host "  [PASS] $name" -ForegroundColor Green
        $script:passCount++
    } catch {
        $code = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { $_.Exception.Message }
        Write-Host "  [FAIL] $name -> $code" -ForegroundColor Red
        $script:failCount++
    }
}

Write-Host '== PM Daily Smoke Test E2E ==' -ForegroundColor Cyan

# 1. Frontend SPA
Check 'GET / (SPA)' { Invoke-WebRequest -Uri $BASE -UseBasicParsing -TimeoutSec 15 | Out-Null }

# 2. Backend health
Check 'GET /actuator/health' {
    $h = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 10
    if ((Get-ContentText $h) -notmatch 'UP') { throw "health not UP: $(Get-ContentText $h)" }
}

# 3. Login sai -> 401
Check 'POST /auth/login (sai pass -> 401)' {
    $bad = @{ username = $USER; password = 'wrong-pass' } | ConvertTo-Json
    try {
        Invoke-WebRequest -Uri "$API/auth/login" -Method POST -ContentType 'application/json' -Body $bad -UseBasicParsing -TimeoutSec 15 | Out-Null
        throw 'expected 401'
    } catch {
        $s = [int]$_.Exception.Response.StatusCode
        if ($s -ne 401) { throw "got $s" }
    }
}

# 4. Login admin -> token
$token = $null
Check 'POST /auth/login (admin)' {
    $body = @{ username = $USER; password = $PASS } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$API/auth/login" -Method POST -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 15
    $j = Get-ContentText $r | ConvertFrom-Json
    if (-not $j.accessToken) { throw 'no accessToken' }
    if ($j.user.roles -notcontains 'ADMIN') { throw 'not ADMIN' }
    $script:token = $j.accessToken
}

if (-not $token) {
    Write-Host '  -> Login that bai, dung smoke test.' -ForegroundColor Red
    Write-Host "Tong ket: $passCount PASS / $failCount FAIL"
    exit 1
}
$H = @{ Authorization = "Bearer $token" }

# 5. me
Check 'GET /auth/me' { Invoke-WebRequest -Uri "$API/auth/me" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }

# 6. Dashboard
Check 'GET /dashboard/summary' {
    $r = Invoke-WebRequest -Uri "$API/dashboard/summary" -Headers $H -UseBasicParsing -TimeoutSec 15
    $j = Get-ContentText $r | ConvertFrom-Json
    if ($null -eq $j.tasksToday -and $null -eq $j.totalTasksToday) { throw 'summary empty' }
}

# 7. Module chính
Check 'GET /users?size=2' { Invoke-WebRequest -Uri "$API/users?size=2" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }
Check 'GET /roles' { Invoke-WebRequest -Uri "$API/roles" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }
Check 'GET /projects' { Invoke-WebRequest -Uri "$API/projects" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }
Check 'GET /meetings' { Invoke-WebRequest -Uri "$API/meetings" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }
Check 'GET /reports/project-progress' { Invoke-WebRequest -Uri "$API/reports/project-progress" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }
Check 'GET /audit-logs?size=2' { Invoke-WebRequest -Uri "$API/audit-logs?size=2" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }
Check 'GET /notifications/unread-count' { Invoke-WebRequest -Uri "$API/notifications/unread-count" -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null }

# 8. Swagger
Check 'GET /v3/api-docs' { Invoke-WebRequest -Uri 'http://localhost:8080/v3/api-docs' -UseBasicParsing -TimeoutSec 15 | Out-Null }

Write-Host ''
Write-Host "Tong ket: $passCount PASS / $failCount FAIL" -ForegroundColor Cyan
if ($failCount -gt 0) { exit 1 }
Write-Host 'Smoke test OK.' -ForegroundColor Green
