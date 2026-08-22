import { test, expect } from '@playwright/test';

test.describe('PM Daily Work Management - QA Testing & Finance EVM E2E Tests', () => {

  test.beforeEach(async ({ page }) => {
    // 1. Đăng nhập hệ thống bằng tài khoản PM
    await page.goto('/auth/login');
    await page.fill('input[type="text"]', 'pm.minh');
    await page.fill('input[type="password"]', 'Pm@12345');
    await page.click('button[type="submit"]');
    await expect(page).toHaveURL(/\/dashboard/);
  });

  test('Kiểm tra màn hình QA (Quản lý Test Cases & Test Runs)', async ({ page }) => {
    // 2. Đi tới chi tiết dự án CRM
    await page.goto('/projects');
    const prjLink = page.locator('a', { hasText: 'He thong CRM Ban hang' });
    await expect(prjLink).toBeVisible();
    await prjLink.click();

    // 3. Chuyển sang Tab QA
    const qaTab = page.locator('button.tab-header', { hasText: 'QA' });
    await expect(qaTab).toBeVisible();
    await qaTab.click();

    // Xác nhận đã vào giao diện QA
    await expect(page.locator('.qa-tab-container')).toBeVisible();
    
    // Kiểm tra danh sách Test Cases / Test Runs
    const subTabTc = page.locator('button.qa-sub-tab', { hasText: 'Test Cases' });
    await expect(subTabTc).toBeVisible();
    
    const subTabTr = page.locator('button.qa-sub-tab', { hasText: 'Test Runs' });
    await expect(subTabTr).toBeVisible();
  });

  test('Kiểm tra màn hình Tài chính & Chỉ số EVM (Planned/Earned Value, CPI/SPI)', async ({ page }) => {
    // 4. Đi tới chi tiết dự án CRM
    await page.goto('/projects');
    const prjLink = page.locator('a', { hasText: 'He thong CRM Ban hang' });
    await expect(prjLink).toBeVisible();
    await prjLink.click();

    // 5. Chuyển sang Tab Tài chính
    const financeTab = page.locator('button.tab-header', { hasText: 'Tài chính' });
    await expect(financeTab).toBeVisible();
    await financeTab.click();

    // Xác nhận đã vào giao diện EVM
    await expect(page.locator('.finance-tab-container')).toBeVisible();
    
    // Kiểm tra hiển thị các giá trị PV, EV, AC
    await expect(page.locator('.finance-stats-card')).toBeVisible();
    await expect(page.locator('.metric-title', { hasText: 'CPI' })).toBeVisible();
    await expect(page.locator('.metric-title', { hasText: 'SPI' })).toBeVisible();

    // Kiểm tra có đồ thị SVG tiến độ tài chính
    await expect(page.locator('svg.evm-chart-svg')).toBeVisible();
  });
});
