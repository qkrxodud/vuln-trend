import { useMemo, useState, type ReactNode } from 'react'
import { ALL_ROWS, type ListFilter } from '../lib/list-model.ts'
import { ListFilterContext } from './list-filter-context.ts'

// 목록의 신호·제품 필터. 제품 막대(D4)·산점도(D3)도 같은 상태를 건다 (design/v6 버스 'product')
export const ListFilterProvider = ({ children }: { children: ReactNode }) => {
  const [filter, setFilter] = useState<ListFilter>(ALL_ROWS)
  const value = useMemo(
    () => ({
      filter,
      setSignal: (signal: ListFilter['signal']) => setFilter((current) => ({ ...current, signal })),
      setProduct: (product: string | null) => setFilter((current) => ({ ...current, product })),
      clear: () => setFilter(ALL_ROWS),
    }),
    [filter],
  )
  return <ListFilterContext value={value}>{children}</ListFilterContext>
}
