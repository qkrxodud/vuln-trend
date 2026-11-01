import { useMemo, useState, type ReactNode } from 'react'
import { ALL_ROWS, type ListFilter } from '../lib/list-model.ts'
import { ListFilterContext } from './list-filter-context.ts'

// 목록의 신호·제품·칸 필터. 제품 막대와 4칸 건수표도 같은 상태를 건다 (design/v6 버스 'product')
export const ListFilterProvider = ({ children }: { children: ReactNode }) => {
  const [filter, setFilter] = useState<ListFilter>(ALL_ROWS)
  const value = useMemo(
    () => ({
      filter,
      setSignal: (signal: ListFilter['signal']) => setFilter((current) => ({ ...current, signal })),
      setProduct: (product: string | null) => setFilter((current) => ({ ...current, product })),
      setQuadrant: (quadrant: ListFilter['quadrant']) => setFilter((current) => ({ ...current, quadrant })),
      clear: () => setFilter(ALL_ROWS),
    }),
    [filter],
  )
  return <ListFilterContext value={value}>{children}</ListFilterContext>
}
