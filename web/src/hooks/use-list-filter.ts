import { useContext } from 'react'
import { ListFilterContext, type ListFilterContextValue } from './list-filter-context.ts'

export const useListFilter = (): ListFilterContextValue => {
  const context = useContext(ListFilterContext)
  if (context === null) throw new Error('useListFilter must be used inside <ListFilterProvider>')
  return context
}
