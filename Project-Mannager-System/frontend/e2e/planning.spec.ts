import { test, expect } from '@playwright/test';

test.describe('PM Daily Work Management - Project Planning E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Đăng nhập hệ thống bằng tài khoản PM
    await page.goto('/auth/login');
    await page.fill('input[type="text"]', 'pm.minh');
    await page.fill('input[type="password"]', 'Pm@12345');
    await page.click('button[type="submit"]');
    
    // Đợi chuyển hướng đến dashboard thành công
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('Kiểm tra danh sách kế hoạch và chi tiết Master Plan (WBS & Gantt SVG)', async ({ page }) => {
    // 2. Đi tới trang danh sách Kế hoạch
    await page.goto('/plans');
    await expect(page.locator('h3')).toContainText('Lập kế hoạch');

    // Tìm dự án CRM trong danh sách và bấm xem chi tiết
    const planCard = page.locator('.plan-card', { hasText: 'PF-CRM-MASTER' });
    await expect(planCard).toBeVisible();
    await planCard.click();

    // Xác nhận đã vào trang chi tiết kế hoạch
    await expect(page).toHaveURL(/\/plans\/[a-f0-9-]+/);
    await expect(page.locator('.plan-title')).toContainText('Master Plan: CRM');

    // 3. Tab WBS: Kiểm tra hiển thị bảng phân rã công việc WBS
    const wbsTab = page.locator('button.tab-header', { hasText: 'WBS' });
    await wbsTab.click();
    
    // Kiểm tra dòng 1. INITIATION
    await expect(page.locator('.wbs-row', { hasText: '1. INITIATION' })).toBeVisible();
    
    // 4. Tab Lịch trình & Găng: Kiểm tra tính toán đường găng
    const cpTab = page.locator('button.tab-header', { hasText: 'Lịch trình & Găng' });
    await cpTab.click();
    await expect(page.locator('.critical-path-summary')).toBeVisible();
    await expect(page.locator('.critical-badge')).toBeVisible();

    // 5. Tab Gantt: Kiểm tra hiển thị biểu đồ Gantt tự vẽ SVG
    const ganttTab = page.locator('button.tab-header', { hasText: 'Gantt' });
    await ganttTab.click();
    await expect(page.locator('svg.gantt-svg')).toBeVisible();
    await expect(page.locator('svg.gantt-svg .gantt-bar-critical')).toBeVisible();
  });
});
