import { useContext } from 'react'
import { MineContext, type MineContextValue } from './mine-context.ts'

export const useMine = (): MineContextValue => {
  const context = useContext(MineContext)
  if (context === null) throw new Error('useMine must be used inside <MineProvider>')
  return context
}

/** 공급자가 없으면(단위 시험 등) `null` — 별표·배지를 그리지 않는다 */
export const useMineOptional = (): MineContextValue | null => useContext(MineContext)
