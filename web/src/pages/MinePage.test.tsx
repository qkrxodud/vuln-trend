import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'
import { createDataClient } from '../api/data-client.ts'
import App from '../App.tsx'
import { DataProvider } from '../hooks/DataProvider.tsx'
import { MINE_STORAGE_KEY } from '../lib/mine.ts'
import { FakeFetch } from '../test/fake/fake-fetch.ts'
import { setViewportWidth } from '../test/fake/fake-media.ts'
import { cveDetail, metaWithVersion, stats } from '../test/fixtures.ts'
import { trendRow } from '../test/rows.ts'

const META = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }
const RECENT_URL = '/vuln-trend/data/recent.json?v=v1'
const SEARCH_URL = '/vuln-trend/data/search-index.json?v=v1'

const INDEX = [
  trendRow({ id: 'CVE-2026-1', score: 90, primary_product: 'GitLab', published_at: '2026-10-20T09:00:00+09:00' }),
  trendRow({ id: 'CVE-2026-2', score: 80, primary_product: 'PHP', published_at: '2026-10-21T09:00:00+09:00' }),
]
const RECENT = [
  trendRow({ id: 'CVE-2026-3', score: 30, primary_product: 'GitLab', published_at: '2026-11-01T10:00:00+09:00' }),
  trendRow({ id: 'CVE-2026-4', score: 40, primary_product: 'GitLab', published_at: '2026-10-30T10:00:00+09:00' }),
  trendRow({ id: 'CVE-2026-5', score: 50, primary_product: 'Apache', published_at: '2026-10-31T10:00:00+09:00' }),
]
const SEARCH = [
  { id: 'CVE-2026-3', title: 't3', product: 'GitLab' },
  { id: 'CVE-2025-9', title: '지난해 GitLab 문제', product: 'GitLab' },
  { id: 'CVE-2025-8', title: 'php old', product: 'PHP' },
  { id: 'CVE-2025-7', title: 'tomcat', product: 'Apache Tomcat' },
]

const LocationProbe = () => {
  const location = useLocation()
  return <output aria-label="현재 주소">{`${location.pathname}${location.search}`}</output>
}

let server: FakeFetch

