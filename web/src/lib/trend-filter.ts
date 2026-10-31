import type { TrendRow } from '../types/trend.ts'

/** MVP 필터 한 세트: 전체 / KEV / Critical (기획서 8장) */
export type TrendFilter = 'all' | 'kev' | 'critical'

export const TREND_FILTERS: { value: TrendFilter; label: string }[] = [
  { value: 'all', label: '전체' },
  { value: 'kev', label: 'KEV' },
  { value: 'critical', label: 'Critical' },
]

export const matchesTrendFilter = (row: TrendRow, filter: TrendFilter): boolean => {
  if (filter === 'kev') return row.is_kev
  if (filter === 'critical') return row.severity === 'CRITICAL'
  return true
}
