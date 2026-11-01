import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { ListFilterProvider } from '../../hooks/ListFilterProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { trendRow } from '../../test/rows.ts'
import type { TrendRow } from '../../types/trend.ts'
import { ProductFilter } from '../watch/ListFilters.tsx'
import { ProductBars } from './ProductBars.tsx'

const META = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }

const row = (product: string | null, overrides: Partial<TrendRow> = {}) =>
  trendRow({ primary_product: product, severity: 'HIGH', ...overrides })

// 상위: Linux Kernel 3(Critical 1) · Plex 2(기한 임박 1) · Windows · Apache · Zabbix / 그 외: Cisco·Fortinet·Ivanti 1건씩
const ROWS = [
  row('Linux Kernel', { severity: 'CRITICAL' }),
  row('Linux Kernel'),
  row('Linux Kernel'),
  row('Plex Media Server', { is_kev: true, kev_due_date: '2026-11-02' }),
  row('Plex Media Server', { severity: 'MEDIUM' }),
  row('Windows', { score: 90 }),
  row('Apache', { score: 80 }),
  row('Zabbix', { score: 70 }),
  row('Cisco'),
  row('Fortinet'),
  row('Ivanti'),
]

const renderBars = (rows: TrendRow[] = ROWS) => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', META)
    .serve('/vuln-trend/data/index.json?v=v1', rows)
  render(
    <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
      <ListFilterProvider>
        <ProductBars />
        <ProductFilter rows={rows} today="2026-11-01" />
      </ListFilterProvider>
    </DataProvider>,
  )
  return userEvent.setup()
}

const card = () => screen.findByRole('region', { name: /^어디에 몰려 있나/ })

describe('어디에 몰려 있나', () => {
  it('제목에 답을 쓰고, 상위 제품이 차지하는 몫과 나머지를 부제로 밝힌다', async () => {
    renderBars()

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent(
      '어디에 몰려 있나 — 몇 제품에 몰려 있습니다',
    )
    expect(await card()).toHaveTextContent(
      '많은 5개 제품이 주목 11건 중 8건(73%) · 나머지 3개 제품이 3건 · 제품을 누르면 아래 목록을 좁힙니다',
    )
  })

  it('고르게 퍼져 있으면 그렇게 답한다', async () => {
    renderBars(Array.from({ length: 30 }, (_, i) => row(`P${i}`)))

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent(
      '어디에 몰려 있나 — 고르게 퍼져 있습니다',
    )
  })

  it('범례에 실제로 있는 심각도와 그 외 합계, 기한 임박 표시를 적는다', async () => {
    renderBars()
    const legend = within(await card()).getByRole('list', { name: '막대 색 설명' })

    expect(
      within(legend)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['Critical', 'High', 'Medium', '그 외 제품 합계', 'CISA 기한 3일 안 건수'])
  })

  it('상위 5개 제품을 건수·Critical 수·기한 임박 수와 함께 보이고, 그 외는 누를 수 없는 한 줄로 묶는다', async () => {
    renderBars()
    const bars = within(await card()).getByRole('list', { name: '제품별 건수' })

    expect(within(bars).getByRole('button', { name: /^Linux Kernel:/ })).toHaveAccessibleName(
      'Linux Kernel: 3건(Critical 1, High 2), 누르면 목록을 이 제품으로 좁힘',
    )
    expect(within(bars).getByRole('button', { name: /^Plex Media Server:/ })).toHaveTextContent('D-3 안 1')
    expect(within(bars).getByRole('button', { name: /^Linux Kernel:/ })).toHaveTextContent('3건 · Critical 1')
    expect(within(bars).getAllByRole('button')).toHaveLength(5)
    expect(within(bars).getByText('그 외 3개 제품').closest('li')).toHaveTextContent(
      '그 외 3개 제품제품마다 1건 이하3건',
    )
  })
})

describe('목록 필터와 함께 쓴다', () => {
  it('막대를 누르면 목록의 제품 필터가 걸리고, 다시 누르면 풀린다', async () => {
    const user = renderBars()
    const bars = within(await card()).getByRole('list', { name: '제품별 건수' })
    const chips = screen.getByRole('group', { name: '제품으로 거르기' })

    await user.click(within(bars).getByRole('button', { name: /^Plex Media Server:/ }))
    expect(within(bars).getByRole('button', { name: /^Plex Media Server:/ })).toHaveAttribute('aria-pressed', 'true')
    expect(within(chips).getByRole('button', { name: 'Plex Media Server 2' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('목록을 Plex Media Server 로 좁힌 상태')).toBeInTheDocument()

    await user.click(within(bars).getByRole('button', { name: /^Plex Media Server:/ }))
    expect(within(chips).getByRole('button', { name: /^모든 제품/ })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('전체 제품 표시 중')).toBeInTheDocument()
  })

  it('목록 칩으로 고른 제품도 막대에 눌린 상태로 보이고, 필터 해제로 함께 푼다', async () => {
    const user = renderBars()
    const region = await card()

    await user.click(
      within(screen.getByRole('group', { name: '제품으로 거르기' })).getByRole('button', { name: 'Linux Kernel 3' }),
    )
    expect(within(region).getByRole('button', { name: /^Linux Kernel:/ })).toHaveAttribute('aria-pressed', 'true')

    await user.click(within(region).getByRole('button', { name: '필터 해제 ✕' }))
    expect(within(region).getByRole('button', { name: /^Linux Kernel:/ })).toHaveAttribute('aria-pressed', 'false')
    expect(within(region).queryByRole('button', { name: '필터 해제 ✕' })).toBeNull()
  })
})

describe('빈 상태', () => {
  it('주목 목록이 비어 있으면 그렇다고 적는다', async () => {
    renderBars([])

    expect(await screen.findByText('표시할 제품이 없습니다')).toBeInTheDocument()
  })
})
