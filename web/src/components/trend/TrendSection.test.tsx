import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { ListFilterProvider } from '../../hooks/ListFilterProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { setViewportWidth } from '../../test/fake/fake-media.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { rankedRows, trendRow } from '../../test/rows.ts'
import type { TrendRow } from '../../types/trend.ts'
import { TrendSection } from './TrendSection.tsx'

// 기준일 2026-11-01 (갱신 18:49 KST)
const META = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }

const kev = (overrides: Partial<TrendRow> = {}) =>
  trendRow({ is_kev: true, kev_due_date: '2026-12-01', kev_added_at: '2026-09-01', ...overrides })

const LocationProbe = () => <output aria-label="현재 주소">{useLocation().pathname}</output>

const renderSection = (rows: TrendRow[], selectedId: string | null = null) => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', META)
    .serve('/vuln-trend/data/index.json?v=v1', rows)
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <ListFilterProvider>
          <TrendSection selectedId={selectedId} />
          <LocationProbe />
        </ListFilterProvider>
      </DataProvider>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

const tier = (name: RegExp) => screen.findByRole('region', { name })
const rowIds = (region: HTMLElement) =>
  within(region)
    .queryAllByRole('button', { name: /^\d+위 / })
    .map((button) => button.getAttribute('data-id'))

describe('지금 주목할 취약점 — 머리', () => {
  it('목록 범위와 기준 시각을 적고, 기한 임박을 먼저 보인다고 알린다', async () => {
    renderSection([trendRow(), trendRow()])

    expect(await screen.findByText('트렌드 점수 기준 상위 2건 · 기준 2026-11-01 18:49 KST')).toBeInTheDocument()
    expect(screen.getByText(/주목 목록/)).toHaveTextContent('주목 목록 2건 · 기한 임박을 먼저, 나머지는 점수 높은 순')
  })
})

describe('기한 임박 고정 묶음', () => {
  const rows = [
    kev({ id: 'CVE-2026-1', score: 90 }),
    kev({ id: 'CVE-2026-2', score: 40, kev_due_date: '2026-11-03' }),
    kev({ id: 'CVE-2026-3', score: 30, kev_due_date: '2026-11-01' }),
    trendRow({ id: 'CVE-2026-4', score: 60 }),
  ]

  it('기한 임박을 기한 빠른 순으로 맨 위에 고정하고, 나머지는 점수 높은 순으로 그 아래에 둔다', async () => {
    renderSection(rows)

    expect(rowIds(await tier(/^CISA 기한 임박 2건/))).toEqual(['CVE-2026-3', 'CVE-2026-2'])
    expect(rowIds(await tier(/^나머지 2건/))).toEqual(['CVE-2026-1', 'CVE-2026-4'])
  })

  it('기한 임박이 없으면 묶음을 숨기고 전체 한 묶음이다', async () => {
    renderSection([trendRow(), trendRow()])

    expect(await tier(/^목록 2건/)).toBeInTheDocument()
    expect(screen.queryByRole('region', { name: /CISA 기한 임박/ })).toBeNull()
  })

  it('기한 임박이 10건을 넘으면 10건만 보이고 나머지는 더 보기로 편다', async () => {
    const urgent = Array.from({ length: 12 }, (_, i) => kev({ score: 50 - i, kev_due_date: '2026-11-02' }))
    const user = renderSection(urgent)
    const region = await tier(/^CISA 기한 임박 12건/)

    expect(rowIds(region)).toHaveLength(10)
    await user.click(within(region).getByRole('button', { name: '기한 임박 2건 더 보기' }))

    expect(rowIds(region)).toHaveLength(12)
  })

  it('나머지는 20건씩 보이고, 더 보기에 남은 수와 진행을 적는다', async () => {
    const user = renderSection(rankedRows(25))
    const region = await tier(/^목록 25건/)

    expect(rowIds(region)).toHaveLength(20)
    await user.click(within(region).getByRole('button', { name: '5건 더 보기 (20/25)' }))

    expect(rowIds(region)).toHaveLength(25)
  })
})

describe('고른 행 펼치기 (KPI 히어로·주소에서 고를 때)', () => {
  it('고른 취약점이 더 보기 뒤(21위 아래)에 있으면 그 행까지 펼쳐 보인다', async () => {
    const rows = rankedRows(45)
    renderSection(rows, rows[32].id)
    const region = await tier(/^목록 45건/)

    expect(rowIds(region)).toHaveLength(40)
    expect(within(region).getByRole('button', { name: new RegExp(rows[32].id) })).toHaveAttribute(
      'aria-current',
      'true',
    )
  })

  it('고른 취약점이 기한 임박 10건 뒤에 있으면 기한 임박 묶음을 모두 펼친다', async () => {
    const urgent = Array.from({ length: 12 }, (_, i) => kev({ score: 50 - i, kev_due_date: '2026-11-02' }))
    renderSection(urgent, urgent[11].id)

    expect(rowIds(await tier(/^CISA 기한 임박 12건/))).toHaveLength(12)
  })
})

