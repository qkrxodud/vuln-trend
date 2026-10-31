import { createContext } from 'react'
import type { DataClient } from '../api/data-client.ts'
import type { Meta } from '../types/meta.ts'
import type { Loadable } from './loadable.ts'

export interface DataContextValue {
  client: DataClient
  meta: Loadable<Meta>
}

export const DataContext = createContext<DataContextValue | null>(null)
