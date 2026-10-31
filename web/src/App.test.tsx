import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from './api/data-client.ts'
import App from './App.tsx'
import { DataProvider } from './hooks/DataProvider.tsx'
import { TrendFilterProvider } from './hooks/TrendFilterProvider.tsx'
import { FakeFetch } from './test/fake/fake-fetch.ts'
import { cveDetail, metaWithVersion, stats } from './test/fixtures.ts'
import { trendRow } from './test/rows.ts'

const first = trendRow({ id: 'CVE-2026-40117', score: 91, title: '첫 번째 취약점' })
const second = trendRow({ id: 'CVE-2026-41203', score: 84, title: '두 번째 취약점' })

const LocationProbe = () => <output aria-label="현재 주소">{useLocation().pathname}</output>

const renderApp = (path: string) => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
    .serve('/vuln-trend/data/stats.json?v=v1', stats)
    .serve('/vuln-trend/data/index.json?v=v1', [first, second])
    .serve('/vuln-trend/data/cve/CVE-2026-40117.json?v=v1', { ...cveDetail, ...first })
    .serve('/vuln-trend/data/cve/CVE-2026-41203.json?v=v1', { ...cveDetail, ...second })
  render(
    <MemoryRouter initialEntries={[path]}>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <TrendFilterProvider>
          <App />
          <LocationProbe />
        </TrendFilterProvider>
      </DataProvider>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

const panelTitle = async () => {
  const panel = await screen.findByRole('complementary', { name: '선택한 취약점 상세' })
  return (await screen.findAllByRole('heading', { level: 3 })).find((heading) => panel.contains(heading))
}

describe('메인 화면과 상세 패널 (D8)', () => {
  it('처음에는 목록 맨 위 취약점의 상세를 옆 패널에 보인다', async () => {
    renderApp('/')

    expect(await panelTitle()).toHaveTextContent('첫 번째 취약점')
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent(/^\/$/)
  })

  it('목록에서 취약점을 고르면 주소가 /cve/ID 로 바뀌고 옆 패널에 그 상세가 열린다', async () => {
    const user = renderApp('/')

    await user.click(await screen.findByRole('button', { name: /CVE-2026-41203/ }))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-41203')
    expect(await screen.findByText('CVE-2026-41203 상세 표시 중')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /CVE-2026-41203/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('점수순으로 보던 중에 취약점을 골라도 보기가 그대로 남는다', async () => {
    const user = renderApp('/')
    await user.click(await screen.findByRole('button', { name: '점수순' }))

    await user.click(screen.getByRole('button', { name: /CVE-2026-41203/ }))

    expect(screen.getByRole('button', { name: '점수순' })).toHaveAttribute('aria-pressed', 'true')
  })

  it('/cve/ID 주소로 바로 들어오면 상세 페이지를 보인다', async () => {
    renderApp('/cve/CVE-2026-41203')

    expect(await screen.findByRole('heading', { level: 1, name: 'CVE-2026-41203' })).toBeInTheDocument()
    expect(screen.queryByRole('complementary', { name: '선택한 취약점 상세' })).not.toBeInTheDocument()
  })
})
