import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import type { Meta } from '../../types/meta.ts'
import type { DailyCount, Stats } from '../../types/stats.ts'
import { TrendCard } from './TrendCard.tsx'

// 실데이터 2026-11-01 18:49 스냅샷
const META: Meta = {
  ...metaWithVersion('v1'),
  updated_at: '2026-11-01T18:49:00+09:00',
  today: { new: 61, kev_new: 0, critical: 28, high_risk: 4379 },
}
const DAILY: DailyCount[] = [
  { date: '2026-10-19', new: 693, critical: 16 },
  { date: '2026-10-20', new: 417, critical: 42 },
  { date: '2026-10-21', new: 1983, critical: 160 },
  { date: '2026-10-22', new: 530, critical: 51 },
  { date: '2026-10-23', new: 633, critical: 83 },
  { date: '2026-10-24', new: 152, critical: 15 },
  { date: '2026-10-25', new: 123, critical: 6 },
  { date: '2026-10-26', new: 196, critical: 15 },
  { date: '2026-10-27', new: 953, critical: 42 },
  { date: '2026-10-28', new: 628, critical: 65 },
  { date: '2026-10-29', new: 429, critical: 20 },
  { date: '2026-10-30', new: 686, critical: 62 },
  { date: '2026-10-31', new: 436, critical: 37 },
  { date: '2026-11-01', new: 48, critical: 8 },
]
const STATS: Stats = { daily: DAILY, severity_7d: { critical: 249, high: 965, medium: 1225, low: 148, unrated: 789 } }

const renderCard = (stats: Stats = STATS, meta: Meta = META) => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', meta)
    .serve('/vuln-trend/data/stats.json?v=v1', stats)
  render(
    <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
      <TrendCard />
    </DataProvider>,
  )
}

const card = () => screen.findByRole('region', { name: /^늘고 있나/ })

describe('늘고 있나 — 제목과 판정', () => {
  it('제목에 바로 답을 쓰고, 비교 기준을 부제로 밝힌다', async () => {
    renderCard()

    expect(await screen.findByRole('heading', { level: 2 })).toHaveTextContent(
      '늘고 있나 — 새 CVE 는 평소보다 적고, Critical 은 비슷합니다',
    )
    expect(await card()).toHaveTextContent(
      '10/19~11/1 하루 건수 · 최근 7일 중앙값을 평소(가로선, 13일 중앙값)와 비교해 ±15% 넘으면 많음·적음 · 두 줄은 세로 눈금이 다름',
    )
  })

  it('두 줄마다 최근 7일 하루 중앙값을 크게, 평소와 판정·증감과 세로 눈금을 작게 적는다', async () => {
    renderCard()
    const fresh = await screen.findByRole('group', { name: '새 CVE' })
    const critical = screen.getByRole('group', { name: 'Critical' })

    expect(fresh).toHaveTextContent('436최근 7일')
    expect(fresh).toHaveTextContent('평소 530 · 적음 (−18%)')
    expect(fresh).toHaveTextContent('세로 눈금 0~1,500')
    expect(critical).toHaveTextContent('평소 42 · 비슷 (−12%)')
    expect(critical).toHaveTextContent('세로 눈금 0~95')
  })
})

describe('막대', () => {
  it('막대 그림은 평소와 잘라 그린 상한을 말로도 알린다', async () => {
    renderCard()

    expect(await screen.findByRole('img', { name: /^새 CVE 하루 건수 14일 막대/ })).toHaveAccessibleName(
      '새 CVE 하루 건수 14일 막대. 평소(중앙값) 530건. 세로축 1,500건 위는 잘라 그림. 화살표 키로 날짜를 옮겨 값을 읽습니다.',
    )
  })

  it('잘라 그린 막대와 집계 중인 오늘 막대에는 실제 값을 적는다', async () => {
    renderCard()
    const plot = await screen.findByRole('img', { name: /^새 CVE/ })

    expect(within(plot).getByText('1,983')).toBeInTheDocument()
    expect(within(plot).getByText('48')).toBeInTheDocument()
    expect(within(plot).getByText('평소 530')).toBeInTheDocument()
  })

  it('x축은 하루 건너 하나씩, 집계 중인 오늘은 "오늘"로 적는다', async () => {
    renderCard()
    await card()

    expect(screen.getByText('오늘')).toBeInTheDocument()
    expect(screen.getByText('30')).toBeInTheDocument()
  })

  it('주말 띠·잘라 그림·집계 중인 오늘을 주석으로 풀어 쓴다', async () => {
    renderCard()
    const region = await card()

    expect(region).toHaveTextContent('옅은 띠 = 주말 (토·일은 새 CVE 가 적게 나옵니다)')
    expect(region).toHaveTextContent('평소의 2배를 넘는 날은 막대 위를 잘라 그리고 실제 값을 적었습니다 (10/21)')
    expect(region).toHaveTextContent(
      '오늘은 18:49 까지 모은 값이라 비교에서 뺐습니다 (일별 통계 48건 — 위 카드의 61건과 집계 시점이 다릅니다)',
    )
  })

  it('화살표 키로 날짜를 옮기며 그날 값을 읽는다', async () => {
    renderCard()
    const plot = await screen.findByRole('img', { name: /^새 CVE/ })

    fireEvent.focus(plot)
    expect(screen.getByRole('status', { name: '고른 날 값' })).toHaveTextContent(
      '11/1 (일) · 집계 중새 CVE48건Critical8건',
    )
    fireEvent.keyDown(plot, { key: 'ArrowLeft' })
    expect(screen.getByRole('status', { name: '고른 날 값' })).toHaveTextContent('10/31 (토)새 CVE436건Critical37건')
  })
})

