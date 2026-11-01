import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import App from '../../App.tsx'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import type { Meta } from '../../types/meta.ts'
import type { Stats } from '../../types/stats.ts'
import type { TrendRow } from '../../types/trend.ts'
import { FakeFetch } from '../fake/fake-fetch.ts'
import kev0 from './kev0.json'
import kevflood from './kevflood.json'
import poc from './poc.json'
import spike from './spike.json'

// design/v6 스트레스 변형(design/v6/build.py)의 줄인 사본. 극단 데이터에서도 화면 규칙이 지켜지는지 고정한다
interface StressData {
  meta: Meta
  stats: Stats
  index: TrendRow[]
}

const renderMain = (data: StressData) => {
  const meta = { ...data.meta, version: 'stress' }
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', meta)
    .serve('/vuln-trend/data/stats.json?v=stress', data.stats)
    .serve('/vuln-trend/data/index.json?v=stress', data.index)
  const view = render(
    <MemoryRouter initialEntries={['/']}>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <App />
      </DataProvider>
    </MemoryRouter>,
  )
  return view.container
}

const loaded = async () => {
  await screen.findByRole('region', { name: '지금 주목할 취약점' })
  await screen.findByRole('region', { name: '어느 쪽이 더 위험한가' })
  await screen.findByRole('region', { name: /^늘고 있나/ })
}

describe('KEV 0건 (kev0)', () => {
  it('빨강(기한 임박) 요소가 화면 어디에도 없고, 히어로는 KEV 가 없다고 알린다', async () => {
    const container = renderMain(kev0 as unknown as StressData)
    await loaded()

    expect(container.querySelectorAll('[class*="urgent"], [fill*="urgent"]')).toHaveLength(0)
    expect(screen.getByRole('article', { name: 'CISA 조치 기한 임박' })).toHaveTextContent(
      '주목 목록에 CISA KEV 가 없습니다',
    )
    expect(screen.queryByRole('button', { name: /^CISA 기한 임박/ })).toBeNull()
  })
})

describe('기한 임박 홍수 (kevflood)', () => {
  it('산점도 이름표는 상한 8건까지만 달고, 목록의 기한 임박 묶음은 10건 + 더 보기다', async () => {
    renderMain(kevflood as unknown as StressData)
    await loaded()
    const scatter = screen.getByRole('region', { name: '어느 쪽이 더 위험한가' })
    const list = screen.getByRole('region', { name: '지금 주목할 취약점' })

    expect(scatter.querySelectorAll('[data-label]').length).toBeLessThanOrEqual(8)
    expect(scatter).toHaveTextContent('가장 급한 8건만 이름을 달았습니다')
    const urgentTier = within(list).getByRole('region', { name: /^CISA 기한 임박 24건/ })
    expect(within(urgentTier).getAllByRole('button', { name: /^\d+위 / })).toHaveLength(10)
    expect(within(urgentTier).getByRole('button', { name: '기한 임박 14건 더 보기' })).toBeInTheDocument()
  })
})

describe('급증·빈 값·긴 이름 (spike)', () => {
  it('심각도·EPSS·제품이 비어도 화면이 멈추지 않고, 긴 제품명은 잘라 보이되 전체 이름을 남긴다', async () => {
    const container = renderMain(spike as unknown as StressData)
    await loaded()
    const longest = (spike as unknown as StressData).index
      .map((row) => row.primary_product ?? '')
      .sort((a, b) => b.length - a.length)[0]

    expect(screen.getByRole('region', { name: /^늘고 있나/ })).toHaveTextContent('급증했습니다')
    expect(
      within(screen.getByRole('region', { name: '지금 주목할 취약점' })).getAllByText('미평가', { exact: false })
        .length,
    ).toBeGreaterThan(0)
    expect(container.querySelector(`[title="${longest}"]`)).not.toBeNull()
  })
})

describe('PoC 다수 (poc)', () => {
  it('산점도 범례에 PoC 를 더하고, 목록에는 PoC 신호와 배지를 보인다', async () => {
    renderMain(poc as unknown as StressData)
    await loaded()

    expect(screen.getByRole('list', { name: '점 모양 설명' })).toHaveTextContent('PoC 공개 19건')
    expect(screen.getByRole('button', { name: 'PoC 공개 19' })).toBeInTheDocument()
    expect(screen.getAllByText(/^PoC \d+$/).length).toBeGreaterThan(0)
  })
})
