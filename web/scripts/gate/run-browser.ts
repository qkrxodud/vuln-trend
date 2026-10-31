// 빌드한 사이트를 시스템 Chrome(헤드리스)으로 열어 Gate G 화면 항목을 관찰한다. 판정은 browser-checks.ts
import { readFile } from 'node:fs/promises'
import { join } from 'node:path'
import { chromium, type Browser, type Page } from 'playwright-core'
import type { Meta } from '../../src/types/meta.ts'
import { checkCacheBusting, checkFooterText, checkSearchIndexLazy, checkStaleBanner } from './browser-checks.ts'
import type { GateResult } from './report.ts'
import { startPagesServer, type PagesServer } from './run-server.ts'

const STALE_BANNER = '데이터가 오래됐습니다'
const HOUR = 60 * 60 * 1000
const SETTLE_MS = 1500

const dataRequestsOf = (page: Page, server: PagesServer): string[] => {
  const urls: string[] = []
  page.on('request', (request) => {
    const url = request.url()
    if (url.startsWith(`${server.origin}${server.base}data/`)) urls.push(url.slice(server.origin.length))
  })
  return urls
}

const open = async (page: Page, server: PagesServer) => {
  await page.goto(`${server.origin}${server.base}`, { waitUntil: 'networkidle' })
  await page.waitForTimeout(SETTLE_MS)
}

const withMeta = (server: PagesServer, meta: Meta, changes: Partial<Meta>) =>
  server.overrides.set('data/meta.json', JSON.stringify({ ...meta, ...changes }))

const footerAndLazy = async (browser: Browser, server: PagesServer): Promise<GateResult[]> => {
  const page = await browser.newPage()
  const requests = dataRequestsOf(page, server)
  await open(page, server)
  const footer = page.getByRole('contentinfo')
  const footerText = (await footer.count()) === 0 ? null : await footer.innerText()
  const beforeFocus = [...requests]
  await page.getByRole('combobox').first().focus()
  await page.waitForTimeout(SETTLE_MS)
  await page.close()
  return [checkFooterText(footerText), checkSearchIndexLazy({ beforeFocus, afterFocus: [...requests] })]
}

const bannerVisible = async (browser: Browser, server: PagesServer): Promise<boolean> => {
  const page = await browser.newPage()
  await open(page, server)
  const visible = await page.getByText(STALE_BANNER).isVisible()
  await page.close()
  return visible
}

const staleBanner = async (browser: Browser, server: PagesServer, meta: Meta): Promise<GateResult> => {
  withMeta(server, meta, { updated_at: new Date(Date.now() - 48 * HOUR).toISOString() })
  const stale = await bannerVisible(browser, server)
  withMeta(server, meta, { updated_at: new Date().toISOString() })
  const fresh = await bannerVisible(browser, server)
  server.overrides.delete('data/meta.json')
  return checkStaleBanner({ stale, fresh })
}

// 같은 탭에서 데이터 push(version 변경)를 흉내 내고 새로고침한다. 브라우저 HTTP 캐시가 살아 있는 상태로 본다
const cacheBusting = async (browser: Browser, server: PagesServer, meta: Meta): Promise<GateResult> => {
  const versions = { before: 'gate-check-A', after: 'gate-check-B' }
  const page = await browser.newPage()
  const requests = dataRequestsOf(page, server)
  withMeta(server, meta, { version: versions.before })
  await open(page, server)
  const before = requests.splice(0)
  withMeta(server, meta, { version: versions.after })
  await page.reload({ waitUntil: 'networkidle' })
  await page.waitForTimeout(SETTLE_MS)
  const after = [...requests]
  await page.close()
  server.overrides.delete('data/meta.json')
  return checkCacheBusting({ before, after }, versions)
}

const baseOf = async (distDir: string): Promise<string> => {
  const html = await readFile(join(distDir, '404.html'), 'utf8')
  const match = html.match(/var base = "([^"]+)"/)
  return match === null ? '/vuln-trend/' : match[1]
}

export const runBrowserChecks = async (distDir: string): Promise<GateResult[]> => {
  const meta = JSON.parse(await readFile(join(distDir, 'data', 'meta.json'), 'utf8')) as Meta
  const server = await startPagesServer(distDir, await baseOf(distDir))
  const browser = await chromium.launch({ channel: 'chrome', headless: true })
  try {
    return [
      await staleBanner(browser, server, meta),
      ...(await footerAndLazy(browser, server)),
      await cacheBusting(browser, server, meta),
    ]
  } finally {
    await browser.close()
    await server.close()
  }
}
