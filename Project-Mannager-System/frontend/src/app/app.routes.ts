import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { MainLayoutComponent } from './layout/main-layout/main-layout.component';
import { authGuard } from './core/guards/auth.guard';
import { permissionGuard } from './core/guards/permission.guard';

export const routes: Routes = [
  { path: 'auth/login', component: LoginComponent },
  {
    path: '',
    component: MainLayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: 'dashboard', loadComponent: () => import('./pages/dashboard/dashboard.component').then(m => m.DashboardComponent), canActivate: [permissionGuard], data: { permission: 'dashboard:view' } },
      { path: 'projects', loadComponent: () => import('./pages/projects/project-list.component').then(m => m.ProjectListComponent), canActivate: [permissionGuard], data: { permission: 'project:view' } },
      { path: 'projects/:id', loadComponent: () => import('./pages/projects/project-detail.component').then(m => m.ProjectDetailComponent), canActivate: [permissionGuard], data: { permission: 'project:view' } },
      { path: 'tasks', loadComponent: () => import('./pages/tasks/task-list.component').then(m => m.TaskListComponent), canActivate: [permissionGuard], data: { permission: 'task:view' } },
      { path: 'tasks/:id', loadComponent: () => import('./pages/tasks/task-detail.component').then(m => m.TaskDetailComponent), canActivate: [permissionGuard], data: { permission: 'task:view' } },
      { path: 'meetings', loadComponent: () => import('./pages/meetings/meeting-list.component').then(m => m.MeetingListComponent), canActivate: [permissionGuard], data: { permission: 'meeting:view' } },
      { path: 'risks-issues', loadComponent: () => import('./pages/risks-issues/risk-issue-list.component').then(m => m.RiskIssueListComponent) },
      { path: 'milestones', loadComponent: () => import('./pages/milestones/milestone-list.component').then(m => m.MilestoneListComponent), canActivate: [permissionGuard], data: { permission: 'milestone:view' } },
      { path: 'plans', loadComponent: () => import('./pages/planning/plan-list.component').then(m => m.PlanListComponent), canActivate: [permissionGuard], data: { permission: 'plan:view' } },
      { path: 'plans/templates', loadComponent: () => import('./pages/planning/plan-template.component').then(m => m.PlanTemplateComponent), canActivate: [permissionGuard], data: { permission: 'plan:view' } },
      { path: 'plans/:id', loadComponent: () => import('./pages/planning/plan-detail.component').then(m => m.PlanDetailComponent), canActivate: [permissionGuard], data: { permission: 'plan:view' } },
      { path: 'portfolio', loadComponent: () => import('./pages/planning/plan-portfolio.component').then(m => m.PlanPortfolioComponent), canActivate: [permissionGuard], data: { permission: 'plan:view' } },
      { path: 'reports', loadComponent: () => import('./pages/reports/report-list.component').then(m => m.ReportListComponent), canActivate: [permissionGuard], data: { permission: 'report:view' } },
      { path: 'admin', loadComponent: () => import('./pages/admin/admin-panel.component').then(m => m.AdminPanelComponent), canActivate: [permissionGuard], data: { permission: 'audit:view' } },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'auth/login' }
];
