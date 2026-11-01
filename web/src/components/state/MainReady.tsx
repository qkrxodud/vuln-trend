import type { ReactNode } from 'react'
import { useMeta, useStats, useTrendIndex } from '../../hooks/use-data.ts'
import { LoadingState } from './LoadingState.tsx'

// 메인 화면은 meta·index·stats 가 다 온 뒤(또는 실패한 뒤) 한꺼번에 그린다.
// 영역마다 따로 "불러오는 중"을 띄웠다가 커지면 아래가 밀린다(CLS). 기다리는 동안은 화면 높이만큼 자리를 잡아 둔다 (v6 D7)
export const MainReady = ({ children }: { children: ReactNode }) => {
  const settled = [useMeta(), useTrendIndex(), useStats()].every(
    (loadable) => loadable.status === 'ready' || loadable.status === 'error',
  )
  if (settled) return children
  return (
    <div className="min-h-screen">
      <LoadingState label="오늘 고칠 취약점" />
    </div>
  )
}