describe('신호로 거르기', () => {
  const rows = [
    kev({ id: 'CVE-2026-1', score: 90, kev_ransomware: true }),
    kev({ id: 'CVE-2026-2', score: 80, kev_added_at: '2026-10-30' }),
    kev({ id: 'CVE-2026-3', score: 70, kev_due_date: '2026-11-02' }),
    trendRow({ id: 'CVE-2026-4', score: 60 }),
  ]

  it('신호마다 건수를 적고, 0건인 신호(PoC)는 보이지 않는다', async () => {
    renderSection(rows)
    const signals = await screen.findByRole('group', { name: '신호로 거르기' })

    expect(
      within(signals)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['전체 4', 'CISA 기한 임박 1', '랜섬웨어 1', '새 KEV 7일 1'])
  })

  it('신호를 고르면 고정 묶음 없이 그 신호의 취약점만 점수순으로 보이고, 몇 건 중 몇 건인지 알린다', async () => {
    const user = renderSection(rows)
    const signals = await screen.findByRole('group', { name: '신호로 거르기' })

    await user.click(within(signals).getByRole('button', { name: '랜섬웨어 1' }))

    expect(within(signals).getByRole('button', { name: '랜섬웨어 1' })).toHaveAttribute('aria-pressed', 'true')
    expect(rowIds(await tier(/^랜섬웨어 1건/))).toEqual(['CVE-2026-1'])
    expect(screen.getByRole('status', { name: '목록 범위' })).toHaveTextContent('랜섬웨어 — 4건 중 1건')
  })

  it('필터 해제를 누르면 처음 보기로 돌아간다', async () => {
    const user = renderSection(rows)
    await user.click(await screen.findByRole('button', { name: '새 KEV 7일 1' }))

    await user.click(screen.getByRole('button', { name: '필터 해제 ✕' }))

    expect(await tier(/^CISA 기한 임박 1건/)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '전체 4' })).toHaveAttribute('aria-pressed', 'true')
  })
})

describe('제품으로 거르기', () => {
  const products = ['PHP', 'PHP', 'PHP', 'GitLab', 'GitLab', 'Apache', 'Cisco', 'Ivanti', 'Fortinet', null]
  const rows = products.map((product, i) =>
    trendRow({ id: `CVE-2026-${i + 1}`, score: 90 - i, primary_product: product }),
  )

  it('제품 막대와 같은 순서(건수 → 기한 임박 → 최고 점수)로 5개를 칩으로, 나머지는 "그 외 n개 제품" 선택 상자로 보인다', async () => {
    renderSection(rows)
    const chips = await screen.findByRole('group', { name: '제품으로 거르기' })

    expect(
      within(chips)
        .getAllByRole('button')
        .map((button) => button.textContent),
    ).toEqual(['모든 제품 10', 'PHP 3', 'GitLab 2', 'Apache 1', 'Cisco 1', 'Ivanti 1'])
    expect(within(chips).getByRole('combobox', { name: '그 외 제품 고르기' })).toHaveTextContent('그 외 2개 제품…')
  })

  it('칩이나 선택 상자로 제품을 고르면 그 제품만 보인다', async () => {
    const user = renderSection(rows)
    const chips = await screen.findByRole('group', { name: '제품으로 거르기' })

    await user.click(within(chips).getByRole('button', { name: 'GitLab 2' }))
    expect(rowIds(await tier(/^목록 2건/))).toEqual(['CVE-2026-4', 'CVE-2026-5'])

    await user.selectOptions(within(chips).getByRole('combobox', { name: '그 외 제품 고르기' }), '제품 미상')
    expect(rowIds(await tier(/^목록 1건/))).toEqual(['CVE-2026-10'])
    expect(screen.getByRole('status', { name: '목록 범위' })).toHaveTextContent('제품 미상 — 10건 중 1건')
  })
})

