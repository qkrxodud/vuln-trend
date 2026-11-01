import { use, useMemo, useState, type ReactNode } from 'react'
import { EMPTY_MINE, MINE_STORAGE_KEY, parseMine, toggleProduct, type MineState } from '../lib/mine.ts'
import { DataContext } from './data-context.ts'
import { MineContext } from './mine-context.ts'

// 저장소를 쓸 수 없는 브라우저(사생활 보호 모드 등)에서는 이 화면 동안만 기억한다
const load = (): MineState => {
  try {
    return parseMine(localStorage.getItem(MINE_STORAGE_KEY))
  } catch {
    return EMPTY_MINE
  }
}

const save = (state: MineState) => {
  try {
    localStorage.setItem(MINE_STORAGE_KEY, JSON.stringify(state))
  } catch {
    // 저장 못 해도 화면 상태는 유지한다
  }
}

// "내 제품" 상태 하나를 머리글 배지·메인 한 줄·/mine·목록 별표가 같이 쓴다 (사용자 결정 2026-11-01)
export const MineProvider = ({ children }: { children: ReactNode }) => {
  const [mine, setMine] = useState<MineState>(load)
  // 처음 고른 때의 기준 시각. 데이터가 아직 안 왔으면 null 이고, 그때는 /mine 을 열 때 잡는다
  const meta = use(DataContext)?.meta ?? null
  const now = meta !== null && meta.status === 'ready' ? meta.data.updated_at : null
  const value = useMemo(() => {
    const update = (next: MineState) => {
      save(next)
      setMine(next)
    }
    return {
      mine,
      toggle: (product: string) => update(toggleProduct(mine, product, now)),
      markSeen: (updatedAt: string) => {
        if (mine.seenAt !== updatedAt) update({ ...mine, seenAt: updatedAt })
      },
    }
  }, [mine, now])
  return <MineContext value={value}>{children}</MineContext>
}
