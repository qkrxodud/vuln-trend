// v6 목록 모델 (design/v6 c-list, CONSENSUS 3절): 신호·제품으로 거르고, 기한 임박을 맨 위에 고정, 나머지는 점수순
import type { IsoDate } from '../types/common.ts'
import type { TrendRow } from '../types/trend.ts'
import { compareProducts, type RankedProduct } from './product-rank.ts'
import { isUrgent, kevAddedDaysAgo, NEW_KEV_WITHIN_DAYS, productName, urgentRows } from './urgency.ts'

export type ListSignal = 'all' | 'urgent' | 'ransomware' | 'new-kev' | 'poc'

export interface ListFilter {
  signal: ListSignal
  /** productName(row) 값. `null` 이면 모든 제품 */
  product: string | null
}

export const ALL_ROWS: ListFilter = { signal: 'all', product: null }

export const SIGNALS: { key: ListSignal; label: string }[] = [
  { key: 'all', label: '전체' },
  { key: 'urgent', label: 'CISA 기한 임박' },
  { key: 'ransomware', label: '랜섬웨어' },
  { key: 'new-kev', label: '새 KEV 7일' },
  { key: 'poc', label: 'PoC 공개' },
]

/** 나머지 묶음은 20건씩, 기한 임박 묶음은 처음 10건 */
export const REST_PAGE = 20
export const URGENT_PAGE = 10
/** 제품 칩으로 바로 보이는 제품 수. 나머지는 "그 외 n개 제품" 선택 상자 */
export const TOP_PRODUCTS = 5

const byScore = (a: TrendRow, b: TrendRow) => b.score - a.score

/** 새 KEV = 등재 0~7일 전 */
export const isNewKev = (row: TrendRow, today: IsoDate): boolean => {
  const age = kevAddedDaysAgo(row, today)
  return row.is_kev && age !== null && age >= 0 && age <= NEW_KEV_WITHIN_DAYS
}

const SIGNAL_TESTS: Record<ListSignal, (row: TrendRow, today: IsoDate) => boolean> = {
  all: () => true,
  urgent: isUrgent,
  ransomware: (row) => row.kev_ransomware,
  'new-kev': isNewKev,
  poc: (row) => row.poc_count > 0,
}

export const matchesSignal = (row: TrendRow, signal: ListSignal, today: IsoDate): boolean =>
  SIGNAL_TESTS[signal](row, today)

export interface SignalOption {
  key: ListSignal
  label: string
  count: number
}

/** 신호 버튼. 0건인 신호는 숨긴다(지금 고른 것은 남긴다) — 예: PoC 수집 전 */
export const signalOptions = (rows: TrendRow[], current: ListSignal, today: IsoDate): SignalOption[] =>
  SIGNALS.map(({ key, label }) => ({
    key,
    label,
    count: rows.filter((row) => matchesSignal(row, key, today)).length,
  })).filter((option) => option.key === 'all' || option.key === current || option.count > 0)

export interface ProductCount {
  name: string
  count: number
}

/** 제품별 건수. 순서는 제품 막대와 같은 compareProducts. 건수는 지금 고른 신호 안에서 센다 */
export const productCounts = (rows: TrendRow[], signal: ListSignal, today: IsoDate): ProductCount[] => {
  const products = new Map<string, RankedProduct>()
  rows
    .filter((row) => matchesSignal(row, signal, today))
    .forEach((row) => {
      const name = productName(row)
      const product = products.get(name) ?? { name, count: 0, urgent: 0, top: 0 }
      products.set(name, {
        name,
        count: product.count + 1,
        urgent: product.urgent + (isUrgent(row, today) ? 1 : 0),
        top: Math.max(product.top, row.score),
      })
    })
  return [...products.values()].sort(compareProducts).map(({ name, count }) => ({ name, count }))
}

export interface ListTiers {
  /** 기한 임박 고정 묶음 (D-0 부터). 신호 필터를 걸면 비어 있다 */
  urgent: TrendRow[]
  /** 나머지, 점수 높은 순 */
  rest: TrendRow[]
  /** 필터에 맞는 전체 건수 */
  total: number
}

export const listTiers = (rows: TrendRow[], filter: ListFilter, today: IsoDate): ListTiers => {
  const matched = rows
    .filter((row) => matchesSignal(row, filter.signal, today))
    .filter((row) => filter.product === null || productName(row) === filter.product)
    .sort(byScore)
  if (filter.signal !== 'all') return { urgent: [], rest: matched, total: matched.length }
  return {
    urgent: urgentRows(matched, today),
    rest: matched.filter((row) => isUrgent(row, today) === false),
    total: matched.length,
  }
}

/** 점수 순위 (1위부터). 필터와 상관없이 주목 목록 전체 기준 */
export const scoreRanks = (rows: TrendRow[]): Map<string, number> =>
  new Map([...rows].sort(byScore).map((row, index) => [row.id, index + 1]))

/** 처음 고른 상태로 둘 취약점 = 목록 맨 위(기한 임박이 있으면 그 첫 건, 없으면 점수 1위) */
export const defaultSelectedId = (rows: TrendRow[], today: IsoDate): string | null => {
  const { urgent, rest } = listTiers(rows, ALL_ROWS, today)
  return urgent[0]?.id ?? rest[0]?.id ?? null
}

/** EPSS 확률을 % 로. 10% 이상 정수, 1% 이상 소수 1자리, 그 아래 2자리, 0.01% 미만은 "<0.01%" */
export const formatEpssPercent = (epss: number): string => {
  const percent = epss * 100
  if (percent >= 99.95) return '100%'
  if (percent >= 10) return `${percent.toFixed(0)}%`
  if (percent >= 1) return `${percent.toFixed(1)}%`
  if (percent >= 0.01) return `${percent.toFixed(2)}%`
  return '<0.01%'
}

/** EPSS percentile → "상위 1% 안" / "상위 3%" */
export const formatEpssRank = (percentile: number): string => {
  const top = (1 - percentile) * 100
  if (top < 1) return '상위 1% 안'
  return `상위 ${Math.ceil(top - 1e-9)}%`
}
