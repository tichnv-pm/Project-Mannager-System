# integration-planning-sweep.ps1 - Test tich hop live-stack: du lieu mau tich hop v1.1
# Pham vi: auth, projects, plans (WBS/gantt/critical-path/recalc/baseline/versions),
#          plan links (AC-LINK-03 write-test), milestones/risks/issues cua PRJ-AGILE (303),
#          templates, calendars, portfolio summary.
# Yeu cau: stack dang chay (docker compose up -d --build).
# Cach dung: powershell -ExecutionPolicy Bypass -File scripts/integration-planning-sweep.ps1

$ErrorActionPreference = 'Continue'
$API = 'http://localhost:8080/api/v1'
$USER = 'pm.minh'
$PASS = 'Pm@12345'
$PRJ_AGILE = '00000000-0000-0000-0000-000000000303'
$PLAN_MASTER = '00000000-0000-0000-0000-000000000b01'
$TASK_C04 = '00000000-0000-0000-0000-000000000c04'   # TSK-102 Auth Service (critical)
$TASK_C09 = '00000000-0000-0000-0000-000000000c09'   # TSK-202 Payment Gateway
$MS_303 = '00000000-0000-0000-0000-000000001004'     # Release 1.0 MVP AgriCorp
$ISS_303 = '00000000-0000-0000-0000-000000000902'    # Mat phien gio hang Redis
$ISS_301 = '00000000-0000-0000-0000-000000000901'    # (cross-project - phai bi chan)
$passCount = 0
$failCount = 0
$details = @()

function Get-Text($response) {
    if ($response.Content -is [byte[]]) { return [System.Text.Encoding]::UTF8.GetString($response.Content) }
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
        $script:details += "$name -> $code"
    }
}

function Get-Json($uri, $headers) {
    $r = Invoke-WebRequest -Uri $uri -Headers $headers -UseBasicParsing -TimeoutSec 20
    return (Get-Text $r | ConvertFrom-Json)
}

Write-Host '== PM Daily Integration Sweep (v1.1 planning + du lieu tich hop) ==' -ForegroundColor Cyan

# 1. Health
Check 'GET /actuator/health' {
    $h = Get-Text (Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 10)
    if ($h -notmatch 'UP') { throw "health not UP: $h" }
}

# 2. Login pm.minh (PROJECT_MANAGER)
$token = $null
Check 'POST /auth/login (pm.minh)' {
    $body = @{ username = $USER; password = $PASS } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$API/auth/login" -Method POST -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 15
    $j = Get-Text $r | ConvertFrom-Json
    if (-not $j.accessToken) { throw 'no accessToken' }
    $script:token = $j.accessToken
}
if (-not $token) { Write-Host 'Login that bai.' -ForegroundColor Red; exit 1 }
$H = @{ Authorization = "Bearer $token" }

# 3. Du an cua pm.minh (bao gom PRJ-AGILE 303)
Check 'GET /projects (myOnly, PM co 3 du an)' {
    $j = Get-Json "$API/projects?myOnly=true&size=20" $H
    if (($j.content | Measure-Object).Count -lt 3) { throw 'expect >=3 projects' }
    if (-not ($j.content | Where-Object { $_.id -eq $PRJ_AGILE })) { throw 'PRJ-AGILE missing' }
}

# 4. Plans cua PRJ-AGILE (master + 2 detail)
Check 'GET /plans?projectId=303 (3 plans)' {
    $j = Get-Json "$API/plans?projectId=$PRJ_AGILE&size=20" $H
    if (($j.content | Measure-Object).Count -ne 3) { throw "expect 3 plans, got $($j.content.Count)" }
    if (-not ($j.content | Where-Object { $_.id -eq $PLAN_MASTER -and $_.planType -eq 'MASTER' })) { throw 'MASTER plan missing' }
}

