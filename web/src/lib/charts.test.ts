import { describe, expect, it } from 'vitest'
import { dailyBarTitle, formatMonthDay, toDailyBars, toSeverityShares } from './charts.ts'

describe('일별 신규 막대 (최근 14일, KST 달력일)', () => {
  it('날짜는 "월/일"로 적는다', () => {
    expect(formatMonthDay('2026-10-30')).toBe('10/30')
    expect(formatMonthDay('2026-11-03')).toBe('11/3')
  })

  it('막대는 Critical 이 아닌 신규 위에 Critical 을 쌓아 전체 높이가 그날 신규 건수가 된다', () => {
    const [bar] = toDailyBars([{ date: '2026-10-30', new: 142, critical: 11 }])

    expect(bar).toMatchObject({ label: '10/30', total: 142, critical: 11, nonCritical: 131 })
  })

  it('가장 최근 날만 표시해 오늘 막대를 알아보게 한다', () => {
    const bars = toDailyBars([
      { date: '2026-10-29', new: 133, critical: 7 },
      { date: '2026-10-30', new: 142, critical: 11 },
    ])

    expect(bars.map((bar) => bar.isLatest)).toEqual([false, true])
  })
})

describe('오늘 막대는 갱신 시각까지만 센 것 (진행 중)', () => {
  const daily = [
    { date: '2026-10-31', new: 436, critical: 37 },
    { date: '2026-11-01', new: 5, critical: 0 },
  ]

  it('마지막 갱신과 같은 날의 막대는 갱신 시각까지 센 진행 중으로 표시한다', () => {
    const bars = toDailyBars(daily, '2026-11-01T09:00:00+09:00')

    expect(bars.map((bar) => bar.inProgressUntil)).toEqual([null, '09:00'])
  })

  it('갱신이 지난 날에 멈춰 있으면 진행 중인 막대가 없다', () => {
    const bars = toDailyBars(daily, '2026-11-02T09:00:00+09:00')

    expect(bars.map((bar) => bar.inProgressUntil)).toEqual([null, null])
  })

  it('갱신 시각을 모르면 진행 중으로 표시하지 않는다', () => {
    expect(toDailyBars(daily, null).map((bar) => bar.inProgressUntil)).toEqual([null, null])
  })

  it('툴팁과 표의 날짜는 진행 중이면 언제까지 셌는지 함께 적는다', () => {
    const [done, today] = toDailyBars(daily, '2026-11-01T09:00:00+09:00')

    expect(dailyBarTitle(done)).toBe('10/31')
    expect(dailyBarTitle(today)).toBe('11/1 (진행 중, 09:00까지)')
  })
})

describe('심각도 분포 도넛 (최근 7일, 미평가 포함)', () => {
  const distribution = { critical: 52, high: 253, medium: 383, low: 113, unrated: 70 }

  it('Critical·High·Medium·Low·미평가 다섯 범주를 이 순서로 고정해 보인다', () => {
    expect(toSeverityShares(distribution).shares.map((share) => share.label)).toEqual([
      'Critical',
      'High',
      'Medium',
      'Low',
      '미평가',
    ])
  })

  it('범주마다 건수와 전체 대비 비율(정수 %)을 준다', () => {
    const { total, shares } = toSeverityShares(distribution)

    expect(total).toBe(871)
    expect(shares[0]).toMatchObject({ count: 52, percent: 6 })
    expect(shares[4]).toMatchObject({ count: 70, percent: 8 })
  })

  it('신규가 하나도 없는 주에는 모든 비율을 0% 로 둔다', () => {
    const { total, shares } = toSeverityShares({ critical: 0, high: 0, medium: 0, low: 0, unrated: 0 })

    expect(total).toBe(0)
    expect(shares.every((share) => share.percent === 0)).toBe(true)
  })
})
