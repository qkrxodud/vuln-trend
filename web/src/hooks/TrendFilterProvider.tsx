import { useMemo, useState, type ReactNode } from 'react'
import type { TrendFilter } from '../lib/trend-filter.ts'
import { TrendFilterContext } from './trend-filter-context.ts'

// 헤더 필터·목록 옆 필터·KPI 카드가 같은 필터 상태를 쓴다 (R1)
export const TrendFilterProvider = ({ children }: { children: ReactNode }) => {
  const [filter, setFilter] = useState<TrendFilter>('all')
  const value = useMemo(() => ({ filter, setFilter }), [filter])
  return <TrendFilterContext value={value}>{children}</TrendFilterContext>
}
