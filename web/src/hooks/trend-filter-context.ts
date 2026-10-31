import { createContext } from 'react'
import type { TrendFilter } from '../lib/trend-filter.ts'

export interface TrendFilterContextValue {
  filter: TrendFilter
  setFilter: (filter: TrendFilter) => void
}

export const TrendFilterContext = createContext<TrendFilterContextValue | null>(null)
