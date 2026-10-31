import type { ReactNode } from 'react'
import type { Loadable } from '../../hooks/loadable.ts'
import { ErrorState } from './ErrorState.tsx'
import { LoadingState } from './LoadingState.tsx'

interface LoadableViewProps<T> {
  loadable: Loadable<T>
  /** 무엇을 불러오는지. 안내 문구에 쓴다 (예: "통계") */
  label: string
  children: (data: T) => ReactNode
}

// 데이터 영역 하나의 불러오기·실패·준비 상태를 한 곳에서 그린다
export const LoadableView = <T,>({ loadable, label, children }: LoadableViewProps<T>) => {
  if (loadable.status === 'idle') return null
  if (loadable.status === 'loading') return <LoadingState label={label} />
  if (loadable.status === 'error') return <ErrorState label={label} onRetry={loadable.retry} />
  return children(loadable.data)
}
