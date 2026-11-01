import { fireEvent, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { ListFilterProvider } from '../../hooks/ListFilterProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { setViewportWidth } from '../../test/fake/fake-media.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { trendRow } from '../../test/rows.ts'
import type { TrendRow } from '../../types/trend.ts'
import { ProductFilter } from '../watch/ListFilters.tsx'
import { ScatterCard } from './ScatterCard.tsx'

// 기준일 2026-11-01
const META = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }

const kev = (id: string, overrides: Partial<TrendRow> = {}) =>
  trendRow({ id, is_kev: true, kev_due_date: '2026-12-01', epss: 0.5, cvss: 8, ...overrides })

const ROWS: TrendRow[] = [
  kev('CVE-2026-97229', { score: 63, primary_product: 'GitLab', kev_due_date: '2026-11-01', epss: 0.27, cvss: 10 }),
  kev('CVE-2026-63077', { score: 13, primary_product: 'TeamCity', kev_ransomware: true, epss: 0.94, cvss: 9.8 }),
  kev('CVE-2026-109618', { score: 77, primary_product: 'PHP', epss: 0.99, cvss: 9.8 }),
  trendRow({ id: 'CVE-2026-50001', score: 40, primary_product: 'Apache', epss: 0.02, cvss: 5 }),
  trendRow({ id: 'CVE-2026-50002', score: 30, primary_product: 'Zabbix', epss: null, cvss: null }),
]

const LocationProbe = () => <output aria-label="현재 주소">{useLocation().pathname}</output>

const renderScatter = (rows: TrendRow[] = ROWS, selectedId: string | null = null) => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', META)
    .serve('/vuln-trend/data/index.json?v=v1', rows)
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <ListFilterProvider>
          <ScatterCard selectedId={selectedId} />
          <ProductFilter rows={rows} today="2026-11-01" />
          <LocationProbe />
        </ListFilterProvider>
      </DataProvider>
    </MemoryRouter>,
  )
  return userEvent.setup()
}

const card = () => screen.findByRole('region', { name: '어느 쪽이 더 위험한가' })
const point = (id: string) => screen.getByRole('button', { name: new RegExp(id) })

describe('머리', () => {
  it('부제에 빨강 ◆ 가 먼저고 그다음 오른쪽 위 칸이라고 읽는 순서를 밝힌다', async () => {
    renderScatter()

    expect(await card()).toHaveTextContent(
      '빨강 ◆(CISA 기한 3일 안)은 칸과 상관없이 먼저 · 그다음 오른쪽 위 칸(악용 가능성 30% 이상 · 심각도 7.0 이상) · 주목 5건 중 KEV 3건',
    )
  })

  it('범례는 데이터에 있는 구분만 건수와 함께 보인다', async () => {
    renderScatter()
    const legend = within(await card()).getByRole('list', { name: '점 모양 설명' })

    expect(
      within(legend)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['CISA 기한 3일 안 1건', '랜섬웨어 악용 1건', '그 외'])
  })

  it('KEV 가 없으면 빨강 ◆ 를 범례에도 그림에도 두지 않고, 기한 임박이 없다고 적는다', async () => {
    renderScatter([trendRow({ epss: 0.5, cvss: 9 }), trendRow({ epss: 0.02, cvss: 5 })])
    const region = await card()

    expect(within(region).getByRole('list', { name: '점 모양 설명' })).not.toHaveTextContent('CISA 기한')
    expect(region).toHaveTextContent(
      'CISA 기한 3일 안에 끝나는 건 없음 · 오른쪽 위 칸(악용 가능성 30% 이상 · 심각도 7.0 이상)부터',
    )
    expect(region.querySelector('[data-mark="urgent"]')).toBeNull()
  })

  it('PoC 가 있으면 범례에 속 빈 고리를 더한다', async () => {
    renderScatter([trendRow({ epss: 0.5, cvss: 9, poc_count: 2 })])

    expect(within(await card()).getByRole('list', { name: '점 모양 설명' })).toHaveTextContent('PoC 공개 1건')
  })
})

