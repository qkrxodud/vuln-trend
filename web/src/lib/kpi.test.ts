import { describe, expect, it } from 'vitest'
import { metaWithVersion } from '../test/fixtures.ts'
import { formatCount, median, todayNewSummary } from './kpi.ts'

describe('KPI 숫자 표기', () => {
  it('천 단위마다 쉼표를 찍는다', () => {
    expect(formatCount(1234)).toBe('1,234')
  })
})

describe('가운데 값 (평소)', () => {
  it('홀수 개면 가운데 값, 짝수 개면 가운데 둘의 평균이다', () => {
    expect(median([5, 1, 3])).toBe(3)
    expect(median([4, 1, 3, 2])).toBe(2.5)
    expect(median([])).toBe(0)
  })
})

describe('오늘 새로 나온 CVE 카드', () => {
  const meta = { ...metaWithVersion('v1'), updated_at: '2026-11-01T18:49:00+09:00' }
  const daily = [
    { date: '2026-10-29', new: 500, critical: 30 },
    { date: '2026-10-30', new: 560, critical: 40 },
    { date: '2026-10-31', new: 530, critical: 35 },
    { date: '2026-11-01', new: 48, critical: 2 },
  ]

  it('큰 숫자는 meta 의 24시간 신규, 비교는 어제 하루 값이다', () => {
    const summary = todayNewSummary(meta, daily)

    expect(summary.today).toBe(meta.today.new)
    expect(summary.yesterday).toBe(meta.yesterday.new)
    expect(summary.asOf).toBe('18:49')
  })

  it('마지막 날이 갱신일이면 집계 중이고, 평소는 그날을 뺀 완결일의 중앙값이다', () => {
    const summary = todayNewSummary(meta, daily)

    expect(summary.partial).toBe(true)
    expect(summary.usual).toBe(530)
    expect(summary.usualDays).toBe(3)
    expect(summary.series).toEqual([500, 560, 530, 48])
  })

  it('갱신이 다음 날로 넘어가 있으면 마지막 날도 완결로 친다', () => {
    const summary = todayNewSummary({ ...meta, updated_at: '2026-11-02T09:00:00+09:00' }, daily)

    expect(summary.partial).toBe(false)
    expect(summary.usualDays).toBe(4)
  })

  it('일별 통계가 없으면 평소를 모른다', () => {
    expect(todayNewSummary(meta, []).usual).toBeNull()
  })
})