describe('목록 행', () => {
  it('순위·점수·ID·배지·제품·CISA 기한과 요약, 사실 칩을 보인다', async () => {
    renderSection([
      kev({
        id: 'CVE-2026-97229',
        score: 63,
        kev_due_date: '2026-11-01',
        kev_added_at: '2026-10-29',
        kev_ransomware: true,
        primary_product: 'GitLab',
        summary_what: '인증 없이 원격 코드가 실행됩니다.',
        severity: 'CRITICAL',
        cvss: 9.8,
        epss: 0.2731,
        epss_percentile: 0.996,
      }),
    ])
    const row = await screen.findByRole('button', { name: /^1위 CVE-2026-97229/ })

    expect(row).toHaveAccessibleName('1위 CVE-2026-97229, 점수 63점, GitLab, CISA 기한 D-0 임박, 랜섬웨어, 새 KEV')
    expect(row).toHaveTextContent('63점')
    expect(row).toHaveTextContent('랜섬웨어')
    expect(row).toHaveTextContent('새 KEV · 3일 전')
    expect(row).toHaveTextContent('CISA 기한 D-0')
    expect(row).toHaveTextContent('인증 없이 원격 코드가 실행됩니다.')
    expect(row).toHaveTextContent('Critical · CVSS 9.8')
    expect(row).toHaveTextContent('EPSS 27% (상위 1% 안)')
    expect(row).toHaveTextContent('CISA 등재 3일 전')
  })

  it('요약이 없으면 제목을, 제품이 없으면 "제품 미상"을, KEV 가 아니면 "KEV 아님"을, EPSS 가 없으면 "EPSS 없음"을 적는다', async () => {
    renderSection([
      trendRow({ title: 'Type confusion in V8', primary_product: null, epss: null, epss_percentile: null }),
    ])
    const row = await screen.findByRole('button', { name: /^1위 / })

    expect(row).toHaveTextContent('Type confusion in V8')
    expect(row).toHaveTextContent('제품 미상')
    expect(row).toHaveTextContent('KEV 아님')
    expect(row).toHaveTextContent('EPSS 없음')
    expect(row).not.toHaveTextContent('CISA 기한')
  })

  it('기한이 지났으면 지난 날 수를, 아직 멀면 D-n 을 적는다', async () => {
    renderSection([
      kev({ id: 'CVE-2026-1', score: 90, kev_due_date: '2026-10-29' }),
      kev({ id: 'CVE-2026-2', score: 80, kev_due_date: '2026-11-10' }),
    ])

    expect(await screen.findByRole('button', { name: /CVE-2026-1/ })).toHaveTextContent('CISA 기한 3일 지남')
    expect(screen.getByRole('button', { name: /CVE-2026-2/ })).toHaveTextContent('CISA 기한 D-9')
  })

  it('처음에는 목록 맨 위(기한 임박 첫 건)를 고른 상태로 두고, 행을 누르면 그 취약점을 고른다', async () => {
    const user = renderSection([
      kev({ id: 'CVE-2026-1', score: 90 }),
      kev({ id: 'CVE-2026-2', score: 10, kev_due_date: '2026-11-02' }),
    ])

    expect(await screen.findByRole('button', { name: /CVE-2026-2/ })).toHaveAttribute('aria-current', 'true')
    await user.click(screen.getByRole('button', { name: /CVE-2026-1/ }))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-1')
  })

  it('좁은 화면에서는 고른 표시 없이, 누르면 상세 페이지로 간다', async () => {
    setViewportWidth(390)
    const user = renderSection([trendRow({ id: 'CVE-2026-1' })])
    const row = await screen.findByRole('button', { name: /CVE-2026-1/ })

    expect(row).toHaveAttribute('aria-current', 'false')
    await user.click(row)

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-1')
  })
})

describe('목록 행의 조치 한 줄', () => {
  it('수정 버전 중 가장 높은 것과 나머지 수, 패치 공지 링크를 한 줄로 보인다', async () => {
    renderSection([
      trendRow({
        id: 'CVE-2026-97229',
        fix_versions: ['19.2.8', '19.4.2', '19.3.5'],
        patch_url: 'https://docs.gitlab.com/advisory',
      }),
    ])
    const line = await screen.findByText('19.4.2 이상으로 업데이트')

    expect(line.closest('p')).toHaveTextContent('→ 19.4.2 이상으로 업데이트 (외 2개) · 패치 공지 (새 창) ↗')
  })

  it('패치 공지 링크는 새 창으로 열고, 눌러도 그 행을 고르지 않는다', async () => {
    const user = renderSection([
      trendRow({ id: 'CVE-2026-1', score: 90 }),
      trendRow({ id: 'CVE-2026-2', score: 10, fix_versions: [], patch_url: 'https://vendor.example/patch' }),
    ])
    const link = await screen.findByRole('link', { name: /패치 공지/ })

    expect(link).toHaveAttribute('href', 'https://vendor.example/patch')
    expect(link).toHaveAttribute('target', '_blank')
    expect(link).toHaveAttribute('rel', 'noopener noreferrer')
    expect(link.closest('p')).toHaveTextContent('→ 패치 공지 (새 창) ↗')
    await user.click(link)
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent(/^\/$/)
  })

  it('수정 버전도 패치 링크도 없으면 줄을 숨긴다', async () => {
    renderSection([trendRow({ id: 'CVE-2026-1' })])
    await screen.findByRole('button', { name: /CVE-2026-1/ })

    expect(screen.queryByText(/^→/)).toBeNull()
  })
})

describe('빈 상태', () => {
  it('주목 목록이 비어 있으면 2시간마다 다시 확인한다고 알린다', async () => {
    renderSection([])

    expect(await screen.findByText(/이번 수집에서 주목할 취약점이 없습니다/)).toHaveTextContent(
      '2시간마다 다시 확인합니다.',
    )
    expect(screen.queryByText(/매일 09:00/)).toBeNull()
  })

  it('필터에 맞는 취약점이 없으면 필터 해제를 권한다', async () => {
    const user = renderSection([
      kev({ score: 90, kev_ransomware: true, primary_product: 'PHP' }),
      trendRow({ score: 50, primary_product: 'GitLab' }),
    ])
    await user.click(await screen.findByRole('button', { name: 'GitLab 1' }))
    await user.click(screen.getByRole('button', { name: '랜섬웨어 1' }))

    expect(screen.getByText(/조건에 맞는 취약점이 없습니다/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '필터 해제' }))
    expect(await tier(/^나머지|^목록/)).toBeInTheDocument()
  })
})