# 5. Chi tiet Master Plan
Check 'GET /plans/b01 (master detail)' {
    $j = Get-Json "$API/plans/$PLAN_MASTER" $H
    if ($j.planCode -ne 'PLN-AGILE-MASTER') { throw "wrong plan: $($j.planCode)" }
}

# 6. Gantt (tu dung SVG data: tasks + dependencies)
Check 'GET /plans/b01/gantt' {
    $j = Get-Json "$API/plans/$PLAN_MASTER/gantt" $H
    if (($j.tasks | Measure-Object).Count -lt 10) { throw 'gantt tasks < 10' }
    if (($j.dependencies | Measure-Object).Count -lt 6) { throw 'gantt deps < 6' }
}

# 7. Recalc (lich lam viec) — phai chay TRUOC critical-path
Check 'POST /plans/b01/recalc' {
    Invoke-WebRequest -Uri "$API/plans/$PLAN_MASTER/recalc" -Method POST -Headers $H -UseBasicParsing -TimeoutSec 20 | Out-Null
}

# 8. Critical path (toan bo chain TSK-101..TSK-202 + RELEASE 2.0 khi schedule khop)
Check 'GET /plans/b01/critical-path (5+ tasks critical)' {
    $j = Get-Json "$API/plans/$PLAN_MASTER/critical-path" $H
    if ($j.criticalTaskCount -lt 5) { throw "criticalTaskCount < 5 (got $($j.criticalTaskCount))" }
    if (-not ($j.tasks | Where-Object { $_.wbsCode -eq '1.1.2' -and $_.isCritical })) { throw 'TSK-102 not critical' }
}

# 9. Baseline & versions
Check 'GET /plans/b01/baselines (1 baseline)' {
    $j = Get-Json "$API/plans/$PLAN_MASTER/baselines" $H
    if (($j | Measure-Object).Count -ne 1) { throw 'baselines != 1' }
}
Check 'GET /plans/b01/versions (1 active)' {
    $j = Get-Json "$API/plans/$PLAN_MASTER/versions" $H
    if (($j | Measure-Object).Count -lt 1) { throw 'versions = 0' }
}

# 10. Link cua c04/c08 (sau V7: e31 issue 902 nam tren c08, e35 execution task 412 tren c04 - cung project 303)
Check 'GET /plans/b01/tasks/c04/links (execution task 412)' {
    $j = Get-Json "$API/plans/$PLAN_MASTER/tasks/$TASK_C04/links" $H
    if (($j | Measure-Object).Count -lt 1) { throw 'links < 1' }
    if (-not ($j | Where-Object { $_.targetId -eq '00000000-0000-0000-0000-000000000412' -and $_.targetType -eq 'EXECUTION_TASK' })) { throw 'execution task 412 missing' }
}
Check 'GET /plans/b01/tasks/c08/links (issue 902 cung project 303)' {
    $j = Get-Json "$API/plans/$PLAN_MASTER/tasks/$([guid]::Parse('00000000-0000-0000-0000-000000000c08'))/links" $H
    if (-not ($j | Where-Object { $_.targetId -eq $ISS_303 -and $_.targetType -eq 'ISSUE' })) { throw 'issue 902 link missing' }
}

# 11. AC-LINK-03 write-test: link cheo project PHAI bi chan (400)
Check 'POST link cheo project (301) -> 400 AC-LINK-03' {
    $body = @{ targetType = 'ISSUE'; targetId = $ISS_301; linkType = 'RELATED' } | ConvertTo-Json
    try {
        Invoke-WebRequest -Uri "$API/plans/$PLAN_MASTER/tasks/$TASK_C09/links" -Method POST -Headers $H -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 15 | Out-Null
        throw 'expected 400'
    } catch {
        $s = [int]$_.Exception.Response.StatusCode
        if ($s -ne 400) { throw "got $s" }
    }
}

