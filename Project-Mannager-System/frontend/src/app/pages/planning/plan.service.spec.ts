import { describe, it, expect, beforeEach } from 'vitest';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PlanService } from './plan.service';

describe('PlanService', () => {
  let service: PlanService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PlanService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  it('should list plans with all filters', () => {
    service.getPlans('alpha', 'prj1', 'MASTER', 'DRAFT', 1, 20, 'planCode,asc').subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/plans'
      && r.params.get('page') === '1'
      && r.params.get('size') === '20'
      && r.params.get('sort') === 'planCode,asc'
      && r.params.get('keyword') === 'alpha'
      && r.params.get('projectId') === 'prj1'
      && r.params.get('planType') === 'MASTER'
      && r.params.get('status') === 'DRAFT'
    );
    expect(req.request.method).toBe('GET');
    req.flush({ content: [], totalElements: 0, page: 1, totalPages: 0 });
    httpMock.verify();
  });

  it('should list plans with defaults when no filters', () => {
    service.getPlans().subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/plans'
      && r.params.get('page') === '0'
      && r.params.get('size') === '10'
      && r.params.get('sort') === 'createdAt,desc'
    );
    expect(req.request.params.has('keyword')).toBe(false);
    expect(req.request.params.has('status')).toBe(false);
    req.flush({ content: [], totalElements: 0, page: 0, totalPages: 0 });
    httpMock.verify();
  });

  it('should create plan via POST /plans', () => {
    service.createPlan({
      projectId: 'prj1',
      planCode: 'MASTER-001',
      planName: 'Kế hoạch tổng thể',
      planType: 'MASTER'
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({
      projectId: 'prj1',
      planCode: 'MASTER-001',
      planType: 'MASTER'
    });
    req.flush({ id: 'p1', status: 'DRAFT', version: 1 });
    httpMock.verify();
  });

  it('should create detail plan with parentPlanId', () => {
    service.createPlan({
      projectId: 'prj1',
      planCode: 'BE-001',
      planName: 'Backend Plan',
      planType: 'DETAIL',
      parentPlanId: 'master-1'
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans');
    expect(req.request.body).toMatchObject({ planType: 'DETAIL', parentPlanId: 'master-1' });
    req.flush({ id: 'd1', version: 1 });
    httpMock.verify();
  });

  it('should update plan with version via PUT /plans/{id}', () => {
    service.updatePlan('p1', {
      planName: 'Tên mới',
      description: 'mô tả',
      plannedStart: '2026-08-03',
      note: 'ghi chú',
      version: 3
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({ planName: 'Tên mới', version: 3 });
    req.flush({ id: 'p1', version: 4 });
    httpMock.verify();
  });

  it('should delete plan via DELETE /plans/{id}', () => {
    service.deletePlan('p1').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should submit plan via POST /plans/{id}/submit', () => {
    service.submitPlan('p1').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/submit');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({});
    req.flush({ id: 'p1', status: 'SUBMITTED' });
    httpMock.verify();
  });

  it('should approve plan via POST /plans/{id}/approve', () => {
    service.approvePlan('p1').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/approve');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'p1', status: 'APPROVED' });
    httpMock.verify();
  });

  it('should activate plan via POST /plans/{id}/activate', () => {
    service.activatePlan('p1').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/activate');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'p1', status: 'ACTIVE' });
    httpMock.verify();
  });

  it('should get single plan via GET /plans/{id}', () => {
    service.getPlan('p1').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'p1' });
    httpMock.verify();
  });

  it('should get WBS tree via GET /plans/{id}/tasks', () => {
    service.getTasks('p1').subscribe(res => {
      expect(res.length).toBe(2);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks');
    expect(req.request.method).toBe('GET');
    req.flush([
      { id: 't1', wbsCode: '1', taskName: 'Phase 1' },
      { id: 't2', parentId: 't1', wbsCode: '1.1', taskName: 'Task 1.1' }
    ]);
    httpMock.verify();
  });

  it('should create task via POST /plans/{id}/tasks', () => {
    service.createTask('p1', {
      parentId: 't1',
      taskCode: 'TASK-001',
      taskName: 'Thiết kế DB',
      taskType: 'TASK',
      percentComplete: 0,
      status: 'NOT_STARTED',
      priority: 'MEDIUM',
      scheduleMode: 'AUTO'
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ parentId: 't1', taskCode: 'TASK-001', taskType: 'TASK' });
    req.flush({ id: 't3', wbsCode: '1.2', version: 1 });
    httpMock.verify();
  });

  it('should update task with version via PUT /plans/{id}/tasks/{taskId}', () => {
    service.updateTask('p1', 't3', {
      taskName: 'Tên mới',
      taskType: 'TASK',
      percentComplete: 50,
      status: 'IN_PROGRESS',
      priority: 'HIGH',
      scheduleMode: 'AUTO',
      version: 2
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/t3');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({ taskName: 'Tên mới', version: 2, percentComplete: 50 });
    req.flush({ id: 't3', version: 3 });
    httpMock.verify();
  });

  it('should delete task via DELETE /plans/{id}/tasks/{taskId}', () => {
    service.deleteTask('p1', 't3').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/t3');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should move task via PUT /plans/{id}/tasks/{taskId}/move', () => {
    service.moveTask('p1', 't3', { direction: 'DOWN' }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/t3/move');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ direction: 'DOWN' });
    req.flush({ id: 't3', version: 4 });
    httpMock.verify();
  });

  it('should move task to parent via TO_PARENT with targetParentId', () => {
    service.moveTask('p1', 't3', { direction: 'TO_PARENT', targetParentId: 't1' }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/t3/move');
    expect(req.request.body).toEqual({ direction: 'TO_PARENT', targetParentId: 't1' });
    req.flush({ id: 't3' });
    httpMock.verify();
  });

  it('should get dependencies via GET /plans/{id}/tasks/dependencies', () => {
    service.getDependencies('p1').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].dependencyType).toBe('FS');
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/dependencies');
    expect(req.request.method).toBe('GET');
    req.flush([
      { id: 'd1', predecessorTaskCode: 'A', successorTaskCode: 'B', dependencyType: 'FS', lagMinutes: 480 }
    ]);
    httpMock.verify();
  });

  it('should create dependency via POST /plans/{id}/tasks/{taskId}/dependencies', () => {
    service.createDependency('p1', 'tB', {
      predecessorTaskId: 'tA',
      dependencyType: 'SS',
      lagMinutes: -240
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/tB/dependencies');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ predecessorTaskId: 'tA', dependencyType: 'SS', lagMinutes: -240 });
    req.flush({ id: 'd2', dependencyType: 'SS' });
    httpMock.verify();
  });

  it('should delete dependency via DELETE /plans/{id}/tasks/{taskId}/dependencies/{depId}', () => {
    service.deleteDependency('p1', 'tB', 'd1').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/tB/dependencies/d1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should list calendars via GET /plan-calendars', () => {
    service.getCalendars().subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].name).toBe('Lịch chuẩn');
    });
    const req = httpMock.expectOne('/api/v1/plan-calendars');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'c1', name: 'Lịch chuẩn', status: 'ACTIVE', workingDays: [], exceptions: [] }]);
    httpMock.verify();
  });

  it('should create calendar via POST /plan-calendars', () => {
    service.createCalendar({
      name: 'Lịch VN',
      dailyWorkingHours: 8,
      timezone: 'Asia/Ho_Chi_Minh',
      workingDays: [{ dayOfWeek: 1, isWorking: true }]
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plan-calendars');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ name: 'Lịch VN', dailyWorkingHours: 8 });
    req.flush({ id: 'c2', status: 'ACTIVE', version: 1 });
    httpMock.verify();
  });

  it('should update calendar with version via PUT /plan-calendars/{id}', () => {
    service.updateCalendar('c1', {
      name: 'Lịch mới',
      status: 'INACTIVE',
      version: 2,
      workingDays: []
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plan-calendars/c1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({ name: 'Lịch mới', status: 'INACTIVE', version: 2 });
    req.flush({ id: 'c1', version: 3 });
    httpMock.verify();
  });

  it('should delete calendar via DELETE /plan-calendars/{id}', () => {
    service.deleteCalendar('c1').subscribe();
    const req = httpMock.expectOne('/api/v1/plan-calendars/c1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should add calendar exception via POST /plan-calendars/{id}/exceptions', () => {
    service.addCalendarException('c1', {
      exceptionDate: '2026-09-02',
      exceptionType: 'NON_WORKING',
      note: 'Lễ Quốc khánh'
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plan-calendars/c1/exceptions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ exceptionDate: '2026-09-02', exceptionType: 'NON_WORKING' });
    req.flush({ id: 'e1', exceptionDate: '2026-09-02', exceptionType: 'NON_WORKING' });
    httpMock.verify();
  });

  it('should get effective plan calendar via GET /plans/{id}/calendar', () => {
    service.getPlanCalendar('p1').subscribe(res => {
      expect(res.name).toBe('Lịch hiệu lực');
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/calendar');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'c1', name: 'Lịch hiệu lực', status: 'ACTIVE', workingDays: [], exceptions: [] });
    httpMock.verify();
  });

  it('should run recalc via POST /plans/{id}/recalc', () => {
    service.recalculatePlan('p1').subscribe(res => {
      expect(res.warnings.length).toBe(1);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/recalc');
    expect(req.request.method).toBe('POST');
    req.flush({
      planId: 'p1',
      totalTasks: 5,
      scheduledTasks: 4,
      warnings: [{ wbsCode: '1.1', type: 'NO_START_ANCHOR', message: 'Thiếu neo' }]
    });
    httpMock.verify();
  });

  it('should get critical path via GET /plans/{id}/critical-path', () => {
    service.getCriticalPath('p1').subscribe(res => {
      expect(res.criticalTaskCount).toBe(2);
      expect(res.tasks.length).toBe(2);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/critical-path');
    expect(req.request.method).toBe('GET');
    req.flush({
      planId: 'p1',
      thresholdMinutes: 0,
      criticalTaskCount: 2,
      tasks: [
        { taskId: 't1', wbsCode: '1', taskName: 'A', taskType: 'PHASE', totalFloatMinutes: 0, freeFloatMinutes: 0, isCritical: true },
        { taskId: 't2', wbsCode: '1.1', taskName: 'B', taskType: 'TASK', totalFloatMinutes: 480, freeFloatMinutes: 480, isCritical: false }
      ]
    });
    httpMock.verify();
  });

  it('should get plan resources via GET /plans/{id}/resources', () => {
    service.getPlanResources('p1').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].resourceName).toBe('Nguyễn A');
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/resources');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'r1', taskCode: 'A', resourceName: 'Nguyễn A', resourceType: 'USER', allocationPercent: 100, overAllocation: false }]);
    httpMock.verify();
  });

  it('should assign resource via POST /plans/{id}/tasks/{taskId}/resources', () => {
    service.assignResource('p1', 't1', {
      resourceType: 'USER',
      resourceId: 'u1',
      allocationPercent: 80
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/t1/resources');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ resourceType: 'USER', resourceId: 'u1', allocationPercent: 80 });
    req.flush({ id: 'r2', overAllocation: false });
    httpMock.verify();
  });

  it('should update allocation via PUT /resource-allocations/{id}', () => {
    service.updateResourceAllocation('r1', { allocationPercent: 50 }).subscribe();
    const req = httpMock.expectOne('/api/v1/resource-allocations/r1');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ allocationPercent: 50 });
    req.flush({ id: 'r1', allocationPercent: 50 });
    httpMock.verify();
  });

  it('should remove allocation via DELETE /resource-allocations/{id}', () => {
    service.removeResourceAllocation('r1').subscribe();
    const req = httpMock.expectOne('/api/v1/resource-allocations/r1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should update capacity via PUT /resources/{resourceId}/capacity', () => {
    service.updateCapacity('u1', {
      resourceType: 'USER',
      capacityPercent: 50,
      startDate: '2026-08-03',
      source: 'PROJECT'
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/resources/u1/capacity');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toMatchObject({ capacityPercent: 50, source: 'PROJECT' });
    req.flush({});
    httpMock.verify();
  });

  it('should get plan workload via GET /plans/{id}/workload with params', () => {
    service.getPlanWorkload('p1', '2026-08-01', '2026-08-10', 'WEEK').subscribe();
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/plans/p1/workload'
      && r.params.get('from') === '2026-08-01'
      && r.params.get('to') === '2026-08-10'
      && r.params.get('granularity') === 'WEEK'
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);
    httpMock.verify();
  });

  it('should get resources overview via GET /resources/overview with params', () => {
    service.getResourcesOverview('2026-08-01', '2026-08-10').subscribe(res => {
      expect(res.length).toBe(1);
    });
    const req = httpMock.expectOne(r =>
      r.url === '/api/v1/resources/overview'
      && r.params.get('from') === '2026-08-01'
      && r.params.get('to') === '2026-08-10'
    );
    expect(req.request.method).toBe('GET');
    req.flush([{ resourceName: 'X', resourceType: 'USER', demandMinutes: 100, overAllocation: false }]);
    httpMock.verify();
  });

  it('should get versions via GET /plans/{id}/versions', () => {
    service.getVersions('p1').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].versionNo).toBe(2);
      expect(res[0].isActive).toBe(true);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/versions');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'v1', planId: 'p1', versionNo: 2, status: 'APPROVED', createdAt: '2026-08-01T10:00:00Z', taskCount: 5, dependencyCount: 3, resourceCount: 2, isActive: true }]);
    httpMock.verify();
  });

  it('should create version via POST /plans/{id}/versions', () => {
    service.createVersion('p1', 'Chốt giai đoạn 1').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/versions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ note: 'Chốt giai đoạn 1' });
    req.flush({ id: 'v2', versionNo: 3, isActive: true });
    httpMock.verify();
  });

  it('should get version diff via GET /plans/{id}/versions/{versionNo}/diff', () => {
    service.getVersionDiff('p1', 2).subscribe(res => {
      expect(res.versionNo).toBe(2);
      expect(res.compareToVersionNo).toBe(3);
      expect(res.tasks.length).toBe(1);
      expect(res.tasks[0].field).toBe('startDate');
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/versions/2/diff');
    expect(req.request.method).toBe('GET');
    req.flush({
      versionNo: 2,
      compareToVersionNo: 3,
      tasks: [{ wbsCode: '1.1', taskName: 'A', field: 'startDate', fromValue: '2026-08-01', toValue: '2026-08-03' }]
    });
    httpMock.verify();
  });

  it('should get baselines via GET /plans/{id}/baselines', () => {
    service.getBaselines('p1').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].baselineNum).toBe(1);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/baselines');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'b1', planId: 'p1', baselineNum: 1, versionNo: 2, capturedAt: '2026-08-01T10:00:00Z', taskCount: 5 }]);
    httpMock.verify();
  });

  it('should create baseline via POST /plans/{id}/baselines', () => {
    service.createBaseline('p1', 'Chốt baseline').subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/baselines');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ description: 'Chốt baseline' });
    req.flush({ id: 'b2', baselineNum: 2, taskCount: 5 });
    httpMock.verify();
  });

  it('should get baseline variance via GET /plans/{id}/baselines/{num}/variance', () => {
    service.getBaselineVariance('p1', 1).subscribe(res => {
      expect(res.baselineNum).toBe(1);
      expect(res.tasks.length).toBe(1);
      expect(res.tasks[0].progressDifference).toBe(-20);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/baselines/1/variance');
    expect(req.request.method).toBe('GET');
    req.flush({
      baselineId: 'b1',
      baselineNum: 1,
      planId: 'p1',
      planName: 'Kế hoạch A',
      tasks: [{
        taskId: 't1', wbsCode: '1.1', taskName: 'A', taskType: 'TASK',
        baselineProgress: 80, currentProgress: 60, progressDifference: -20,
        milestoneDone: false, taskDeleted: false
      }]
    });
    httpMock.verify();
  });

  it('should delete baseline via DELETE /plans/{id}/baselines/{num}', () => {
    service.deleteBaseline('p1', 1).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/baselines/1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should get change histories via GET /plans/{id}/change-histories', () => {
    service.getChangeHistories('p1').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].changeType).toBe('PLAN_TASK_UPDATED');
      expect(res[0].fieldChanged).toBe('plannedStart');
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/change-histories');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'h1', planId: 'p1', changeType: 'PLAN_TASK_UPDATED', entityType: 'PLAN_TASK', fieldChanged: 'plannedStart', oldValue: '2026-08-03', newValue: '2026-08-10', changedAt: '2026-08-01T10:00:00Z' }]);
    httpMock.verify();
  });

  it('should get change suggestions via GET /plans/{id}/change-suggestions', () => {
    service.getChangeSuggestions('p1').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].status).toBe('PENDING');
      expect(res[0].title).toBe('Trễ kế hoạch');
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/change-suggestions');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 's1', planId: 'p1', sourceType: 'ISSUE', title: 'Trễ kế hoạch', description: 'x', status: 'PENDING', createdAt: '2026-08-01T10:00:00Z' }]);
    httpMock.verify();
  });

  it('should create change suggestion via POST /plans/{id}/change-suggestions', () => {
    service.createChangeSuggestion('p1', {
      title: 'T',
      description: 'D',
      sourceType: 'ISSUE',
      sourceId: 'i1',
      suggestedChanges: [{ entityType: 'PLAN_TASK', entityId: 't1', field: 'percentComplete', oldValue: '0', newValue: '50' }]
    }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/change-suggestions');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ title: 'T', suggestedChanges: [{ entityType: 'PLAN_TASK', entityId: 't1', field: 'percentComplete', oldValue: '0', newValue: '50' }] });
    req.flush({ id: 's1', status: 'PENDING' });
    httpMock.verify();
  });

  it('should accept suggestion via POST /change-suggestions/{id}/accept', () => {
    service.acceptSuggestion('s1').subscribe(res => {
      expect(res.status).toBe('APPLIED');
    });
    const req = httpMock.expectOne('/api/v1/change-suggestions/s1/accept');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 's1', status: 'APPLIED' });
    httpMock.verify();
  });

  it('should reject suggestion via POST /change-suggestions/{id}/reject', () => {
    service.rejectSuggestion('s1').subscribe(res => {
      expect(res.status).toBe('REJECTED');
    });
    const req = httpMock.expectOne('/api/v1/change-suggestions/s1/reject');
    expect(req.request.method).toBe('POST');
    req.flush({ id: 's1', status: 'REJECTED' });
    httpMock.verify();
  });

  it('should get task links via GET /plans/{id}/tasks/{taskId}/links', () => {
    service.getTaskLinks('p1', 't1').subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].targetType).toBe('EXECUTION_TASK');
      expect(res[0].isPrimaryExecution).toBe(true);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/t1/links');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'l1', planId: 'p1', planningTaskId: 't1', targetType: 'EXECUTION_TASK', targetId: 'e1', linkType: 'RELATED', isPrimaryExecution: true, createdAt: '2026-08-01T10:00:00Z' }]);
    httpMock.verify();
  });

  it('should create link via POST /plans/{id}/tasks/{taskId}/links', () => {
    service.createLink('p1', 't1', { targetType: 'ISSUE', targetId: 'i1', linkType: 'BLOCKED_BY' }).subscribe();
    const req = httpMock.expectOne('/api/v1/plans/p1/tasks/t1/links');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ targetType: 'ISSUE', targetId: 'i1', linkType: 'BLOCKED_BY' });
    req.flush({ id: 'l2', isPrimaryExecution: false });
    httpMock.verify();
  });

  it('should delete link via DELETE /links/{id}', () => {
    service.deleteLink('l1').subscribe();
    const req = httpMock.expectOne('/api/v1/links/l1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
    httpMock.verify();
  });

  it('should get templates via GET /plan-templates', () => {
    service.getTemplates().subscribe(res => {
      expect(res.length).toBe(1);
      expect(res[0].templateCode).toBe('FULL_SDL');
      expect(res[0].taskCount).toBe(17);
    });
    const req = httpMock.expectOne('/api/v1/plan-templates');
    expect(req.request.method).toBe('GET');
    req.flush([{ id: 'tp1', templateCode: 'FULL_SDL', templateName: 'Software Development Lifecycle', templateType: 'FULL', versionNo: 1, status: 'PUBLISHED', isBuiltIn: true, taskCount: 17 }]);
    httpMock.verify();
  });

  it('should get template detail via GET /plan-templates/{id}', () => {
    service.getTemplateDetail('tp1').subscribe(res => {
      expect(res.tasks.length).toBe(2);
      expect(res.tasks[0].taskType).toBe('PHASE');
    });
    const req = httpMock.expectOne('/api/v1/plan-templates/tp1');
    expect(req.request.method).toBe('GET');
    req.flush({ id: 'tp1', templateCode: 'FULL_SDL', templateName: 'SDL', templateType: 'FULL', status: 'PUBLISHED', taskCount: 2, tasks: [
      { id: 't1', parentId: null, taskName: 'INITIATION', taskType: 'PHASE', sequenceNo: 1, wbsCode: '1', durationMinutes: 960, scheduleMode: 'AUTO' },
      { id: 't2', parentId: 't1', taskName: 'Kickoff', taskType: 'MILESTONE', sequenceNo: 1, wbsCode: '1.1', durationMinutes: 0, scheduleMode: 'MANUAL' }
    ] });
    httpMock.verify();
  });

  it('should create plan from template via POST /plans/from-template', () => {
    service.createPlanFromTemplate({
      projectId: 'prj1', templateId: 'tp1', planCode: 'PLN-01', planName: 'Plan SDL', planType: 'MASTER', startDate: '2026-08-03'
    }).subscribe(res => {
      expect(res.id).toBe('p1');
    });
    const req = httpMock.expectOne('/api/v1/plans/from-template');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toMatchObject({ projectId: 'prj1', templateId: 'tp1', planCode: 'PLN-01', startDate: '2026-08-03' });
    req.flush({ id: 'p1', planCode: 'PLN-01', planName: 'Plan SDL' });
    httpMock.verify();
  });

  it('should get portfolio via GET /portfolio', () => {
    service.getPortfolio().subscribe(res => {
      expect(res.totalProjects).toBe(2);
      expect(res.projects.length).toBe(2);
      expect(res.upcomingMilestones.length).toBe(1);
    });
    const req = httpMock.expectOne('/api/v1/portfolio');
    expect(req.request.method).toBe('GET');
    req.flush({
      totalProjects: 2, activeProjects: 1, delayedProjects: 1, overAllocatedResourcesCount: 1, averageProgress: 45.5,
      projects: [
        { id: 'p1', code: 'PRJ-1', name: 'A', pmName: 'Nguyễn A', status: 'ACTIVE', plannedStart: '2026-08-01', plannedFinish: '2026-12-31', progress: 50, delayDays: 3, isOverAllocated: true, criticalTaskCount: 2 },
        { id: 'p2', code: 'PRJ-2', name: 'B', pmName: 'Trần B', status: 'APPROVED', progress: 40, delayDays: 0, isOverAllocated: false }
      ],
      upcomingMilestones: [{ id: 'm1', name: 'M1', targetDate: '2026-08-20', status: 'UPCOMING', projectId: 'p1', projectName: 'A' }]
    });
    httpMock.verify();
  });

  it('should get gantt data via GET /plans/{id}/gantt', () => {
    service.getGantt('p1').subscribe(res => {
      expect(res.plan.planCode).toBe('GT-01');
      expect(res.tasks.length).toBe(2);
      expect(res.tasks[0].isCritical).toBe(true);
      expect(res.tasks[0].baseline?.start).toBe('2026-08-03');
      expect(res.tasks[0].resources[0].allocationPercent).toBe(80);
      expect(res.dependencies.length).toBe(1);
      expect(res.warnings.length).toBe(0);
    });
    const req = httpMock.expectOne('/api/v1/plans/p1/gantt');
    expect(req.request.method).toBe('GET');
    req.flush({
      plan: { id: 'p1', planCode: 'GT-01', planName: 'GT-01', planType: 'MASTER', status: 'APPROVED' },
      tasks: [
        {
          id: 't1', parentId: null, wbsCode: '1', taskName: 'Task A', taskType: 'TASK',
          start: '2026-08-03', finish: '2026-08-04', durationMinutes: 960, plannedEffortMinutes: 480,
          percentComplete: 50, status: 'IN_PROGRESS', scheduleMode: 'AUTO', isCritical: true,
          baseline: { start: '2026-08-03', finish: '2026-08-04' },
          resources: [{ resourceId: 'u1', resourceType: 'USER', allocationPercent: 80 }]
        },
        {
          id: 't2', parentId: 't1', wbsCode: '1.1', taskName: 'Task B', taskType: 'MILESTONE',
          start: '2026-08-05', finish: '2026-08-05', durationMinutes: 0, plannedEffortMinutes: 0,
          percentComplete: 0, status: 'NOT_STARTED', scheduleMode: 'AUTO', isCritical: true,
          baseline: null, resources: []
        }
      ],
      dependencies: [{ from: 't1', to: 't2', type: 'FS', lagMinutes: 0 }],
      warnings: []
    });
    httpMock.verify();
  });
});