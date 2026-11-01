import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { ListFilterProvider } from '../../hooks/ListFilterProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { setViewportWidth } from '../../test/fake/fake-media.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { trendRow } from '../../test/rows.ts'
import type { TrendRow } from '../../types/trend.ts'
import { TrendSection } from '../trend/TrendSection.tsx'
import { QuadrantCard } from './QuadrantCard.tsx'

// 기준일 2026-11-01
const META = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }

const ROWS: TrendRow[] = [
  trendRow({
    id: 'CVE-2026-97229',
    score: 63,
    primary_product: 'GitLab',
    is_kev: true,
    kev_due_date: '2026-11-01',
    epss: 0.27,
    cvss: 10,
  }),
  trendRow({
    id: 'CVE-2026-109618',
    score: 77,
    primary_product: 'PHP',
    is_kev: true,
    kev_due_date: '2026-10-29',
    epss: 0.99,
    cvss: 9.8,
  }),
  trendRow({ id: 'CVE-2026-63077', score: 13, primary_product: 'TeamCity', epss: 0.94, cvss: 9.8 }),
  trendRow({ id: 'CVE-2026-50001', score: 40, primary_product: 'Apache', epss: 0.02, cvss: 5 }),
  trendRow({ id: 'CVE-2026-50002', score: 30, primary_product: 'Zabbix', epss: null, cvss: null }),
  ...Array.from({ length: 3 }, (_, i) =>
    trendRow({ id: `CVE-2026-6000${i}`, score: 50 - i, primary_product: `Hot ${i}`, epss: 0.6, cvss: 8 }),
  ),
]

const renderGrid = (rows: TrendRow[] = ROWS) => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', META)
    .serve('/vuln-trend/data/index.json?v=v1', rows)
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <ListFilterProvider>
          <QuadrantCard />
          <TrendSection selectedId={null} />
        </ListFilterProvider>
      </DataProvider>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

const card = () => screen.findByRole('region', { name: '어느 쪽이 더 위험한가' })
const cell = (name: RegExp) => within(screen.getByRole('group', { name: '4칸 건수표' })).getByRole('button', { name })

describe('4칸 건수표', () => {
  it('부제에 ◆ 가 칸과 상관없이 먼저라는 것과 칸을 누르면 목록이 좁혀진다는 것을 적는다', async () => {
    renderGrid()

    expect(await card()).toHaveTextContent(
      '빨강 ◆(CISA 기한 3일 안)은 칸과 상관없이 먼저 · 칸을 누르면 아래 목록을 그 칸으로 좁힙니다 · 주목 8건 중 KEV 2건',
    )
  })

  it('열은 악용 가능성, 행은 심각도로 나누고 칸마다 건수를 크게 적는다', async () => {
    renderGrid()
    const region = await card()

    expect(region).toHaveTextContent('악용 가능성 낮음')
    expect(region).toHaveTextContent('악용 가능성 높음 (EPSS 30% 이상)')
    expect(region).toHaveTextContent('심각 (CVSS 7.0 이상)')
    expect(region).toHaveTextContent('덜 심각')
    expect(
      within(screen.getByRole('group', { name: '4칸 건수표' }))
        .getAllByRole('button')
        .map((button) => button.getAttribute('aria-label')),
    ).toEqual([
      '심각·악용 가능성 낮음 1건, 누르면 목록을 이 칸으로 좁힘',
      '악용 가능성 높음·심각 5건, 누르면 목록을 이 칸으로 좁힘',
      '둘 다 낮음 1건, 누르면 목록을 이 칸으로 좁힘',
      '악용 가능성 높음·덜 심각 0건, 누르면 목록을 이 칸으로 좁힘',
    ])
  })

  it('칸마다 대표 3건(◆ 기한 임박 먼저, 그다음 점수순)을 제품·번호 끝자리·D-n 으로 적고, 나머지는 "그 외 n건"이다', async () => {
    renderGrid()
    await card()
    const hot = cell(/^악용 가능성 높음·심각/)

    expect(
      within(hot)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['PHP 1096183일 지남', 'Hot 0 60000', 'Hot 1 60001'])
    expect(hot).toHaveTextContent('그 외 2건')
    expect(within(cell(/^심각·악용 가능성 낮음/)).getByRole('listitem')).toHaveTextContent('GitLab 97229D-0')
  })

  it('높음·심각 칸은 "먼저"로 강조하되 빨강은 쓰지 않는다 (빨강은 ◆ 기한 임박 전용)', async () => {
    renderGrid()
    await card()
    const hot = cell(/^악용 가능성 높음·심각/)

    expect(hot).toHaveTextContent('먼저')
    expect(hot.className).not.toMatch(/urgent/)
    expect(cell(/^심각·악용 가능성 낮음/).querySelector('[class*="bg-urgent"]')).not.toBeNull()
  })

  it('EPSS·CVSS 가 없는 건은 칸 밖에 "점수 없음 n건"으로 적는다', async () => {
    renderGrid()

    expect(await card()).toHaveTextContent('점수 없음 1건 (EPSS·CVSS 없음, 목록에는 있음)')
  })

  it('좁은 화면에서도 2×2 를 유지한다', async () => {
    setViewportWidth(390)
    renderGrid()
    await card()

    expect(screen.getByRole('group', { name: '4칸 건수표' })).toHaveClass('grid-cols-2')
  })

  it('주목 목록이 비어 있으면 그렇다고 적는다', async () => {
    renderGrid([])

    expect(await card()).toHaveTextContent('표시할 취약점이 없습니다')
  })
})

describe('칸으로 목록 거르기', () => {
  it('칸을 누르면 목록이 그 칸으로 좁혀지고 요약 줄에 칸 이름이 오르며, 다시 누르면 풀린다', async () => {
    const user = renderGrid()
    await card()

    await user.click(cell(/^악용 가능성 높음·심각/))
    expect(cell(/^악용 가능성 높음·심각/)).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('status', { name: '목록 범위' })).toHaveTextContent('악용 가능성 높음·심각 — 8건 중 5건')
    expect(screen.queryByRole('button', { name: /^\d+위 CVE-2026-50001/ })).toBeNull()

    await user.click(cell(/^악용 가능성 높음·심각/))
    expect(cell(/^악용 가능성 높음·심각/)).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('button', { name: /^\d+위 CVE-2026-50001/ })).toBeInTheDocument()
  })

  it('필터 해제는 칸 조건도 함께 푼다', async () => {
    const user = renderGrid()
    await card()
    await user.click(cell(/^둘 다 낮음/))
    await user.click(cell(/^심각·악용 가능성 낮음/))

    await user.click(screen.getByRole('button', { name: '필터 해제 ✕' }))

    expect(cell(/^심각·악용 가능성 낮음/)).toHaveAttribute('aria-pressed', 'false')
  })
})
