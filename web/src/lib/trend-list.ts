// 트렌드 목록 규칙 (기획서 8장 목록 행, D7·D15)
import type { CvssVersion } from '../types/common.ts'
import type { TrendRow } from '../types/trend.ts'
import { matchesTrendFilter, type TrendFilter } from './trend-filter.ts'

/** 처음 보이는 건수. 나머지는 "더 보기" (index.json 100건 안에서) */
export const PAGE_SIZE = 20

export const NO_PRODUCT = '제품 정보 없음'

export type TrendView = 'product' | 'score'

export interface ProductGroup {
  name: string
  rows: TrendRow[]
  kevCount: number
  topScore: number
}

const EPSS_DISPLAY_FLOOR = 0.01

/** 소수 둘째 자리. 0 은 아니지만 0.01 보다 작으면 `<0.01` (0 으로 읽히지 않게) */
export const formatEpss = (epss: number | null): string => {
  if (epss === null) return '—'
  if (epss > 0 && epss < EPSS_DISPLAY_FLOOR) return '<0.01'
  return epss.toFixed(2)
}

/** percentile 0.97 → `상위 3%`. 반올림해 0% 가 되면 `상위 1%` */
export const formatTopPercent = (percentile: number | null): string | null => {
  if (percentile === null) return null
  return `상위 ${Math.max(1, Math.round((1 - percentile) * 100))}%`
}

export const formatCvss = (cvss: number | null, version: CvssVersion | null): string => {
  if (cvss === null) return '미평가'
  const score = cvss.toFixed(1)
  return version === '4.0' ? `${score} (v4)` : score
}

/** `2026-11-13` → `11-13` */
export const formatDueDate = (isoDate: string): string => isoDate.slice(5)

export const countByFilter = (rows: TrendRow[]): Record<TrendFilter, number> => ({
  all: rows.length,
  kev: rows.filter((row) => matchesTrendFilter(row, 'kev')).length,
  critical: rows.filter((row) => matchesTrendFilter(row, 'critical')).length,
})

/** 점수 내림차순 행을 제품별로 묶는다. 처음 나온 순서 = 묶음의 최고 점수순 */
export const groupByProduct = (rows: TrendRow[]): ProductGroup[] => {
  const groups = new Map<string, TrendRow[]>()
  for (const row of rows) {
    const name = row.primary_product ?? NO_PRODUCT
    groups.set(name, [...(groups.get(name) ?? []), row])
  }
  return [...groups].map(([name, groupRows]) => ({
    name,
    rows: groupRows,
    kevCount: groupRows.filter((row) => row.is_kev).length,
    topScore: Math.max(...groupRows.map((row) => row.score)),
  }))
}
