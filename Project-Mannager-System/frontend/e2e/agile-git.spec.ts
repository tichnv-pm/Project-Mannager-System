import { test, expect } from '@playwright/test';

test.describe('PM Daily Work Management - Agile Sprints & Git Webhook E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Đăng nhập hệ thống bằng tài khoản PM
    await page.goto('/auth/login');
    await page.fill('input[type="text"]', 'pm.minh');
    await page.fill('input[type="password"]', 'Pm@12345');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('Kiểm tra Sprint Backlog và hiển thị Sprints trong Dự án', async ({ page }) => {
    // 2. Đi tới chi tiết dự án CRM
    await page.goto('/projects');
    const prjLink = page.locator('a', { hasText: 'He thong CRM Ban hang' });
    await expect(prjLink).toBeVisible();
    await prjLink.click();

    // 3. Chuyển sang Tab Sprints
    const sprintsTab = page.locator('button.tab-header', { hasText: 'Sprints' });
    await expect(sprintsTab).toBeVisible();
    await sprintsTab.click();

    // Xác nhận giao diện quản lý Sprints hiển thị
    await expect(page.locator('h3')).toContainText('Quản lý Agile/Sprints');
    
    // Kiểm tra nút tạo Sprint
    const createBtn = page.locator('button', { hasText: 'Tạo Sprint mới' });
    await expect(createBtn).toBeVisible();
  });

  test('Kiểm tra tích hợp thông tin Git trong chi tiết Task', async ({ page }) => {
    // 4. Đi tới trang danh sách Tasks
    await page.goto('/tasks');
    await expect(page.locator('h3')).toContainText('Công việc dự án');

    // Tìm task "Phat trien API Auth & Phan quyen CRM" và click xem chi tiết
    const taskLink = page.locator('td.task-title-cell a', { hasText: 'Phat trien API Auth' }).first();
    await expect(taskLink).toBeVisible();
    await taskLink.click();

    // Xác nhận đã chuyển sang trang chi tiết Task
    await expect(page).toHaveURL(/\/tasks\/[a-f0-9-]+/);

    // Click vào tab Git
    const gitTab = page.locator('button#tab-git');
    await expect(gitTab).toBeVisible();
    await gitTab.click();

    // Đợi tải và kiểm tra thông tin liên kết Git
    await expect(page.locator('.git-section')).toBeVisible();
    // Bằng chứng liên kết: hoặc hiển thị danh sách, hoặc hiển thị "Chưa có commit" nếu dữ liệu trống.
    // Vì script demo V10 đã seed commit nên chúng ta có thể kiểm tra danh sách commit.
    const commitSection = page.locator('.git-section', { hasText: 'Commit' });
    await expect(commitSection).toBeVisible();
  });
});
