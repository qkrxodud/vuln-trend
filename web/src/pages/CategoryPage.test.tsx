import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../api/data-client.ts'
import App from '../App.tsx'
import { DataProvider } from '../hooks/DataProvider.tsx'
import { categoriesFixture } from '../test/categories.ts'
import { FakeFetch } from '../test/fake/fake-fetch.ts'
import { setViewportWidth } from '../test/fake/fake-media.ts'
import { cveDetail, metaWithVersion, stats } from '../test/fixtures.ts'
import { trendRow } from '../test/rows.ts'
import type { Categories } from '../types/categories.ts'

const META = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }
const CATEGORIES_URL = '/vuln-trend/data/categories.json?v=v1'
const RCE_TOP = [
  trendRow({ id: 'CVE-2026-901', score: 91, attack_types: ['rce'], product_family: 'network' }),
  trendRow({ id: 'CVE-2026-902', score: 70, attack_types: ['rce', 'memory'], product_family: 'os' }),
]
const CATEGORIES = categoriesFixture({
  rce: { count_7d: 12, count_30d: 40, kev_count: 3, top: RCE_TOP },
  xss: { count_7d: 30, count_30d: 90, kev_count: 1 },
  network: { count_7d: 2, count_30d: 7, kev_count: 29, top: [RCE_TOP[0]] },
})

const LocationProbe = () => {
  const location = useLocation()
  return <output aria-label="현재 주소">{`${location.pathname}${location.search}`}</output>
}

let server: FakeFetch