# 12. Tao + xoa link hop le (cung project: milestone 1004) - kiem chung write-path OK sau V7
$newLinkId = $null
Check 'POST link milestone 1004 (cung project 303) -> 201' {
    $body = @{ targetType = 'MILESTONE'; targetId = $MS_303; linkType = 'RELATED' } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$API/plans/$PLAN_MASTER/tasks/$TASK_C09/links" -Method POST -Headers $H -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 15
    $j = Get-Text $r | ConvertFrom-Json
    if ($j.targetId -ne $MS_303) { throw 'wrong target' }
    $script:newLinkId = $j.id
}
if ($newLinkId) {
    Check 'DELETE link vua tao -> 204' {
        Invoke-WebRequest -Uri "$API/links/$newLinkId" -Method DELETE -Headers $H -UseBasicParsing -TimeoutSec 15 | Out-Null
    }
}

# 13. Milestones / risks / issues cua PRJ-AGILE (seed V7)
Check 'GET /milestones?projectId=303 (4 moc: 2 release + 2 sync baseline)' {
    $j = Get-Json "$API/milestones?projectId=$PRJ_AGILE&size=10" $H
    $ms = if ($j.content) { @($j.content) } else { @($j) }
    if ($ms.Count -ne 4) { throw "milestones 303 != 4 (got $($ms.Count))" }
}
Check 'GET /risks?projectId=303 (RSK000003)' {
    $j = Get-Json "$API/risks?projectId=$PRJ_AGILE&size=10" $H
    if (-not ($j.content | Where-Object { $_.code -eq 'RSK000003' })) { throw 'RSK000003 missing' }
}
Check 'GET /issues?projectId=303 (ISS000002)' {
    $j = Get-Json "$API/issues?projectId=$PRJ_AGILE&size=10" $H
    if (-not ($j.content | Where-Object { $_.code -eq 'ISS000002' })) { throw 'ISS000002 missing' }
}

# 14. Templates (8 built-in) & FULL_SDL 17 phases
Check 'GET /plan-templates (8 built-in)' {
    $j = Get-Json "$API/plan-templates?size=20" $H
    if (($j | Measure-Object).Count -ne 8) { throw "templates != 8 (got $($j.Count))" }
}
Check 'GET /plan-templates/FULL_SDL (17 phases)' {
    $j = Get-Json "$API/plan-templates/a1111111-1111-4111-a111-111111111111" $H
    if (($j.tasks | Measure-Object).Count -ne 17) { throw "FULL_SDL phases != 17 (got $($j.tasks.Count))" }
}

# 15. Lich lam viec (calendar seed V6)
Check 'GET /plan-calendars (co lich ACTIVE)' {
    $j = Get-Json "$API/plan-calendars?size=10" $H
    if (($j | Measure-Object).Count -lt 1) { throw 'no calendar' }
}

# 16. Portfolio summary (tong hop tu master plan - phai co 303 + milestones)
Check 'GET /portfolio (summary co PRJ-AGILE + milestone)' {
    $j = Get-Json "$API/portfolio" $H
    if ($j.totalProjects -lt 3) { throw "totalProjects < 3 (got $($j.totalProjects))" }
    if (-not ($j.projects | Where-Object { $_.id -eq $PRJ_AGILE -and $_.status -eq 'APPROVED' })) { throw '303 not in portfolio with master' }
    if (-not ($j.upcomingMilestones | Where-Object { $_.projectId -eq $PRJ_AGILE })) { throw 'no milestone from 303' }
}

# 17. Tai nguyen & workload (resource seed V6)
Check 'GET /plans/b01/resources' {
    $j = Get-Json "$API/plans/$PLAN_MASTER/resources" $H
    if (($j | Measure-Object).Count -lt 3) { throw 'resources < 3' }
}

Write-Host ''
Write-Host "Tong ket: $passCount PASS / $failCount FAIL" -ForegroundColor Cyan
if ($details.Count) { Write-Host ('FAIL: ' + ($details -join '; ')) -ForegroundColor Red }
if ($failCount -gt 0) { exit 1 }
Write-Host 'Integration sweep OK.' -ForegroundColor Green