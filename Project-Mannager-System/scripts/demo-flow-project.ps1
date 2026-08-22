# demo-flow-project.ps1 - Luong chuan: tao du an -> lap ke hoach (master/detail
# theo chuan FULL_SDL) -> phan bo nguon luc -> giao viec -> baseline -> moc
# baseline synchronized sang tinh nang Milestone (nhu task/meeting/risk/issue).
# Idempotent: neu du lieu da ton tai thi chi verify, khong tao lai.
# Yeu cau: stack dang chay (docker compose up -d --build).
# Cach dung: powershell -ExecutionPolicy Bypass -File scripts/demo-flow-project.ps1

$ErrorActionPreference = 'Continue'
$API = 'http://localhost:8080/api/v1'
$USER = 'pm.minh'
$PASS = 'Pm@12345'
$U_PM     = '00000000-0000-0000-0000-000000000002'   # pm.minh
$U_DEV1   = '00000000-0000-0000-0000-000000000003'   # member1
$U_TESTER = '00000000-0000-0000-0000-000000000004'   # member2
$U_DEV2   = '00000000-0000-0000-0000-000000000005'   # member3
$PRJ_CRM  = '00000000-0000-0000-0000-000000000304'
$CAL_ORG  = '00000000-0000-0000-0000-000000000a01'
$TPL_FULL_SDL = 'a1111111-1111-4111-a111-111111111111'
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

function Post-Json($uri, $headers, $body) {
    Invoke-WebRequest -Uri $uri -Method POST -Headers $headers -ContentType 'application/json' -Body ($body | ConvertTo-Json -Depth 6) -UseBasicParsing -TimeoutSec 20
}

Write-Host '== PM Daily Demo Flow: Du an -> Ke hoach (FULL_SDL) -> Nguon luc -> Giao viec -> Milestone ==' -ForegroundColor Cyan

