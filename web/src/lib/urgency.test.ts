import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import { metaWithVersion } from '../test/fixtures.ts'
import {
  daysBetween,
  dueInDays,
  isUrgent,
  kevAddedCounts,
  kevAddedDaysAgo,
  nextDueRow,
  overdueCount,
  productName,
  referenceDate,
  urgentRows,
} from './urgency.ts'

const TODAY = '2026-11-01'
const kev = (due: string | null, overrides = {}) => trendRow({ is_kev: true, kev_due_date: due, ...overrides })

describe('기준일 (갱신 시각의 한국 날짜)', () => {
  it('UTC 로는 전날이어도 한국 날짜를 기준일로 쓴다', () => {
    expect(referenceDate('2026-10-31T15:30:00Z')).toBe('2026-11-01')
  })

  it('두 날짜 사이 날 수를 센다', () => {
    expect(daysBetween('2026-10-29', '2026-11-01')).toBe(3)
    expect(daysBetween('2026-11-01', '2026-10-29')).toBe(-3)
  })
})

describe('CISA 조치 기한', () => {
  it('KEV 기한까지 남은 날을 센다. 지났으면 음수다', () => {
    expect(dueInDays(kev('2026-11-03'), TODAY)).toBe(2)
    expect(dueInDays(kev('2026-10-22'), TODAY)).toBe(-10)
  })

  it('KEV 가 아니거나 기한이 없으면 기한을 모른다', () => {
    expect(dueInDays(trendRow({ is_kev: false, kev_due_date: '2026-11-02' }), TODAY)).toBeNull()
    expect(dueInDays(kev(null), TODAY)).toBeNull()
  })

  it('기한이 오늘부터 3일 안에 끝나면 임박이다. 4일 뒤나 이미 지난 것은 아니다', () => {
    expect(isUrgent(kev('2026-11-01'), TODAY)).toBe(true)
    expect(isUrgent(kev('2026-11-04'), TODAY)).toBe(true)
    expect(isUrgent(kev('2026-11-05'), TODAY)).toBe(false)
    expect(isUrgent(kev('2026-10-31'), TODAY)).toBe(false)
  })

  it('임박한 것은 기한이 가까운 순, 같으면 점수 높은 순으로 줄 세운다', () => {
    const rows = [
      kev('2026-11-03', { id: 'D2', score: 90 }),
      kev('2026-11-01', { id: 'D0-low', score: 40 }),
      kev('2026-11-01', { id: 'D0-high', score: 60 }),
      kev('2026-11-09', { id: 'D8', score: 99 }),
    ]

    expect(urgentRows(rows, TODAY).map((row) => row.id)).toEqual(['D0-high', 'D0-low', 'D2'])
  })

  it('기한이 지난 KEV 수를 센다', () => {
    expect(overdueCount([kev('2026-10-20'), kev('2026-10-31'), kev('2026-11-01'), trendRow()], TODAY)).toBe(2)
  })

  it('임박이 없을 때 그다음으로 가까운 기한을 찾는다', () => {
    const rows = [kev('2026-11-20', { id: 'far' }), kev('2026-11-08', { id: 'near' }), kev('2026-10-01')]

    expect(nextDueRow(rows, TODAY)?.id).toBe('near')
    expect(nextDueRow([kev('2026-10-01')], TODAY)).toBeNull()
  })
})

describe('KEV 등재', () => {
  it('등재 며칠 전인지 센다. 등재일을 모르면 알 수 없다', () => {
    expect(kevAddedDaysAgo(kev('2026-11-20', { kev_added_at: '2026-10-29' }), TODAY)).toBe(3)
    expect(kevAddedDaysAgo(kev('2026-11-20', { kev_added_at: null }), TODAY)).toBeNull()
    expect(kevAddedDaysAgo(kev('2026-11-20', { kev_added_at: undefined }), TODAY)).toBeNull()
  })

  it('배치가 전체 KEV 집계를 주면 그 값을 쓴다', () => {
    const meta = { ...metaWithVersion('v1'), kev_added_7d: 9, kev_added_prev_7d: 8 }

    expect(kevAddedCounts(meta, [], TODAY)).toEqual({ recent: 9, previous: 8, scope: 'all' })
  })

  it('전체 집계가 없으면 주목 목록 행의 등재일로 최근 7일과 그 전 7일을 센다', () => {
    const rows = [
      kev('2026-11-20', { kev_added_at: '2026-11-01' }),
      kev('2026-11-20', { kev_added_at: '2026-10-26' }),
      kev('2026-11-20', { kev_added_at: '2026-10-25' }),
      kev('2026-11-20', { kev_added_at: '2026-10-19' }),
      kev('2026-11-20', { kev_added_at: '2026-10-18' }),
    ]

    expect(kevAddedCounts(metaWithVersion('v1'), rows, TODAY)).toEqual({ recent: 2, previous: 2, scope: 'watchlist' })
  })

  it('전체 집계도 행의 등재일도 없으면 셀 수 없다', () => {
    expect(kevAddedCounts(metaWithVersion('v1'), [kev('2026-11-20')], TODAY)).toBeNull()
  })
})

describe('제품 이름', () => {
  it('제품 정보가 없으면 "제품 미상"으로 적는다', () => {
    expect(productName(trendRow({ primary_product: 'GitLab' }))).toBe('GitLab')
    expect(productName(trendRow({ primary_product: null }))).toBe('제품 미상')
  })
})
