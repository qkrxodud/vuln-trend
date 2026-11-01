import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { setViewportWidth } from '../../test/fake/fake-media.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { trendRow } from '../../test/rows.ts'
import type { Meta } from '../../types/meta.ts'
import type { DailyCount } from '../../types/stats.ts'
import type { TrendRow } from '../../types/trend.ts'
import { KpiCards } from './KpiCards.tsx'

// 기준일 2026-11-01 (갱신 18:49 KST)
const META: Meta = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }
const DAILY: DailyCount[] = [
  { date: '2026-10-29', new: 500, critical: 30 },
  { date: '2026-10-30', new: 560, critical: 40 },
  { date: '2026-10-31', new: 530, critical: 35 },
  { date: '2026-11-01', new: 48, critical: 2 },
]

const kev = (id: string, due: string, overrides: Partial<TrendRow> = {}) =>
  trendRow({ id, is_kev: true, kev_due_date: due, ...overrides })

const LocationProbe = () => <output aria-label="현재 주소">{useLocation().pathname}</output>

const renderKpis = ({
  meta = META,
  rows = [] as TrendRow[],
  selectedId = null as string | null,
  daily = DAILY,
} = {}) => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', meta)
    .serve('/vuln-trend/data/stats.json?v=v1', {
      daily,
      severity_7d: { critical: 1, high: 1, medium: 1, low: 1, unrated: 1 },
    })
    .serve('/vuln-trend/data/index.json?v=v1', rows)
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <KpiCards selectedId={selectedId} />
        <LocationProbe />
      </DataProvider>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

const card = async (name: string) => screen.findByRole('article', { name })