describe('막대 읽기 (마우스·키보드)', () => {
  it('막대에 마우스를 올리면 그날 값을 보이고, 벗어나면 닫는다', async () => {
    renderCard()
    const plot = await screen.findByRole('img', { name: /^Critical 하루/ })
    const columns = plot.querySelectorAll(':scope > div')

    fireEvent.mouseMove(columns[2])
    expect(screen.getByRole('status', { name: '고른 날 값' })).toHaveTextContent('10/21 (수)새 CVE1,983건Critical160건')
    fireEvent.mouseLeave(plot)
    expect(screen.queryByRole('status', { name: '고른 날 값' })).toBeNull()
  })

  it('오른쪽 화살표는 다음 날로 가되 마지막 날에서 멈추고, Esc 와 포커스를 떠나면 닫는다', async () => {
    renderCard()
    const plot = await screen.findByRole('img', { name: /^새 CVE/ })

    fireEvent.focus(plot)
    fireEvent.keyDown(plot, { key: 'ArrowRight' })
    expect(screen.getByRole('status', { name: '고른 날 값' })).toHaveTextContent('11/1 (일)')
    fireEvent.keyDown(plot, { key: 'Escape' })
    expect(screen.queryByRole('status', { name: '고른 날 값' })).toBeNull()
    fireEvent.focus(plot)
    fireEvent.blur(plot)
    expect(screen.queryByRole('status', { name: '고른 날 값' })).toBeNull()
  })
})

describe('갱신이 하루를 넘긴 데이터와 급증일', () => {
  it('마지막 날도 완결이면 집계 중 주석·"오늘" 글자가 없고, 마지막 날 급증을 한 줄로 알린다', async () => {
    const spiked = DAILY.map((day) => (day.date === '2026-11-01' ? { ...day, new: 2000 } : day))
    renderCard({ ...STATS, daily: spiked }, { ...META, updated_at: '2026-11-02T09:00:00+09:00' })
    const region = await card()

    expect(region).not.toHaveTextContent('까지 모은 값이라')
    expect(screen.queryByText('오늘')).toBeNull()
    expect(region).toHaveTextContent('다만 11/1은 평소의')
  })
})

describe('최근 7일 심각도', () => {
  it('7일 신규의 심각도를 한 줄 막대와 범례로 보이고, 미평가는 "등급 없음"이다', async () => {
    renderCard()
    await card()

    expect(screen.getByRole('heading', { name: '최근 7일 새 CVE 3,376건의 심각도' })).toBeInTheDocument()
    expect(
      screen.getByRole('img', { name: 'Critical 249건, High 965건, Medium 1,225건, Low 148건, 등급 없음 789건' }),
    ).toBeInTheDocument()
  })
})

describe('표로 보기', () => {
  it('14일 값을 표로도 읽을 수 있고, 오늘은 집계 중이라고 적는다', async () => {
    renderCard()
    const table = within(await card()).getByRole('table')
    const rows = within(table).getAllByRole('row')

    expect(rows).toHaveLength(15)
    expect(rows.at(-1)).toHaveTextContent('11/1 (일) 집계 중488')
  })
})

describe('빈 상태', () => {
  it('일별 통계가 없으면 그렇다고 알린다', async () => {
    renderCard({ ...STATS, daily: [] })

    expect(await screen.findByText('일별 통계가 아직 없습니다.')).toBeInTheDocument()
  })
})
