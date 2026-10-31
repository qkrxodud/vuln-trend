import { useContext } from 'react'
import { TrendFilterContext, type TrendFilterContextValue } from './trend-filter-context.ts'

export const useTrendFilter = (): TrendFilterContextValue => {
  const context = useContext(TrendFilterContext)
  if (context === null) throw new Error('useTrendFilter must be used inside <TrendFilterProvider>')
  return context
}
