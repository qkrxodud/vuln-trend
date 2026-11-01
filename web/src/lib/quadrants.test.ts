import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import type { TrendRow } from '../types/trend.ts'
import { quadrantGrid, quadrantOf, shortCveId } from './quadrants.ts'

const TODAY = '2026-11-01'
const at = (epss: number | null, cvss: number | null, overrides: Partial<TrendRow> = {}) =>
  trendRow({ epss, cvss, ...overrides })

describe('칸 나누기', () => {
  it('EPSS 30%·CVSS 7.0 을 경계로 네 칸에 나눈다 (경계값은 높은 쪽)', () => {
    expect(quadrantOf(at(0.3, 7))).toBe('hot')
    expect(quadrantOf(at(0.01, 9.8))).toBe('severe')
    expect(quadrantOf(at(0.5, 5))).toBe('likely')
    expect(quadrantOf(at(0.01, 5))).toBe('low')
  })

  it('EPSS 나 CVSS 가 없으면 칸에 넣지 않는다', () => {
    expect(quadrantOf(at(null, 9))).toBeNull()
    expect(quadrantOf(at(0.5, null))).toBeNull()
  })
})

describe('4칸 건수표', () => {
  const urgent = (id: string, due: string, score: number) =>
    at(0.9, 9.8, { id, score, is_kev: true, kev_due_date: due })

  it('칸마다 건수를 세고, 점수가 없는 건은 따로 센다', () => {
    const grid = quadrantGrid([at(0.9, 9), at(0.9, 9), at(0.01, 9), at(0.5, 5), at(null, 9)], TODAY)

    expect(grid.cells.hot.count).toBe(2)
    expect(grid.cells.severe.count).toBe(1)
    expect(grid.cells.likely.count).toBe(1)
    expect(grid.cells.low.count).toBe(0)
    expect(grid.unscored).toBe(1)
  })

  it('대표 3건은 ◆ 기한 임박을 남은 날 순으로 먼저, 그다음 점수순이고, 나머지는 "그 외"로 센다', () => {
    const grid = quadrantGrid(
      [
        at(0.9, 9.8, { id: 'top', score: 99 }),
        urgent('d2', '2026-11-03', 10),
        urgent('d0', '2026-11-01', 5),
        at(0.9, 9.8, { id: 'mid', score: 50 }),
        at(0.9, 9.8, { id: 'low', score: 20 }),
      ],
      TODAY,
    )

    expect(grid.cells.hot.representatives.map((row) => row.id)).toEqual(['d0', 'd2', 'top'])
    expect(grid.cells.hot.more).toBe(2)
  })

  it('CVE 번호는 끝자리만 줄여 적는다', () => {
    expect(shortCveId('CVE-2026-97229')).toBe('97229')
  })
})
