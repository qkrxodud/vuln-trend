import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import {
  countByFilter,
  formatCvss,
  formatDueDate,
  formatEpss,
  formatTopPercent,
  groupByProduct,
  NO_PRODUCT,
} from './trend-list.ts'

describe('목록 행 숫자 표기', () => {
  it('EPSS 는 소수 둘째 자리까지 적고, 없으면 대시로 둔다', () => {
    expect(formatEpss(0.9123)).toBe('0.91')
    expect(formatEpss(null)).toBe('—')
  })

  it('0.01 보다 작은 EPSS 는 0 으로 읽히지 않게 "<0.01"로 적는다', () => {
    expect(formatEpss(0.002)).toBe('<0.01')
    expect(formatEpss(0)).toBe('0.00')
  })

  it('EPSS percentile 은 "상위 N%"로 바꾸고, 아무리 높아도 상위 1% 로 적는다', () => {
    expect(formatTopPercent(0.97)).toBe('상위 3%')
    expect(formatTopPercent(0.999)).toBe('상위 1%')
    expect(formatTopPercent(null)).toBeNull()
  })

  it('CVSS 는 한 자리 소수로 적고, 4.0 점수는 (v4) 를 붙이며, 없으면 미평가', () => {
    expect(formatCvss(8.8, '3.1')).toBe('8.8')
    expect(formatCvss(9.3, '4.0')).toBe('9.3 (v4)')
    expect(formatCvss(10, '3.1')).toBe('10.0')
    expect(formatCvss(null, null)).toBe('미평가')
  })

  it('KEV 패치 기한은 "월-일"로 적는다', () => {
    expect(formatDueDate('2026-11-13')).toBe('11-13')
  })
})

describe('필터별 건수', () => {
  it('전체·KEV·Critical 버튼에 붙일 건수를 센다', () => {
    const rows = [
      trendRow({ is_kev: true, severity: 'CRITICAL' }),
      trendRow({ is_kev: true, severity: 'HIGH' }),
      trendRow({ severity: 'CRITICAL' }),
      trendRow(),
    ]

    expect(countByFilter(rows)).toEqual({ all: 4, kev: 2, critical: 2 })
  })
})

describe('소프트웨어별 묶음', () => {
  it('제품마다 묶고, 묶음은 그 안의 최고 점수순으로 놓는다', () => {
    const rows = [
      trendRow({ primary_product: 'FortiOS', score: 91, is_kev: true }),
      trendRow({ primary_product: 'Chrome', score: 88, is_kev: true }),
      trendRow({ primary_product: 'FortiOS', score: 40 }),
      trendRow({ primary_product: 'Chrome', score: 30 }),
    ]

    const groups = groupByProduct(rows)

    expect(groups.map((group) => [group.name, group.rows.length, group.kevCount, group.topScore])).toEqual([
      ['FortiOS', 2, 1, 91],
      ['Chrome', 2, 1, 88],
    ])
  })

  it('제품 정보가 없는 취약점은 "제품 정보 없음" 묶음에 모은다', () => {
    const groups = groupByProduct([trendRow({ primary_product: null })])

    expect(groups[0].name).toBe(NO_PRODUCT)
    expect(NO_PRODUCT).toBe('제품 정보 없음')
  })
})
