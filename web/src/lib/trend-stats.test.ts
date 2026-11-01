import { describe, expect, it } from 'vitest'
import type { DailyCount } from '../types/stats.ts'
import { completeDays, lastDayIsPartial } from './daily-window.ts'
import {
  axisLabels,
  formatChange,
  severityParts,
  spikeNote,
  trendAnswer,
  trendPanel,
  verdictOf,
} from './trend-stats.ts'

// 실데이터 2026-11-01 18:49 스냅샷 (14일)
const UPDATED = '2026-11-01T18:49:00+09:00'
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

describe('오늘(집계 중) 판정 — KPI 와 추이가 같이 쓴다', () => {
  it('마지막 날이 갱신일(한국 날짜)이면 집계 중이고, 완결일에서 뺀다', () => {
    expect(lastDayIsPartial(DAILY, UPDATED)).toBe(true)
    expect(completeDays(DAILY, UPDATED)).toHaveLength(13)
  })

  it('갱신이 다음 날로 넘어가 있으면 마지막 날도 완결이다', () => {
    expect(lastDayIsPartial(DAILY, '2026-11-02T09:00:00+09:00')).toBe(false)
    expect(completeDays(DAILY, '2026-11-02T09:00:00+09:00')).toHaveLength(14)
  })

  it('일별 통계가 없으면 집계 중인 날도 없다', () => {
    expect(lastDayIsPartial([], UPDATED)).toBe(false)
  })
})

describe('평소와 비교', () => {
  it('최근 7일 중앙값이 평소보다 15% 넘게 많거나 적으면 많음·적음, 그 안이면 비슷이다', () => {
    expect(verdictOf(1.15)).toBe('more')
    expect(verdictOf(1.14)).toBe('similar')
    expect(verdictOf(0.86)).toBe('similar')
    expect(verdictOf(0.85)).toBe('less')
  })

  it('증감은 퍼센트 정수로, 같으면 "같음"으로 적는다', () => {
    expect(formatChange(436 / 530)).toBe('−18%')
    expect(formatChange(1.12)).toBe('+12%')
    expect(formatChange(1)).toBe('같음')
  })
})

describe('새 CVE·Critical 패널', () => {
  it('평소는 완결된 13일의 중앙값, 비교는 최근 7일(완결) 중앙값이다', () => {
    const fresh = trendPanel(DAILY, 'new', UPDATED)

    expect(fresh.usual).toBe(530)
    expect(fresh.recent).toBe(436)
    expect(fresh.verdict).toBe('less')
    expect(trendPanel(DAILY, 'critical', UPDATED)).toMatchObject({ usual: 42, recent: 37, verdict: 'similar' })
  })

  it('세로 눈금 상한은 평소의 2.2배를 올린 값이고, 넘는 날은 위를 잘라 그린다', () => {
    const fresh = trendPanel(DAILY, 'new', UPDATED)

    expect(fresh.cap).toBe(1500)
    expect(fresh.bars[2]).toMatchObject({ date: '2026-10-21', value: 1983, cut: true, height: 100 })
    expect(fresh.bars.filter((bar) => bar.cut)).toHaveLength(1)
    expect(fresh.usualHeight).toBeCloseTo((530 / 1500) * 100)
    expect(trendPanel(DAILY, 'critical', UPDATED).cap).toBe(95)
  })

  it('주말 칸과 집계 중인 오늘 막대를 표시한다', () => {
    const bars = trendPanel(DAILY, 'new', UPDATED).bars

    expect(bars.filter((bar) => bar.weekend).map((bar) => bar.date)).toEqual([
      '2026-10-24',
      '2026-10-25',
      '2026-10-31',
      '2026-11-01',
    ])
    expect(bars.filter((bar) => bar.partial).map((bar) => bar.date)).toEqual(['2026-11-01'])
  })
})

describe('카드 제목의 답', () => {
  it('두 줄의 판정이 다르면 따로 말한다', () => {
    expect(trendAnswer(trendPanel(DAILY, 'new', UPDATED), trendPanel(DAILY, 'critical', UPDATED))).toBe(
      '새 CVE 는 평소보다 적고, Critical 은 비슷합니다',
    )
  })

  it('두 줄의 판정이 같으면 한 문장으로 말한다', () => {
    const flat = DAILY.map((day) => ({ ...day, new: 100, critical: 10 }))

    expect(trendAnswer(trendPanel(flat, 'new', UPDATED), trendPanel(flat, 'critical', UPDATED))).toBe(
      '평소와 비슷합니다',
    )
  })
})

describe('급증 알림', () => {
  it('마지막 완결일이 평소의 2배를 넘으면 몇 배인지 알린다 (평소에는 그날도 들어간다)', () => {
    const spiked = DAILY.map((day) => (day.date === '2026-10-31' ? { ...day, new: 2000 } : day))

    expect(spikeNote(spiked, UPDATED)).toBe('다만 10/31은 평소의 3.2배(2,000건)로 급증했습니다.')
  })

  it('급증이 아니면 알리지 않는다', () => {
    expect(spikeNote(DAILY, UPDATED)).toBeNull()
  })
})

describe('x축 날짜', () => {
  it('마지막 날부터 하루 건너 하나씩 적고, 집계 중인 오늘은 "오늘"이다', () => {
    expect(axisLabels(DAILY, UPDATED)).toEqual([
      null,
      '20',
      null,
      '22',
      null,
      '24',
      null,
      '26',
      null,
      '28',
      null,
      '30',
      null,
      '오늘',
    ])
  })

  it('달이 바뀐 날은 "월/일"로 적는다', () => {
    expect(axisLabels(DAILY, '2026-11-02T09:00:00+09:00').at(-1)).toBe('11/1')
  })
})

describe('최근 7일 심각도 한 줄 막대', () => {
  it('Critical·High·Medium·Low·등급 없음 순서로, 0건은 빼고 준다', () => {
    expect(severityParts({ critical: 249, high: 965, medium: 1225, low: 0, unrated: 789 })).toEqual([
      { key: 'critical', label: 'Critical', count: 249 },
      { key: 'high', label: 'High', count: 965 },
      { key: 'medium', label: 'Medium', count: 1225 },
      { key: 'unrated', label: '등급 없음', count: 789 },
    ])
  })
})
