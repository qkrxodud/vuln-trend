import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from './api/data-client.ts'
import App from './App.tsx'
import { DataProvider } from './hooks/DataProvider.tsx'
import { FakeFetch } from './test/fake/fake-fetch.ts'
import { setViewportWidth } from './test/fake/fake-media.ts'
import { cveDetail, metaWithVersion, stats } from './test/fixtures.ts'
import { trendRow } from './test/rows.ts'

const first = trendRow({ id: 'CVE-2026-40117', score: 91, title: '첫 번째 취약점' })
const second = trendRow({ id: 'CVE-2026-41203', score: 84, title: '두 번째 취약점' })

const LocationProbe = () => <output aria-label="현재 주소">{useLocation().pathname}</output>

let server: FakeFetch

const renderApp = (path: string) => {
  server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
    .serve('/vuln-trend/data/stats.json?v=v1', stats)
    .serve('/vuln-trend/data/index.json?v=v1', [first, second])
    .serve('/vuln-trend/data/cve/CVE-2026-40117.json?v=v1', { ...cveDetail, ...first })
    .serve('/vuln-trend/data/cve/CVE-2026-41203.json?v=v1', { ...cveDetail, ...second })
  render(
    <MemoryRouter initialEntries={[path]}>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <App />
        <LocationProbe />
      </DataProvider>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

// 같은 CVE 가 KPI 히어로에도 있으므로 목록 행은 목록 영역 안에서 찾는다
const listRow = async (cveId: string) =>
  within(await screen.findByRole('region', { name: '지금 주목할 취약점' })).getByRole('button', {
    name: new RegExp(`^\\d+위 ${cveId}`),
  })

const panelTitle = async () => {
  const panel = await screen.findByRole('complementary', { name: '선택한 취약점 상세' })
  return within(panel).findByRole('heading', { level: 3 })
}

describe('처음 그리기 (레이아웃 밀림 0)', () => {
  it('요약·차트·목록 데이터가 다 올 때까지 한 자리에서 기다렸다가 한꺼번에 그린다', async () => {
    renderApp('/')

    expect(screen.getByText(/^오늘 고칠 취약점 불러오는 중/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: '지금 주목할 취약점' })).toBeNull()
    expect(await screen.findByRole('region', { name: '지금 주목할 취약점' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '어느 쪽이 더 위험한가' })).toBeInTheDocument()
    expect(screen.getByRole('article', { name: 'CISA 조치 기한 임박' })).toBeInTheDocument()
  })
})

describe('메인 화면과 상세 패널 (D8)', () => {
  it('처음에는 목록 맨 위 취약점의 상세를 옆 패널에 보인다', async () => {
    renderApp('/')

    expect(await panelTitle()).toHaveTextContent('첫 번째 취약점')
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent(/^\/$/)
  })

  it('목록에서 취약점을 고르면 주소가 /cve/ID 로 바뀌고 옆 패널에 그 상세가 열린다', async () => {
    const user = renderApp('/')

    await user.click(await listRow('CVE-2026-41203'))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-41203')
    expect(await screen.findByText('CVE-2026-41203 상세 표시 중')).toBeInTheDocument()
    expect(await listRow('CVE-2026-41203')).toHaveAttribute('aria-current', 'true')
  })

  it('제품으로 거른 채 취약점을 골라도 필터가 그대로 남는다', async () => {
    const user = renderApp('/')
    await user.click(await screen.findByRole('button', { name: 'Chrome 2' }))

    await user.click(await listRow('CVE-2026-41203'))

    expect(screen.getByRole('button', { name: 'Chrome 2' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('/cve/ID 주소로 바로 들어오면 상세 페이지를 보인다', async () => {
    renderApp('/cve/CVE-2026-41203')

    expect(await screen.findByRole('heading', { level: 1, name: 'CVE-2026-41203' })).toBeInTheDocument()
    expect(screen.queryByRole('complementary', { name: '선택한 취약점 상세' })).not.toBeInTheDocument()
  })
})

describe('좁은 화면 (1024px 미만, 5.10)', () => {
  it('옆 패널을 그리지 않고, 패널용 상세도 받지 않는다', async () => {
    setViewportWidth(375)
    renderApp('/')

    await listRow('CVE-2026-40117')

    expect(screen.queryByRole('complementary', { name: '선택한 취약점 상세' })).not.toBeInTheDocument()
    expect(server.requestsTo('/vuln-trend/data/cve/CVE-2026-40117.json?v=v1')).toHaveLength(0)
  })

  it('목록에서 취약점을 누르면 바로 상세 페이지로 간다', async () => {
    setViewportWidth(375)
    const user = renderApp('/')

    await user.click(await listRow('CVE-2026-41203'))

    expect(await screen.findByRole('heading', { level: 1, name: 'CVE-2026-41203' })).toBeInTheDocument()
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-41203')
  })
})

describe('푸터 고지 (5.11)', () => {
  it.each([
    ['메인', '/'],
    ['상세 페이지', '/cve/CVE-2026-41203'],
    ['없는 주소', '/없는-주소'],
  ])('%s 화면 맨 아래에 고지 푸터를 둔다', async (_name, path) => {
    renderApp(path)

    const footer = await screen.findByRole('contentinfo')
    expect(footer).toHaveTextContent('not endorsed or certified by the NVD')
  })
})