describe('그림', () => {
  it('좌표가 있는 취약점마다 점을 하나씩 두고, 뜻을 말로 읽힌다', async () => {
    renderScatter()
    await card()

    expect(point('CVE-2026-97229')).toHaveAccessibleName(
      'GitLab CVE-2026-97229, 점수 63, 심각·악용 가능성 낮음, 악용 가능성 27%, 심각도 10.0, CISA 기한 D-0 · KEV',
    )
    expect(screen.getAllByRole('button', { name: /^.+ CVE-\d{4}-\d+, 점수/ })).toHaveLength(4)
  })

  it('오른쪽 위 칸 이름에 건수를 붙이고, 기한 임박·랜섬웨어에는 항상 이름표를 단다', async () => {
    renderScatter()
    const region = await card()

    expect(region).toHaveTextContent('악용 가능성 높음 · 심각 2건')
    expect(region).toHaveTextContent('D-0 GitLab 97229')
    expect(region).toHaveTextContent('랜섬웨어 TeamCity 63077')
  })

  it('좌표 없는 건은 그림에서 빠졌다고 알린다', async () => {
    renderScatter()

    expect(await card()).toHaveTextContent('EPSS·CVSS 없는 1건은 그림에서 빠짐(표에는 있음)')
  })

  it('기한 임박이 많으면 가장 급한 8건만 이름을 달고 나머지는 표로 보라고 한다', async () => {
    const flood = Array.from({ length: 12 }, (_, i) =>
      kev(`CVE-2026-${90000 + i}`, {
        score: 60 - i,
        kev_due_date: '2026-11-02',
        epss: 0.001 * (i + 1),
        cvss: 5 + i * 0.4,
      }),
    )
    renderScatter(flood)

    expect(await card()).toHaveTextContent(
      '기한 임박·랜섬웨어 12건 중 가장 급한 8건만 이름을 달았습니다 — 나머지 4건은 표로 보기',
    )
  })

  it('그릴 취약점이 없으면 그렇다고 적는다', async () => {
    renderScatter([])

    expect(await screen.findByText('그릴 취약점이 없습니다')).toBeInTheDocument()
    expect(await card()).toHaveTextContent('표시할 취약점이 없습니다')
  })
})

describe('읽고 고르기', () => {
  it('점 묶음은 Tab 한 번으로 들어가고(고른 점에서 시작), 화살표는 점수 순서로 옮기며 값을 툴팁으로 보인다', async () => {
    renderScatter()
    await card()
    const selected = point('CVE-2026-97229')

    expect(selected).toHaveAttribute('tabindex', '0')
    expect(point('CVE-2026-109618')).toHaveAttribute('tabindex', '-1')
    fireEvent.focus(selected)
    expect(screen.getByRole('tooltip')).toHaveTextContent('트렌드 점수63악용 가능성 EPSS27%심각도 CVSS10.0CISA 기한D-0')
    fireEvent.keyDown(selected, { key: 'ArrowLeft' })
    expect(point('CVE-2026-109618')).toHaveFocus()
    expect(screen.getByRole('tooltip')).toHaveTextContent('CVE-2026-109618')
  })

  it('Enter 로 고르면 목록·상세가 같은 취약점을 고르고, 고른 점과 선택 줄이 남는다', async () => {
    renderScatter(ROWS, 'CVE-2026-97229')
    await card()

    expect(point('CVE-2026-97229')).toHaveAttribute('aria-pressed', 'true')
    expect(await card()).toHaveTextContent('✓ 선택: GitLab CVE-2026-97229 — 아래 목록·상세에 표시 중')
    fireEvent.keyDown(point('CVE-2026-63077'), { key: 'Enter' })

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-63077')
  })

  it('마우스는 가장 가까운 점을 잡아 툴팁을 보이고, 누르면 그 점을 고른다', async () => {
    renderScatter()
    const region = await card()
    const mark = region.querySelector('[data-id="CVE-2026-50001"] circle[data-center]')
    const hit = region.querySelector('[data-hit]')
    if (mark === null || hit === null) throw new Error('scatter parts missing')
    const at = { clientX: Number(mark.getAttribute('cx')) + 3, clientY: Number(mark.getAttribute('cy')) - 2 }

    fireEvent.pointerMove(hit, at)
    expect(screen.getByRole('tooltip')).toHaveTextContent('CVE-2026-50001')
    fireEvent.click(hit, at)
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-50001')
  })

  it('목록 필터에 가려진 취약점을 고르면 필터를 풀어 목록에서 보이게 한다', async () => {
    const user = renderScatter()
    await card()
    await user.click(screen.getByRole('button', { name: 'Apache 1' }))

    fireEvent.keyDown(point('CVE-2026-109618'), { key: 'Enter' })

    expect(screen.getByRole('button', { name: /^모든 제품/ })).toHaveAttribute('aria-pressed', 'true')
  })

  it('좁은 화면에서 고르면 상세 페이지로 간다', async () => {
    setViewportWidth(390)
    renderScatter()
    await card()

    fireEvent.keyDown(point('CVE-2026-109618'), { key: ' ' })

    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-109618')
  })
})