# 0. Health
Check 'GET /actuator/health' {
    $h = Get-Text (Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health' -UseBasicParsing -TimeoutSec 10)
    if ($h -notmatch 'UP') { throw "health not UP: $h" }
}

# 1. Login pm.minh (PROJECT_MANAGER)
$token = $null
Check 'POST /auth/login (pm.minh)' {
    $body = @{ username = $USER; password = $PASS } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "$API/auth/login" -Method POST -ContentType 'application/json' -Body $body -UseBasicParsing -TimeoutSec 15
    $j = Get-Text $r | ConvertFrom-Json
    if (-not $j.accessToken) { throw 'no accessToken' }
    $script:token = $j.accessToken
}
$H = @{ Authorization = "Bearer $token" }

# 2. Buoc 1: Tao du an PRJ-CRM (neu chua co)
$projectId = $null
Check 'Buoc 1 - Tao du an PRJ-CRM (GlobeTel)' {
    $j = Get-Json "$API/projects?myOnly=true&size=50" $H
    $plist = if ($j.content) { @($j.content) } else { @($j) }
    $existing = $plist | Where-Object { $_.code -eq 'PRJ-CRM' } | Select-Object -First 1
    if ($existing) {
        $script:projectId = $existing.id
    } else {
        $body = @{
            code = 'PRJ-CRM'; name = 'He thong CRM Ban hang - GlobeTel'
            description = 'Du an demo luong chuan: tao du an -> ke hoach FULL_SDL -> nguon luc -> giao viec'
            status = 'ACTIVE'; startDate = '2026-09-01'; endDate = '2027-03-31'
            customerName = 'GlobeTel Viet Nam'; projectManagerId = $U_PM
        }
        $r = Post-Json "$API/projects" $H $body
        $script:projectId = (Get-Text $r | ConvertFrom-Json).id
    }
    if (-not $projectId) { throw 'no project id' }
}

# 3. Buoc 2a: Master plan tu template FULL_SDL (17 phases - chuan phat trien phan mem)
$masterId = $null
$masterTaskCount = 0
Check 'Buoc 2 - Master plan tu template FULL_SDL (17 phases)' {
    $j = Get-Json "$API/plans?projectId=$projectId&size=20" $H
    $list = if ($j.content) { $j.content } else { @($j) }
    $m = $list | Where-Object { $_.planCode -eq 'PF-CRM-MASTER' } | Select-Object -First 1
    if (-not $m) {
        $body = @{
            projectId = $projectId; templateId = $TPL_FULL_SDL
            planCode = 'PF-CRM-MASTER'
            planName = 'Master Plan: CRM theo chuan 17 phases phat trien phan mem (FULL_SDL)'
            planType = 'MASTER'; startDate = '2026-09-01'
        }
        $r = Post-Json "$API/plans/from-template" $H $body
        $script:masterId = (Get-Text $r | ConvertFrom-Json).id
    } else { $script:masterId = $m.id }
    if (-not $masterId) { throw 'no master plan' }
    $tasks = Get-Json "$API/plans/$masterId/tasks" $H
    $script:masterTaskCount = @($tasks).Count
    if ($masterTaskCount -lt 17) { throw "master tasks < 17 ($masterTaskCount)" }
    Write-Host "     -> Master $masterId, $masterTaskCount tasks (17 phases FULL_SDL)"
}

# 4. Buoc 2b: Detail plan (parent = master) + 5 task + dependency FS chain
$detailId = $null
$milestoneTaskId = $null
Check 'Buoc 2b - Detail plan PF-CRM-DEV (5 tasks + deps FS)' {
    $j = Get-Json "$API/plans?projectId=$projectId&size=20" $H
    $list = if ($j.content) { $j.content } else { @($j) }
    $d = $list | Where-Object { $_.planCode -eq 'PF-CRM-DEV' } | Select-Object -First 1
    if (-not $d) {
        $body = @{
            projectId = $projectId; planCode = 'PF-CRM-DEV'
            planName = 'Detail Plan: Phat trien CRM Core (giai doan DEVELOPMENT)'
            planType = 'DETAIL'; parentPlanId = $masterId
            calendarId = $CAL_ORG; plannedStart = '2026-10-13'
        }
        $r = Post-Json "$API/plans" $H $body
        $script:detailId = (Get-Text $r | ConvertFrom-Json).id
        $tasks = @(
            @{ taskCode = 'CRM-D1'; taskName = 'Khoi tao du lieu & Entity CRM'; taskType = 'TASK'; plannedEffortMinutes = 2400; ownerId = $U_DEV1; priority = 'HIGH' },
            @{ taskCode = 'CRM-D2'; taskName = 'API Auth/JWT & Phan quyen RBAC'; taskType = 'TASK'; plannedEffortMinutes = 2400; ownerId = $U_DEV2; priority = 'HIGH' },
            @{ taskCode = 'CRM-D3'; taskName = 'CRUD Khach hang & Don hang (API REST)'; taskType = 'TASK'; plannedEffortMinutes = 2880; ownerId = $U_DEV2; priority = 'HIGH' },
            @{ taskCode = 'CRM-D4'; taskName = 'Dashboard Bao cao & Xuat CSV'; taskType = 'TASK'; plannedEffortMinutes = 1920; ownerId = $U_TESTER; priority = 'HIGH' },
            @{ taskCode = 'CRM-D5'; taskName = 'MS-DEV-RW: Review & nghiem thu giai doan DEV'; taskType = 'MILESTONE'; plannedEffortMinutes = 0; ownerId = $U_PM; priority = 'HIGH' }
        )
        $createdIds = @()
        foreach ($t in $tasks) {
            $rr = Post-Json "$API/plans/$detailId/tasks" $H $t
            $createdIds += (Get-Text $rr | ConvertFrom-Json).id
        }
        if ($createdIds.Count -ne 5) { throw 'detail tasks < 5' }
        $deps = @(
            @{ successor = $createdIds[1]; pred = $createdIds[0] },
            @{ successor = $createdIds[2]; pred = $createdIds[1] },
            @{ successor = $createdIds[3]; pred = $createdIds[2] },
            @{ successor = $createdIds[4]; pred = $createdIds[3] }
        )
        foreach ($dp in $deps) {
            $null = Post-Json "$API/plans/$detailId/tasks/$($dp.successor)/dependencies" $H @{ predecessorTaskId = $dp.pred; dependencyType = 'FS'; lagMinutes = 0 }
        }
        $script:milestoneTaskId = $createdIds[4]
    } else {
        $script:detailId = $d.id
        $dt = Get-Json "$API/plans/$detailId/tasks" $H
        $mt = @($dt) | Where-Object { $_.taskCode -eq 'CRM-D5' } | Select-Object -First 1
        $script:milestoneTaskId = $mt.id
        if (-not $milestoneTaskId) { throw 'no milestone task CRM-D5' }
    }
    if (-not $detailId -or -not $milestoneTaskId) { throw 'detail plan incomplete' }
}

# 5. Buoc 3: Phan bo nguon luc (USER allocation tren detail plan)
Check 'Buoc 3 - Phan bo nguon luc (member1/2/3)' {
    $alloc = Get-Json "$API/plans/$detailId/resources" $H
    if (@($alloc).Count -lt 5) {
        $tasks = Get-Json "$API/plans/$detailId/tasks" $H
        $t = @{}
        @($tasks) | ForEach-Object { $t[$_.taskCode] = $_.id }
        $plan = @(
            @{ task = $t['CRM-D1']; resourceId = $U_DEV1;   pct = 60; role = 'Backend Dev' },
            @{ task = $t['CRM-D2']; resourceId = $U_DEV2;   pct = 70; role = 'Backend Dev' },
            @{ task = $t['CRM-D3']; resourceId = $U_DEV1;   pct = 50; role = 'Backend Dev' },
            @{ task = $t['CRM-D3']; resourceId = $U_DEV2;   pct = 50; role = 'Fullstack' },
            @{ task = $t['CRM-D4']; resourceId = $U_TESTER; pct = 60; role = 'QA/Tester' }
        )
        foreach ($p in $plan) {
            $null = Post-Json "$API/plans/$detailId/tasks/$($p.task)/resources" $H @{
                resourceType = 'USER'; resourceId = $p.resourceId; allocationPercent = $p.pct
                roleOnTask = $p.role; startDate = $null; endDate = $null; plannedEffortMinutes = $null
            }
        }
        $alloc = Get-Json "$API/plans/$detailId/resources" $H
    }
    if (@($alloc).Count -lt 5) { throw "allocations < 5 ($(@($alloc).Count))" }
}

# 6. Buoc 4: Giao viec (execution task + plan_links EXECUTION_TASK primary)
Check 'Buoc 4 - Giao viec (2 execution task + link primary)' {
    $tasks = Get-Json "$API/tasks?projectId=$projectId&size=50" $H
    $taskList = if ($tasks.content) { @($tasks.content) } else { @($tasks) }
    $need = $taskList | Where-Object { $_.code -eq 'PRJ-CRM-TASK-000001' } | Select-Object -First 1
    $need2 = $taskList | Where-Object { $_.code -eq 'PRJ-CRM-TASK-000002' } | Select-Object -First 1
    if (-not $need) {
        $body = @{
            projectId = $projectId; title = 'Phat trien API Auth & Phan quyen CRM'
            description = 'Trien khai CRM-D2 (API Auth/JWT + RBAC) theo detail plan PF-CRM-DEV'
            assigneeId = $U_DEV2; status = 'TODO'; priority = 'HIGH'; type = 'FEATURE'; source = 'MANUAL'
            startDate = '2026-10-21'; dueDate = '2026-10-28'; estimateMinutes = 2400
        }
        $need = Get-Text (Post-Json "$API/tasks" $H $body) | ConvertFrom-Json
    }
    if (-not $need2) {
        $body = @{
            projectId = $projectId; title = 'Kich ban + thuc thi kiem thu CRM'
            description = 'Viet kich ban va kiem thu Dashboard Bao cao theo CRM-D4'
            assigneeId = $U_TESTER; status = 'TODO'; priority = 'MEDIUM'; type = 'TASK'; source = 'MANUAL'
            startDate = '2026-11-04'; dueDate = '2026-11-09'; estimateMinutes = 1920
        }
        $need2 = Get-Text (Post-Json "$API/tasks" $H $body) | ConvertFrom-Json
    }
    $pt = Get-Json "$API/plans/$detailId/tasks" $H
    $byCode = @{}
    @($pt) | ForEach-Object { $byCode[$_.taskCode] = $_.id }
    if (-not $byCode.ContainsKey('CRM-D2')) { throw 'no CRM-D2' }
    $lk = Get-Json "$API/plans/$detailId/tasks/$($byCode['CRM-D2'])/links" $H
    if (-not ($lk | Where-Object { $_.targetType -eq 'EXECUTION_TASK' -and $_.isPrimaryExecution })) {
        $null = Post-Json "$API/plans/$detailId/tasks/$($byCode['CRM-D2'])/links" $H @{
            targetType = 'EXECUTION_TASK'; targetId = $need.id; linkType = 'RELATED'
            note = 'Giao viec CRM-D2 cho member3 (luong ke hoach -> cong viec)'; isPrimaryExecution = $true
        }
    }
    $lk2 = Get-Json "$API/plans/$detailId/tasks/$($byCode['CRM-D4'])/links" $H
    if (-not ($lk2 | Where-Object { $_.targetType -eq 'EXECUTION_TASK' -and $_.isPrimaryExecution })) {
        $null = Post-Json "$API/plans/$detailId/tasks/$($byCode['CRM-D4'])/links" $H @{
            targetType = 'EXECUTION_TASK'; targetId = $need2.id; linkType = 'RELATED'
            note = 'Giao viec CRM-D4 cho member2 (kiem thu Dashboard)'; isPrimaryExecution = $true
        }
    }
    $all = Get-Json "$API/plans/$detailId/tasks/$($byCode['CRM-D2'])/links" $H
    if (-not ($all | Where-Object { $_.targetType -eq 'EXECUTION_TASK' })) { throw 'no EXECUTION_TASK link on CRM-D2' }
}

# 7. Buoc 5: Baseline (master APPROVED + baseline 1)
Check 'Buoc 5 - Baseline master (APPROVED + baseline >= 1)' {
    $m = Get-Json "$API/plans/$masterId" $H
    if ($m.status -eq 'DRAFT' -or $m.status -eq 'SUBMITTED') {
        $null = Invoke-WebRequest -Uri "$API/plans/$masterId/submit" -Method POST -Headers $H -UseBasicParsing -TimeoutSec 20
        $null = Invoke-WebRequest -Uri "$API/plans/$masterId/approve" -Method POST -Headers $H -UseBasicParsing -TimeoutSec 20
        $null = Post-Json "$API/plans/$masterId/versions" $H @{ note = 'Version 1 - Master chuan FULL_SDL' }
        $null = Post-Json "$API/plans/$masterId/baselines" $H @{ description = 'Baseline 1 - Phe duyet Master chuan FULL_SDL' }
    }
    $bs = Get-Json "$API/plans/$masterId/baselines" $H
    if (@($bs).Count -lt 1) { throw 'baselines < 1' }
    $var = Get-Json "$API/plans/$masterId/baselines/1/variance" $H
    if (-not $var) { throw 'variance empty' }
}

# 8. Buoc 6: Moc baseline -> milestone + plan_links MILESTONE (nhu task/meeting/risk/issue)
Check 'Buoc 6 - Moc baseline -> Milestone (feature Milestone + link)' {
    $ms = Get-Json "$API/milestones?projectId=$projectId&size=50" $H
    $msList = if ($ms.content) { @($ms.content) } else { @($ms) }
    $moc = $msList | Where-Object { $_.name -like 'MS-DEV-RW*' } | Select-Object -First 1
    if (-not $moc) {
        $body = @{
            projectId = $projectId; name = 'MS-DEV-RW: Review & nghiem thu giai doan DEV'
            description = 'Moc baseline tu detail plan PF-CRM-DEV (CRM-D5) - quan ly trong tinh nang Milestone'
            plannedDate = '2026-11-09'; note = 'Dong bo tu moc baseline ke hoach (V10 / demo-flow)'
        }
        $moc = Get-Text (Post-Json "$API/milestones" $H $body) | ConvertFrom-Json
    }
    if (-not $moc.id) { throw 'no milestone created' }
    $lk = Get-Json "$API/plans/$detailId/tasks/$milestoneTaskId/links" $H
    if (-not ($lk | Where-Object { $_.targetType -eq 'MILESTONE' })) {
        $null = Post-Json "$API/plans/$detailId/tasks/$milestoneTaskId/links" $H @{
            targetType = 'MILESTONE'; targetId = $moc.id; linkType = 'RELATED'
            note = 'Moc baseline -> Milestone (quan ly nhu task/hop/risk/issue)'; isPrimaryExecution = $false
        }
    }
    $lk2 = Get-Json "$API/plans/$detailId/tasks/$milestoneTaskId/links" $H
    if (-not ($lk2 | Where-Object { $_.targetType -eq 'MILESTONE' })) { throw 'no MILESTONE link' }
}

# 9. Verify tong the: portfolio thay PRJ-CRM, master critical-path, link dong bo cua PRJ-AGILE
Check 'Verify - Portfolio co PRJ-CRM + master critical-path OK' {
    $pf = Get-Json "$API/portfolio" $H
    if (-not ($pf.projects | Where-Object { $_.code -eq 'PRJ-CRM' })) { throw 'PRJ-CRM missing in portfolio' }
    $cp = Get-Json "$API/plans/$masterId/critical-path" $H
    if ($cp.criticalTaskCount -lt 1) { throw 'critical path empty' }
}
Check 'Verify - PRJ-AGILE moc baseline da sync sang Milestone (e51/e52)' {
    $lk1 = Get-Json "$API/plans/00000000-0000-0000-0000-000000000b01/tasks/00000000-0000-0000-0000-000000000c06/links" $H
    if (-not ($lk1 | Where-Object { $_.targetType -eq 'MILESTONE' -and $_.targetId -eq '00000000-0000-0000-0000-000000001006' })) { throw 'e51 missing' }
    $lk2 = Get-Json "$API/plans/00000000-0000-0000-0000-000000000b02/tasks/00000000-0000-0000-0000-000000000e05/links" $H
    if (-not ($lk2 | Where-Object { $_.targetType -eq 'MILESTONE' -and $_.targetId -eq '00000000-0000-0000-0000-000000001007' })) { throw 'e52 missing' }
}

Write-Host ''
Write-Host "Tong ket: $passCount PASS / $failCount FAIL" -ForegroundColor $(if ($failCount -eq 0) { 'Green' } else { 'Red' })
if ($failCount -gt 0) { $details | ForEach-Object { Write-Host "  FAIL: $_" -ForegroundColor Red } ; exit 1 }
Write-Host 'Demo flow OK.'