describe('히어로: CISA 조치 기한 임박', () => {
  const rows = [
    kev('CVE-2026-1', '2026-11-03', { primary_product: 'Ivanti Connect Secure', score: 54 }),
    kev('CVE-2026-2', '2026-11-01', { primary_product: 'GitLab', score: 63 }),
    kev('CVE-2026-3', '2026-11-01', { primary_product: 'Cisco ASA·FTD', score: 54 }),
    kev('CVE-2026-4', '2026-11-04', { primary_product: null, score: 41 }),
    kev('CVE-2026-5', '2026-11-20', { score: 90 }),
    kev('CVE-2026-6', '2026-10-01', { score: 80 }),
    trendRow({ id: 'CVE-2026-7', score: 70 }),
  ]

  it('기한이 3일 안에 끝나는 KEV 건수를 크게 보이고, 주목 목록의 KEV·기한 지남 수를 함께 적는다', async () => {
    renderKpis({ rows })
    const hero = await card('CISA 조치 기한 임박')

    expect(within(hero).getByText('4')).toBeInTheDocument()
    expect(hero).toHaveTextContent('주목 7건 중 KEV 6건, 그중 기한 지남 1건')
  })

  it('가장 급한 3건을 기한 가까운 순(같으면 점수순)으로 D-n 과 제품·ID·점수로 보이고, 나머지는 목록에 있다고 알린다', async () => {
    renderKpis({ rows })
    const todo = await screen.findByRole('list', { name: '기한이 가까운 순서' })

    expect(
      within(todo)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual([
      'D-0GitLabCVE-2026-2점수 63',
      'D-0Cisco ASA·FTDCVE-2026-3점수 54',
      'D-2Ivanti Connect SecureCVE-2026-1점수 54',
    ])
    expect(await card('CISA 조치 기한 임박')).toHaveTextContent('외 1건은 아래 목록 맨 위에')
  })

  it('항목을 누르면 그 취약점을 고르고, 고른 항목은 표시가 남는다', async () => {
    const user = renderKpis({ rows, selectedId: 'CVE-2026-3' })
    const todo = await screen.findByRole('list', { name: '기한이 가까운 순서' })

    expect(within(todo).getByRole('button', { name: /CVE-2026-3/ })).toHaveAttribute('aria-current', 'true')
    await user.click(within(todo).getByRole('button', { name: /GitLab CVE-2026-2, CISA 기한 오늘/ }))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-2')
  })

  it('좁은 화면에서 누르면 상세 페이지로 간다', async () => {
    setViewportWidth(390)
    const user = renderKpis({ rows })

    await user.click(
      await screen.findByRole('button', { name: /Ivanti Connect Secure CVE-2026-1, CISA 기한 2일 남음/ }),
    )

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-1')
  })

  it('임박한 기한이 없으면 그다음으로 가까운 기한을 알린다', async () => {
    renderKpis({ rows: [kev('CVE-2026-5', '2026-11-08', { primary_product: 'PHP' }), kev('CVE-2026-6', '2026-10-01')] })
    const hero = await card('CISA 조치 기한 임박')

    expect(within(hero).getByText('0')).toBeInTheDocument()
    expect(hero).toHaveTextContent(
      '3일 안에 끝나는 CISA 기한은 없습니다. 가장 가까운 기한은 D-7 PHP CVE-2026-5 입니다.',
    )
    expect(within(hero).queryByRole('list')).toBeNull()
  })

  it('주목 목록에 KEV 가 하나도 없으면 점수 순서대로 보면 된다고 알린다', async () => {
    renderKpis({ rows: [trendRow(), trendRow()] })

    expect(await card('CISA 조치 기한 임박')).toHaveTextContent('주목 목록에 CISA KEV 가 없습니다')
  })
})

describe('오늘 새로 나온 CVE', () => {
  it('24시간 신규를 보이고, 집계 중임과 갱신 시각·어제 하루 값·평소(완결일 중앙값)를 함께 적는다', async () => {
    renderKpis()
    const today = await card('오늘 새로 나온 CVE')

    expect(today).toHaveTextContent(`${META.today.new}건`)
    expect(today).toHaveTextContent('집계 중')
    expect(today).toHaveTextContent(`18:49 KST 기준 · 어제 하루 ${META.yesterday.new}건`)
    expect(today).toHaveTextContent('평소(3일 중앙값) 530건')
    expect(today).toHaveTextContent('점선 = 오늘(집계 중)')
  })
  it('갱신이 다음 날로 넘어간 데이터면 마지막 날도 완결이라 집계 중 표시가 없다', async () => {
    renderKpis({ meta: { ...META, updated_at: '2026-11-02T09:00:00+09:00' } })
    const today = await card('오늘 새로 나온 CVE')

    expect(today).not.toHaveTextContent('집계 중')
    expect(today).toHaveTextContent('평소(4일 중앙값)')
    expect(today).not.toHaveTextContent('점선 = 오늘')
  })

  it('일별 통계가 하루뿐이면 평소와 14일 선을 그리지 않는다', async () => {
    renderKpis({ daily: [DAILY[3]] })
    const today = await card('오늘 새로 나온 CVE')

    expect(today).not.toHaveTextContent('평소(')
    expect(today).not.toHaveTextContent('가로선 평소')
  })
})

describe('최근 7일 새로 KEV 등재', () => {
  it('배치의 전체 KEV 집계가 있으면 그 값과 그 전 7일 값을 보이고, 기준이 다른 24시간 등재 수는 섞지 않는다', async () => {
    renderKpis({ meta: { ...META, kev_added_7d: 9, kev_added_prev_7d: 8 } })
    const added = await card('최근 7일 새로 KEV 등재')

    expect(added).toHaveTextContent('9건')
    expect(added).toHaveTextContent('그 전 7일 8건')
    expect(added).not.toHaveTextContent('24시간')
    expect(added).toHaveTextContent('CISA 가 실제 악용을 확인해 새로 올린 것 · 전체 KEV 기준')
  })

  it('전체 집계가 없으면 주목 목록 행의 등재일로 세고 그 범위를 밝힌다', async () => {
    renderKpis({
      rows: [
        kev('CVE-2026-1', '2026-11-20', { kev_added_at: '2026-10-30' }),
        kev('CVE-2026-2', '2026-11-20', { kev_added_at: '2026-10-20' }),
      ],
    })
    const added = await card('최근 7일 새로 KEV 등재')

    expect(added).toHaveTextContent('1건')
    expect(added).toHaveTextContent('그 전 7일 1건')
    expect(added).toHaveTextContent('주목 목록 기준')
  })

  it('등재일을 하나도 모르면 "등재일 정보 없음"으로 적고, 아는 것은 최근 24시간 등재 수뿐이라고 밝힌다', async () => {
    renderKpis({ rows: [kev('CVE-2026-1', '2026-11-20')] })
    const added = await card('최근 7일 새로 KEV 등재')

    expect(added).toHaveTextContent('등재일 정보 없음')
    expect(added).toHaveTextContent(`최근 24시간 등재 ${META.today.kev_new}건`)
  })
})
