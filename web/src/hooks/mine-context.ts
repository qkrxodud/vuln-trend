import { createContext } from 'react'
import type { MineState } from '../lib/mine.ts'

export interface MineContextValue {
  mine: MineState
  /** 고른 제품을 더하거나 뺀다 */
  toggle: (product: string) => void
  /** /mine 을 본 때(meta.updated_at)를 남긴다 — 다음 방문의 "새 n건" 기준 */
  markSeen: (updatedAt: string) => void
}

export const MineContext = createContext<MineContextValue | null>(null)
