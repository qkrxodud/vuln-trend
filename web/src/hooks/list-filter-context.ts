import { createContext } from 'react'
import type { ListFilter, ListSignal } from '../lib/list-model.ts'
import type { Quadrant } from '../lib/quadrants.ts'

export interface ListFilterContextValue {
  filter: ListFilter
  setSignal: (signal: ListSignal) => void
  /** `null` 이면 모든 제품 */
  setProduct: (product: string | null) => void
  /** `null` 이면 모든 칸 */
  setQuadrant: (quadrant: Quadrant | null) => void
  clear: () => void
}

export const ListFilterContext = createContext<ListFilterContextValue | null>(null)
