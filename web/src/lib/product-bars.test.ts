import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import type { Severity } from '../types/common.ts'
import type { TrendRow } from '../types/trend.ts'
import { productBars, severityBucket } from './product-bars.ts'

const TODAY = '2026-11-01'
const row = (product: string | null, overrides: Partial<TrendRow> = {}) =>
  trendRow({ primary_product: product, severity: 'HIGH', score: 50, ...overrides })

describe('제품별 건수', () => {
  it('건수 많은 순, 같으면 기한 임박이 많은 순, 그다음 최고 점수 순으로 상위 5개를 고른다', () => {
    const rows = [
      row('Linux Kernel'),
      row('Linux Kernel'),
      row('JFrog', { score: 40 }),
      row('Plex', { is_kev: true, kev_due_date: '2026-11-02' }),
      row('Windows', { score: 80 }),
      row('Apache'),
      row('Zabbix'),
    ]

    expect(productBars(rows, TODAY).ranked.map((bar) => bar.name)).toEqual([
      'Linux Kernel',
      'Plex',
      'Windows',
      'Apache',
      'Zabbix',
    ])
  })

  it('제품마다 심각도별 건수와 CISA 기한 임박 건수를 센다. 제품 정보가 없으면 "제품 미상"이다', () => {
    const bars = productBars(
      [
        row(null, { severity: 'CRITICAL', is_kev: true, kev_due_date: '2026-11-01' }),
        row(null, { severity: 'UNRATED' }),
      ],
      TODAY,
    )

    expect(bars.ranked).toEqual([
      { name: '제품 미상', count: 2, urgent: 1, top: 50, bySeverity: { CRITICAL: 1, NONE: 1 } },
    ])
  })

  it('미평가나 계약 밖 심각도는 "등급 없음"으로 묶는다', () => {
    expect(severityBucket(row('A', { severity: 'UNRATED' }))).toBe('NONE')
    expect(severityBucket(row('A', { severity: null as unknown as Severity }))).toBe('NONE')
    expect(severityBucket(row('A', { severity: 'LOW' }))).toBe('LOW')
  })
})

describe('그 외 제품과 척도', () => {
  const products = ['A', 'A', 'A', 'B', 'B', 'C', 'D', 'E', 'F', 'G', 'G', 'H']
  const bars = productBars(
    products.map((product) => row(product)),
    TODAY,
  )

  it('상위 5개 밖은 제품 수·합계·제품당 최대 건수로 한 줄에 묶는다', () => {
    expect(bars.rest).toEqual({ products: 3, count: 3, maxPerProduct: 1 })
  })

  it('막대 척도는 1위와 "그 외" 합계 중 큰 값이다', () => {
    expect(bars.scale).toBe(3)
    expect(bars.topCount).toBe(9)
    expect(bars.total).toBe(12)
  })

  it('상위 밖 제품이 없으면 "그 외" 줄도 없다', () => {
    expect(productBars([row('A'), row('B')], TODAY).rest).toBeNull()
  })
})

describe('몰려 있나', () => {
  it('1위가 20% 이상이거나 상위 5개가 절반 이상이면 몰려 있다', () => {
    const longTail = Array.from({ length: 30 }, (_, i) => row(`P${i}`))

    expect(productBars(longTail, TODAY).concentrated).toBe(false)
    expect(productBars([...longTail, ...Array.from({ length: 8 }, () => row('Big'))], TODAY).concentrated).toBe(true)
  })

  it('범례에는 상위 막대에 실제로 있는 심각도만 둔다', () => {
    const bars = productBars([row('A', { severity: 'CRITICAL' }), row('B', { severity: 'MEDIUM' })], TODAY)

    expect(bars.usedSeverities).toEqual(['CRITICAL', 'MEDIUM'])
  })
})
