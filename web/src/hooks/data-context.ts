import { createContext } from 'react'
import type { DataClient } from '../api/data-client.ts'
import type { Meta } from '../types/meta.ts'
import type { Loadable } from './loadable.ts'

export interface DataContextValue {
  client: DataClient
  meta: Loadable<Meta>
  /** 받아 둔 데이터(키 → 값). 나중에 붙는 화면이 처음부터 준비된 상태로 그리게 한다 */
  resolved: Map<string, unknown>
}

export const DataContext = createContext<DataContextValue | null>(null)
