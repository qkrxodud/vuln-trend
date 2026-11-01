import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../api/data-client.ts'
import App from '../App.tsx'
import { DataProvider } from '../hooks/DataProvider.tsx'
import { FakeFetch } from '../test/fake/fake-fetch.ts'
import { setViewportWidth } from '../test/fake/fake-media.ts'
import { categoriesFixture } from '../test/categories.ts'
import { metaWithVersion, stats } from '../test/fixtures.ts'
import { trendRow } from '../test/rows.ts'
import type { TrendRow } from '../types/trend.ts'

const META = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }
const RECENT_URL = '/vuln-trend/data/recent.json?v=v1'
const STATS_TOTAL = 54 + 231 + 302 + 41 + 236

const recentRows = (count: number, overrides: (index: number) => Partial<TrendRow> = () => ({})): TrendRow[] =>
  Array.from({ length: count }, (_, i) =>
    trendRow({ id: `CVE-2026-${70000 + i}`, score: (i * 7) % 100, title: `Recent issue ${i}`, ...overrides(i) }),
  )

const LocationProbe = () => {
  const location = useLocation()
  return <output aria-label="현재 주소">{`${location.pathname}${location.search}`}</output>
}

let server: FakeFetch

const renderAt = (path: string, recent: TrendRow[] | null = recentRows(3), recentStatus = 200) => {
  server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', META)
    .serve('/vuln-trend/data/stats.json?v=v1', stats)
    .serve('/vuln-trend/data/index.json?v=v1', [trendRow({ id: 'CVE-2026-1' })])
  if (recent !== null) server.serve(RECENT_URL, recent, recentStatus)
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

const page = () => screen.findByRole('heading', { level: 1, name: '최근 7일 새 CVE' })
const rows = () =>
  within(screen.getByRole('list', { name: '최근 7일 새 CVE 목록' }))
    .getAllByRole('button', { name: /^CVE-\d{4}-\d+, 점수/ })
    .map((button) => button.getAttribute('data-id'))

describe('메인과 오가기', () => {
  it('메인에서는 recent.json 을 받지 않고, 주목 목록 아래에 최근 7일 새 CVE 입구를 둔다', async () => {
    renderAt('/')

    const entry = await screen.findByRole('link', { name: `최근 7일 새 CVE ${STATS_TOTAL}건 모두 보기 →` })
    expect(entry).toHaveAttribute('href', '/new')
    expect(server.requestsTo(RECENT_URL)).toHaveLength(0)
  })

  it('머리글에 두 화면 링크를 두고, 지금 화면을 표시한다', async () => {
    renderAt('/new')
    await page()
    const nav = screen.getByRole('navigation', { name: '화면' })

    expect(within(nav).getByRole('link', { name: '최근 7일 새 CVE' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: '지금 주목할 취약점' })).toHaveAttribute('href', '/')
    expect(server.requestsTo(RECENT_URL)).toHaveLength(1)
  })
})

describe('최근 7일 새 CVE 화면', () => {
  it('행에는 순위를 적지 않는다 (7일 전부라 순위가 뜻이 없음)', async () => {
    renderAt('/new', recentRows(2))
    await page()

    expect(screen.queryByText(/^\d+위$/)).toBeNull()
  })

  it('건수와 기준 시각, 한국어 요약은 점수 상위 건만이라는 안내를 적는다', async () => {
    renderAt('/new', recentRows(3))
    await page()

    expect(screen.getByText(/^3건 · 최근 7개 한국 달력일 공개 · 기준 2026-11-01 18:49 KST/)).toBeInTheDocument()
    expect(
      screen.getByText('한국어 요약은 점수 상위 건에만 있습니다. 나머지는 NVD 영문 제목으로 보입니다.'),
    ).toBeInTheDocument()
  })

  it('50건씩 보이고, 더 보기로 이어서 편다', async () => {
    const user = renderAt('/new', recentRows(120))
    await page()

    expect(rows()).toHaveLength(50)
    await user.click(screen.getByRole('button', { name: '50건 더 보기 (50/120)' }))
    expect(rows()).toHaveLength(100)
  })

  it('최신순(파일 순서)이 기본이고, 점수순으로 바꿀 수 있다', async () => {
    const user = renderAt('/new', recentRows(4))
    await page()

    expect(rows()).toEqual(['CVE-2026-70000', 'CVE-2026-70001', 'CVE-2026-70002', 'CVE-2026-70003'])
    await user.click(screen.getByRole('button', { name: '점수순' }))
    expect(rows()).toEqual(['CVE-2026-70003', 'CVE-2026-70002', 'CVE-2026-70001', 'CVE-2026-70000'])
  })

  it('심각도·KEV 만·조치 정보 있음·제품으로 거르고, 몇 건 중 몇 건인지 알리며, 필터 해제로 돌아간다', async () => {
    const user = renderAt(
      '/new',
      recentRows(6, (i) => ({
        severity: i < 2 ? 'CRITICAL' : 'MEDIUM',
        is_kev: i === 1,
        fix_versions: i === 0 ? ['1.2.3'] : [],
        primary_product: i < 3 ? 'GitLab' : 'Apache',
      })),
    )
    await page()

    await user.click(screen.getByRole('button', { name: 'Critical 2' }))
    expect(rows()).toEqual(['CVE-2026-70000', 'CVE-2026-70001'])
    expect(screen.getByRole('status', { name: '목록 범위' })).toHaveTextContent('6건 중 2건')

    await user.click(screen.getByRole('checkbox', { name: 'KEV 만' }))
    expect(rows()).toEqual(['CVE-2026-70001'])

    await user.click(screen.getByRole('button', { name: '필터 해제 ✕' }))
    await user.click(screen.getByRole('checkbox', { name: '조치 정보 있음' }))
    expect(rows()).toEqual(['CVE-2026-70000'])

    await user.click(screen.getByRole('button', { name: '필터 해제 ✕' }))
    await user.type(screen.getByRole('combobox', { name: '제품' }), 'apa')
    expect(rows()).toEqual(['CVE-2026-70003', 'CVE-2026-70004', 'CVE-2026-70005'])
  })

  it('공개 시각이 있으면 날짜로 거를 수 있고, 없으면 날짜 선택을 숨긴다', async () => {
    const user = renderAt(
      '/new',
      recentRows(3, (i) => ({ published_at: i === 0 ? '2026-11-01T09:00:00+09:00' : '2026-10-31T09:00:00+09:00' })),
    )
    await page()

    await user.selectOptions(screen.getByRole('combobox', { name: '공개일' }), '2026-10-31')
    expect(rows()).toEqual(['CVE-2026-70001', 'CVE-2026-70002'])
  })

  it('공개 시각이 없는 데이터에서는 날짜 선택이 없다', async () => {
    renderAt('/new', recentRows(3))
    await page()

    expect(screen.queryByRole('combobox', { name: '공개일' })).toBeNull()
  })

  it('데스크톱에서 행을 누르면 옆 패널에 그 상세를 보이고 주소에 남긴다', async () => {
    const user = renderAt('/new', recentRows(3))
    await page()

    await user.click(screen.getByRole('button', { name: /^CVE-2026-70002, 점수/ }))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/new?cve=CVE-2026-70002')
    expect(screen.getByRole('button', { name: /^CVE-2026-70002, 점수/ })).toHaveAttribute('aria-current', 'true')
    expect(await screen.findByRole('complementary', { name: '선택한 취약점 상세' })).toHaveTextContent('CVE-2026-70002')
  })

  it('좁은 화면에서 행을 누르면 상세 페이지로 간다', async () => {
    setViewportWidth(390)
    const user = renderAt('/new', recentRows(3))
    await page()

    await user.click(screen.getByRole('button', { name: /^CVE-2026-70001, 점수/ }))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-70001')
  })
})

describe('빈 상태와 실패', () => {
  it('최근 7일 새 CVE 가 없으면 2시간마다 다시 확인한다고 알린다', async () => {
    renderAt('/new', [])
    await page()

    expect(screen.getByText(/최근 7일 동안 새로 공개된 CVE 가 없습니다/)).toHaveTextContent(
      '2시간마다 다시 확인합니다.',
    )
  })

  it('거른 결과가 없으면 필터 해제를 권한다', async () => {
    const user = renderAt('/new', recentRows(2))
    await page()

    await user.type(screen.getByRole('combobox', { name: '제품' }), 'zzz')

    expect(screen.getByText('조건에 맞는 CVE 가 없습니다.')).toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /필터 해제/ }).length).toBeGreaterThan(0)
  })

  it('불러오기에 실패하면 다시 시도할 수 있다', async () => {
    renderAt('/new', [], 500)

    expect(await screen.findByRole('button', { name: /다시 시도/ })).toBeInTheDocument()
  })
})