const renderAt = (path: string, saved: { products: string[]; seenAt: string | null } | null = null) => {
  if (saved !== null) localStorage.setItem(MINE_STORAGE_KEY, JSON.stringify(saved))
  server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', META)
    .serve('/vuln-trend/data/stats.json?v=v1', stats)
    .serve('/vuln-trend/data/index.json?v=v1', INDEX)
    .serve(RECENT_URL, RECENT)
    .serve(SEARCH_URL, SEARCH)
    .serve('/vuln-trend/data/cve/CVE-2026-1.json?v=v1', { ...cveDetail, ...INDEX[0] })
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

afterEach(() => localStorage.clear())

const mineTab = () => within(screen.getByRole('navigation', { name: '화면' })).getByRole('link', { name: /^내 제품/ })
const minePage = () => screen.findByRole('heading', { level: 1, name: '내 제품' })

describe('내 제품 고르기 (목록·상세의 별표)', () => {
  it('내 제품이 없으면 메인은 recent.json 을 받지 않고, 내 제품 한 줄도 없다', async () => {
    renderAt('/')
    await screen.findByRole('region', { name: '지금 주목할 취약점' })

    expect(screen.queryByText(/내 제품 새 취약점/)).toBeNull()
    expect(server.requestsTo(RECENT_URL)).toHaveLength(0)
  })

  it('목록 행의 "☆ 내 제품"을 누르면 그 제품을 이 브라우저에 저장하고, 고른 때부터 새 건을 세므로 처음에는 0건이다', async () => {
    const user = renderAt('/')
    const list = await screen.findByRole('region', { name: '지금 주목할 취약점' })

    await user.click(within(list).getAllByRole('button', { name: 'GitLab 내 제품에 더하기' })[0])

    expect(within(list).getAllByRole('button', { name: 'GitLab 내 제품에서 빼기' })[0]).toHaveAttribute(
      'aria-pressed',
      'true',
    )
    expect(JSON.parse(localStorage.getItem(MINE_STORAGE_KEY) ?? '{}').products).toEqual(['GitLab'])
    expect(JSON.parse(localStorage.getItem(MINE_STORAGE_KEY) ?? '{}').seenAt).toBe('2026-11-01T18:49:00+09:00')
    expect(await screen.findByRole('link', { name: '내 제품 새 취약점 0건 보기 →' })).toHaveAttribute('href', '/mine')
    expect(mineTab()).toHaveAccessibleName('내 제품')
  })

  it('지난 방문 뒤 공개된 내 제품 취약점만 머리글 배지와 메인 한 줄에 센다', async () => {
    renderAt('/', { products: ['GitLab'], seenAt: '2026-10-29T00:00:00+09:00' })

    expect(await screen.findByRole('link', { name: '내 제품 새 취약점 2건 보기 →' })).toHaveAttribute('href', '/mine')
    expect(mineTab()).toHaveAccessibleName('내 제품 새 2건')
  })

  it('상세의 제품 배지 옆에서도 고를 수 있다', async () => {
    const user = renderAt('/')
    const panel = await screen.findByRole('complementary', { name: '선택한 취약점 상세' })

    await user.click(await within(panel).findByRole('button', { name: 'GitLab 내 제품에 더하기' }))

    expect(within(panel).getByRole('button', { name: 'GitLab 내 제품에서 빼기' })).toHaveAttribute(
      'aria-pressed',
      'true',
    )
  })
})

describe('/mine 내 제품 화면', () => {
  it('고른 제품이 없으면 무엇을 하는 기능인지, 고르는 방법, 이 브라우저에만 저장한다는 것을 알린다', async () => {
    renderAt('/mine')
    await minePage()

    expect(screen.getByText(/쓰는 제품을 고르면 그 제품의 취약점만 모아 봅니다/)).toBeInTheDocument()
    expect(screen.getByText('고른 제품은 이 브라우저에만 저장됩니다.')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '제품 찾기' })).toBeInTheDocument()
  })

  it('검색 상자로 제품을 더하면 주목 목록·최근 7일에서 그 제품 취약점을 점수순으로 모은다', async () => {
    const user = renderAt('/mine')
    await minePage()

    await user.type(screen.getByRole('combobox', { name: '제품 찾기' }), 'GitLab')
    await user.click(screen.getByRole('button', { name: '더하기' }))

    expect(screen.getByRole('list', { name: '고른 제품' })).toHaveTextContent('GitLab')
    const rows = within(screen.getByRole('list', { name: '내 제품 취약점' }))
      .getAllByRole('button', { name: /^CVE-\d{4}-\d+, 점수/ })
      .map((button) => button.getAttribute('data-id'))
    expect(rows).toEqual(['CVE-2026-1', 'CVE-2026-4', 'CVE-2026-3'])
  })

  it('최신순으로 바꿀 수 있고, 검색 색인에만 있는 지난 12개월 건은 NVD 원문 링크로 따로 보인다', async () => {
    const user = renderAt('/mine', { products: ['GitLab'], seenAt: null })
    await minePage()

    await user.click(screen.getByRole('button', { name: '최신순' }))
    expect(
      within(screen.getByRole('list', { name: '내 제품 취약점' }))
        .getAllByRole('button', { name: /^CVE-\d{4}-\d+, 점수/ })
        .map((button) => button.getAttribute('data-id')),
    ).toEqual(['CVE-2026-3', 'CVE-2026-4', 'CVE-2026-1'])
    const older = screen.getByRole('list', { name: '지난 12개월 내 제품 CVE (점수·상세 없음)' })
    expect(within(older).getByRole('link', { name: /CVE-2025-9/ })).toHaveAttribute(
      'href',
      'https://nvd.nist.gov/vuln/detail/CVE-2025-9',
    )
  })

  it('들어오면 본 때를 남겨, 다음에는 그 뒤 새것만 센다', async () => {
    renderAt('/mine', { products: ['GitLab'], seenAt: null })
    await minePage()

    expect(JSON.parse(localStorage.getItem(MINE_STORAGE_KEY) ?? '{}').seenAt).toBe('2026-11-01T18:49:00+09:00')
    await waitFor(() => expect(mineTab()).toHaveAccessibleName('내 제품'))
  })

  it('고른 제품을 ✕ 로 뺄 수 있다', async () => {
    const user = renderAt('/mine', { products: ['GitLab', 'PHP'], seenAt: null })
    await minePage()

    await user.click(screen.getByRole('button', { name: 'PHP 빼기' }))

    expect(screen.getByRole('list', { name: '고른 제품' })).not.toHaveTextContent('PHP')
  })

  it('데스크톱은 옆 패널에, 좁은 화면은 상세 페이지로 연다', async () => {
    const user = renderAt('/mine', { products: ['GitLab'], seenAt: null })
    await minePage()

    await user.click(screen.getByRole('button', { name: /^CVE-2026-1, 점수/ }))
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/mine?cve=CVE-2026-1')
    expect(await screen.findByRole('complementary', { name: '선택한 취약점 상세' })).toBeInTheDocument()
  })

  it('좁은 화면에서는 누르면 상세 페이지로 간다', async () => {
    setViewportWidth(390)
    const user = renderAt('/mine', { products: ['GitLab'], seenAt: null })
    await minePage()

    await user.click(screen.getByRole('button', { name: /^CVE-2026-4, 점수/ }))
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-4')
  })
})
