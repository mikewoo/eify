const { chromium } = require('playwright');
const path = require('path');

const BASE = 'http://localhost:5173';
const API = 'http://localhost:8080/api/v1';
const OUT = path.join(__dirname, '..', 'logs', 'screenshots');

const PAGES = [
  { name: '01-login', path: '/login', fullPage: false },
  { name: '02-agents', path: '/agents', fullPage: true },
  { name: '03-chat', path: '/chat', fullPage: true },
  { name: '04-providers', path: '/providers', fullPage: true },
  { name: '05-mcp-servers', path: '/mcp-servers', fullPage: true },
  { name: '06-knowledge', path: '/knowledge', fullPage: true },
  { name: '07-documents', path: '/documents', fullPage: true },
  { name: '08-workflows', path: '/workflows', fullPage: true },
  { name: '09-workflow-create', path: '/workflows/create', fullPage: true },
  { name: '10-profile', path: '/profile', fullPage: true },
];

(async () => {
  const fs = require('fs');
  if (!fs.existsSync(OUT)) fs.mkdirSync(OUT, { recursive: true });

  const browser = await chromium.launch({ channel: 'msedge' });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
  });

  // --- Login via API to get JWT token ---
  let token = null;
  const credentials = [
    { username: 'admin', password: 'admin123' },
    { username: 'admin', password: 'admin' },
    { username: 'admin', password: '123456' },
    { username: 'admin', password: 'Evyber@#2026' },
  ];

  for (const cred of credentials) {
    try {
      const res = await fetch(`${API}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(cred),
      });
      const data = await res.json();
      if (data.data && data.data.accessToken) {
        token = data.data.accessToken;
        console.log(`Login successful with ${cred.username}/${cred.password}`);
        break;
      }
    } catch (e) {
      // try next
    }
  }

  const page = await context.newPage();

  // --- Page 1: Login page (no auth, always works) ---
  console.log('Screenshotting login page...');
  await page.goto(`${BASE}/login`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1000);
  await page.screenshot({ path: path.join(OUT, '01-login.png'), fullPage: false });
  console.log('  -> 01-login.png');

  // --- If we have a token, inject it and screenshot authenticated pages ---
  if (token) {
    // Set the token in localStorage before navigating
    await page.evaluate((t) => {
      localStorage.setItem('accessToken', t);
    }, token);

    for (const { name, path: pagePath, fullPage } of PAGES.slice(1)) {
      try {
        console.log(`Screenshotting ${pagePath}...`);
        await page.goto(`${BASE}${pagePath}`, { waitUntil: 'networkidle', timeout: 15000 });
        await page.waitForTimeout(1500);
        await page.screenshot({
          path: path.join(OUT, `${name}.png`),
          fullPage,
        });
        console.log(`  -> ${name}.png`);
      } catch (e) {
        console.log(`  -> ${name} FAILED: ${e.message}`);
      }
    }

    // --- Sidebar hover ---
    try {
      console.log('Capturing sidebar hover...');
      await page.goto(`${BASE}/agents`, { waitUntil: 'networkidle', timeout: 15000 });
      await page.waitForTimeout(1000);
      const menuItem = page.locator('.eify-menu-item').first();
      if (await menuItem.count() > 0) {
        await menuItem.hover();
        await page.waitForTimeout(500);
        await page.screenshot({
          path: path.join(OUT, '11-sidebar-hover.png'),
          fullPage: false,
        });
        console.log('  -> 11-sidebar-hover.png');
      }
    } catch (e) {
      console.log(`  -> Sidebar hover FAILED: ${e.message}`);
    }
  } else {
    console.log('No valid credentials found. Only login page screenshot taken.');
    console.log('To capture other pages, update credentials in this script.');
  }

  await browser.close();
  console.log(`\nDone! Screenshots in ${OUT}`);
})();
