// "어디에 몰려 있나" 제품 막대 (design/v6 b-products · CHART-REVIEW P1·P2).
// 기준 = 주목 목록 100건 중 제품별 건수(제품별 주간 통계가 데이터에 없음). 상위 5 + "그 외 n개 제품" 합계를 같은 척도로
import type { IsoDate } from '../types/common.ts'
import type { TrendRow } from '../types/trend.ts'
import { compareProducts } from './product-rank.ts'
import { isUrgent, productName } from './urgency.ts'

export type SeverityBucket = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE'

export const SEVERITY_BUCKETS: { key: SeverityBucket; label: string }[] = [
  { key: 'CRITICAL', label: 'Critical' },
  { key: 'HIGH', label: 'High' },
  { key: 'MEDIUM', label: 'Medium' },
  { key: 'LOW', label: 'Low' },
  { key: 'NONE', label: '등급 없음' },
]

export interface ProductBar {
  /** productName(row). 제품 정보가 없으면 "제품 미상" */
  name: string
  count: number
  /** CISA 기한 D-0~3 건수 */
  urgent: number
  /** 가장 높은 점수 */
  top: number
  bySeverity: Partial<Record<SeverityBucket, number>>
}

export interface ProductBars {
  ranked: ProductBar[]
  /** 상위 밖 제품들. 없으면 `null` */
  rest: { products: number; count: number; maxPerProduct: number } | null
  /** 막대 척도 = 1위와 "그 외" 합계 중 큰 값 */
  scale: number
  total: number
  /** 상위 제품 합계 */
  topCount: number
  /** 1위가 20% 이상이거나 상위 5개가 50% 이상 */
  concentrated: boolean
  /** 상위 막대에 실제로 있는 심각도만 (범례) */
  usedSeverities: SeverityBucket[]
}

export const TOP_PRODUCT_BARS = 5

const CONCENTRATED_TOP = 0.2
const CONCENTRATED_TOP5 = 0.5

/** 미평가·계약 밖 값은 "등급 없음" */
export const severityBucket = (row: TrendRow): SeverityBucket => {
  const found = SEVERITY_BUCKETS.find((bucket) => bucket.key === row.severity)
  return found === undefined ? 'NONE' : found.key
}

export const productBars = (rows: TrendRow[], today: IsoDate): ProductBars => {
  const groups = new Map<string, ProductBar>()
  rows.forEach((row) => {
    const name = productName(row)
    const bar = groups.get(name) ?? { name, count: 0, urgent: 0, top: 0, bySeverity: {} }
    const bucket = severityBucket(row)
    groups.set(name, {
      name,
      count: bar.count + 1,
      urgent: bar.urgent + (isUrgent(row, today) ? 1 : 0),
      top: Math.max(bar.top, row.score),
      bySeverity: { ...bar.bySeverity, [bucket]: (bar.bySeverity[bucket] ?? 0) + 1 },
    })
  })
  const all = [...groups.values()].sort(compareProducts)
  const ranked = all.slice(0, TOP_PRODUCT_BARS)
  const others = all.slice(TOP_PRODUCT_BARS)
  const restCount = others.reduce((sum, bar) => sum + bar.count, 0)
  const topCount = ranked.reduce((sum, bar) => sum + bar.count, 0)
  const total = rows.length
  return {
    ranked,
    rest:
      others.length === 0
        ? null
        : { products: others.length, count: restCount, maxPerProduct: Math.max(...others.map((bar) => bar.count)) },
    scale: Math.max(1, ranked[0]?.count ?? 1, restCount),
    total,
    topCount,
    concentrated:
      total > 0 &&
      ranked.length > 0 &&
      (ranked[0].count / total >= CONCENTRATED_TOP || topCount / total >= CONCENTRATED_TOP5),
    usedSeverities: SEVERITY_BUCKETS.map((bucket) => bucket.key).filter((key) =>
      ranked.some((bar) => (bar.bySeverity[key] ?? 0) > 0),
    ),
  }
}