const renderAt = (path: string, categories: Categories | null = CATEGORIES) => {
  server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', META)
    .serve('/vuln-trend/data/stats.json?v=v1', stats)
    .serve('/vuln-trend/data/index.json?v=v1', [trendRow({ id: 'CVE-2026-1' })])
  RCE_TOP.forEach((row) => server.serve(`/vuln-trend/data/cve/${row.id}.json?v=v1`, { ...cveDetail, ...row }))
  if (categories !== null) server.serve(CATEGORIES_URL, categories)
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

const browse = () => screen.findByRole('region', { name: '분류로 보기' })

describe('메인 "분류로 보기"', () => {
  it('공격 유형·제품군 칩마다 최근 7일 건수를 적고, 그 분류 화면으로 간다', async () => {
    renderAt('/')
    const region = await browse()
    const types = await within(region).findByRole('group', { name: '공격 유형' })

    const rce = within(types).getByRole('link', { name: '원격 코드 실행 최근 7일 12건' })
    expect(rce).toHaveAttribute('href', '/type/rce')
    expect(
      within(within(region).getByRole('group', { name: '제품군' })).getByRole('link', {
        name: '네트워크·보안 장비 최근 7일 2건',
      }),
    ).toHaveAttribute('href', '/family/network')
  })

  it('건수가 많은 분류를 앞에 두고, 0건 분류는 숨기지 않고 흐리게 뒤에 둔다', async () => {
    renderAt('/')
    const types = await within(await browse()).findByRole('group', { name: '공격 유형' })
    const links = within(types).getAllByRole('link')

    expect(links[0]).toHaveAccessibleName('크로스 사이트 스크립팅 최근 7일 30건')
    expect(links).toHaveLength(10)
    expect(links.at(-1)).toHaveAccessibleName('기타 최근 7일 0건')
    expect(links.at(-1)).toHaveAttribute('data-quiet', 'true')
  })

  it('배치가 분류를 아직 내보내지 않았으면 같은 자리에 다음 갱신부터 보인다고 알린다', async () => {
    renderAt('/', null)

    expect(await within(await browse()).findByText(/분류는 다음 갱신부터 보입니다/)).toBeInTheDocument()
  })
})

describe('메인 "분류로 보기" — 받지 못했을 때', () => {
  it('분류 파일을 받지 못하면 같은 자리에서 알리고, 다시 시도하면 칩을 보인다', async () => {
    const user = renderAt('/', null)
    server.serve(CATEGORIES_URL, {}, 500).serve(CATEGORIES_URL, CATEGORIES)
    const region = await browse()

    await user.click(await within(region).findByRole('button', { name: '다시 시도' }))

    expect(await within(region).findByRole('group', { name: '공격 유형' })).toBeInTheDocument()
  })
})

describe('분류 화면 /type/<key>, /family/<key>', () => {
  it('분류 이름·건수와 트렌드 점수 상위 목록을 보이고, 최근 7일 새 CVE 로 이어 준다', async () => {
    renderAt('/type/rce')

    expect(await screen.findByRole('heading', { level: 1, name: '원격 코드 실행' })).toBeInTheDocument()
    expect(screen.getByText('최근 7일 12건 · 30일 40건 · KEV 누적 3건')).toBeInTheDocument()
    const list = screen.getByRole('list', { name: '원격 코드 실행 트렌드 점수 상위' })
    expect(
      within(list)
        .getAllByRole('button', { name: /^\d+위 CVE-/ })
        .map((row) => row.dataset.id),
    ).toEqual(['CVE-2026-901', 'CVE-2026-902'])
    expect(screen.getByRole('link', { name: '최근 7일 원격 코드 실행 12건 모두 보기 →' })).toHaveAttribute(
      'href',
      '/new?type=rce',
    )
    expect(screen.getByText(/NVD 가 아직 분석하지 않은 새 CVE 는 분류 전입니다/)).toBeInTheDocument()
  })

  it('같은 축의 다른 분류로 옮겨 갈 수 있고, 지금 분류를 표시한다', async () => {
    renderAt('/family/network')
    await screen.findByRole('heading', { level: 1, name: '네트워크·보안 장비' })
    const others = screen.getByRole('group', { name: '다른 제품군' })

    expect(within(others).getByRole('link', { name: /^네트워크·보안 장비/ })).toHaveAttribute('aria-current', 'page')
    expect(within(others).getByRole('link', { name: '운영체제 최근 7일 0건' })).toHaveAttribute('href', '/family/os')
  })

  it('데스크톱은 맨 위 건을 옆 패널에 열고, 고르면 주소에 남긴다', async () => {
    const user = renderAt('/type/rce')
    await screen.findByRole('heading', { level: 1, name: '원격 코드 실행' })

    await user.click(screen.getByRole('button', { name: /^2위 CVE-2026-902/ }))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/type/rce?cve=CVE-2026-902')
    expect(await screen.findByText('CVE-2026-902 상세 표시 중')).toBeInTheDocument()
  })

  it('좁은 화면에서 고르면 상세 페이지로 간다', async () => {
    setViewportWidth(390)
    const user = renderAt('/type/rce')
    await screen.findByRole('heading', { level: 1, name: '원격 코드 실행' })

    await user.click(screen.getByRole('button', { name: /^1위 CVE-2026-901/ }))

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent(/^\/cve\/CVE-2026-901$/)
  })

  it('들어 있는 건이 없는 분류는 비었다고 알리고, 최근 7일 입구를 두지 않는다', async () => {
    renderAt('/type/dos')

    expect(await screen.findByText('이 분류에 든 취약점이 아직 없습니다.')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /모두 보기/ })).toBeNull()
  })

  it('없는 분류 키면 그렇다고 알리고 분류 목록을 보인다', async () => {
    renderAt('/type/nope')

    expect(await screen.findByRole('heading', { level: 1, name: '없는 분류입니다' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '원격 코드 실행 최근 7일 12건' })).toHaveAttribute('href', '/type/rce')
  })

  it('배치가 분류를 아직 내보내지 않았으면 다음 갱신부터 보인다고 알린다', async () => {
    renderAt('/type/rce', null)

    expect(await screen.findByText(/분류는 다음 갱신부터 보입니다/)).toBeInTheDocument()
  })
})