describe('분류로 거르기 (공격 유형·제품군)', () => {
  const classified = () => [
    trendRow({ id: 'CVE-2026-801', attack_types: ['rce'], product_family: 'network' }),
    trendRow({ id: 'CVE-2026-802', attack_types: ['xss'], product_family: 'web_cms' }),
    trendRow({ id: 'CVE-2026-803', attack_types: [], product_family: null }),
  ]
  const withCategories = (path: string) => {
    const user = renderAt(path, classified())
    server.serve('/vuln-trend/data/categories.json?v=v1', categoriesFixture())
    return user
  }

  it('배치가 준 분류 이름으로 고르고, 최근 7일 안 건수와 "분류 전"을 함께 보인다', async () => {
    const user = withCategories('/new')
    await page()
    const type = screen.getByRole('combobox', { name: '공격 유형' })

    expect(within(type).getByRole('option', { name: '원격 코드 실행 (1)' })).toBeInTheDocument()
    expect(within(type).getByRole('option', { name: '분류 전 (1)' })).toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: '제품군' }), 'web_cms')

    expect(rows()).toEqual(['CVE-2026-802'])
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/new?family=web_cms')
    expect(screen.getByText(/NVD 가 아직 분석하지 않은 새 CVE 는 분류 전입니다/)).toBeInTheDocument()
  })

  it('분류 화면에서 /new?type=키 로 들어오면 그 분류로 거른 채 시작하고, 골라도 거른 상태가 주소에 남는다', async () => {
    const user = withCategories('/new?type=rce')
    await page()

    expect(screen.getByRole('combobox', { name: '공격 유형' })).toHaveValue('rce')
    expect(rows()).toEqual(['CVE-2026-801'])
    await user.click(screen.getByRole('button', { name: /^CVE-2026-801, 점수/ }))
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/new?type=rce&cve=CVE-2026-801')
  })

  it('분류 파일이 아직 없으면 분류 필터 없이 그대로 쓴다', async () => {
    renderAt('/new', classified())
    await page()

    expect(screen.queryByRole('combobox', { name: '공격 유형' })).toBeNull()
    expect(rows()).toHaveLength(3)
  })
})