describe('점 모양', () => {
  it('랜섬웨어는 고리 있는 원, PoC 는 속 빈 고리, 기한 임박은 빨강 ◆, CVSS 4.0 미만은 바닥에 ▼ 를 단다', async () => {
    renderScatter([
      kev('CVE-2026-1', { score: 90, kev_due_date: '2026-11-02', kev_ransomware: true, poc_count: 1 }),
      kev('CVE-2026-2', { score: 80, kev_ransomware: true, poc_count: 1, epss: 0.6, cvss: 6 }),
      trendRow({ id: 'CVE-2026-3', score: 70, poc_count: 3, epss: 0.05, cvss: 9 }),
      trendRow({ id: 'CVE-2026-4', score: 60, epss: 0.2, cvss: 2.5 }),
    ])
    const region = await card()
    const markOf = (id: string) => region.querySelector(`[data-id="${id}"] [data-mark]`)?.getAttribute('data-mark')

    expect([markOf('CVE-2026-1'), markOf('CVE-2026-2'), markOf('CVE-2026-3'), markOf('CVE-2026-4')]).toEqual([
      'urgent',
      'ransom',
      'poc',
      'other',
    ])
    expect(region.querySelectorAll('[data-id="CVE-2026-4"] path')).toHaveLength(1)
  })

  it('같은 자리에 몰린 판단 구간 점은 "n건"으로 세고, 툴팁에 같은 자리·근처 건수를 적는다', async () => {
    renderScatter(
      Array.from({ length: 6 }, (_, i) => trendRow({ id: `CVE-2026-${70 + i}`, score: 60 - i, epss: 0.99, cvss: 9.8 })),
    )
    const region = await card()

    expect(region).toHaveTextContent('6건')
    expect(region).toHaveTextContent('같은 값은 살짝 흩어 그림')
    fireEvent.focus(point('CVE-2026-70'))
    expect(screen.getByRole('tooltip')).toHaveTextContent('같은 자리 6건 · 이 근처 6건 — 표로 보기에서 모두 확인')
  })
})

describe('키보드·마우스 마무리', () => {
  it('Home·End 로 처음·끝 점으로 가고, Esc 와 마우스가 떠나면 툴팁을 닫는다', async () => {
    renderScatter()
    const region = await card()
    const start = point('CVE-2026-97229')

    fireEvent.keyDown(start, { key: 'End' })
    expect(point('CVE-2026-63077')).toHaveFocus()
    fireEvent.keyDown(point('CVE-2026-63077'), { key: 'Home' })
    expect(point('CVE-2026-109618')).toHaveFocus()
    fireEvent.keyDown(point('CVE-2026-109618'), { key: 'Escape' })
    expect(screen.queryByRole('tooltip')).toBeNull()
    fireEvent.keyDown(point('CVE-2026-109618'), { key: 'a' })
    expect(point('CVE-2026-109618')).toHaveFocus()

    const hit = region.querySelector('[data-hit]')
    if (hit === null) throw new Error('hit layer missing')
    fireEvent.pointerMove(hit, { clientX: -500, clientY: -500 })
    expect(screen.queryByRole('tooltip')).toBeNull()
    fireEvent.click(hit, { clientX: -500, clientY: -500 })
    fireEvent.pointerLeave(hit)
    expect(screen.queryByRole('tooltip')).toBeNull()
  })
})

describe('좁은 그림 (480px 미만)', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  // 그림 크기를 360×320 으로 알려 주는 ResizeObserver
  const narrowObserver = class {
    callback: ResizeObserverCallback
    constructor(callback: ResizeObserverCallback) {
      this.callback = callback
    }
    observe() {
      this.callback(
        [{ contentRect: { width: 360, height: 320 } } as ResizeObserverEntry],
        this as unknown as ResizeObserver,
      )
    }
    disconnect() {}
  }

  it('칸 이름·축 설명을 줄이고, 우선 이름표는 4건까지만 단다', async () => {
    vi.stubGlobal('ResizeObserver', narrowObserver)
    const flood = Array.from({ length: 6 }, (_, i) =>
      kev(`CVE-2026-${90000 + i}`, {
        score: 60 - i,
        kev_due_date: '2026-11-02',
        epss: 0.002 * (i + 1),
        cvss: 5 + i * 0.8,
      }),
    )
    renderScatter(flood)
    const region = await card()

    expect(await within(region).findByText('심각·악용 낮음')).toBeInTheDocument()
    expect(region).toHaveTextContent('30% 왼쪽은 로그 눈금 · 4.0 미만은 바닥')
    expect(region).toHaveTextContent('나머지 2건은 표로 보기')
  })
})

describe('표로 보기', () => {
  it('좌표 없는 건까지 점수순으로 표에 모두 적는다', async () => {
    renderScatter()
    const table = within(await card()).getByRole('table')
    const rows = within(table).getAllByRole('row').slice(1)

    expect(rows.map((row) => row.firstElementChild?.textContent)).toEqual([
      'CVE-2026-109618',
      'CVE-2026-97229',
      'CVE-2026-50001',
      'CVE-2026-50002',
      'CVE-2026-63077',
    ])
    expect(rows[1]).toHaveTextContent('CVE-2026-97229GitLab6327%10.0D-0기한 임박심각·악용 가능성 낮음')
  })
})
