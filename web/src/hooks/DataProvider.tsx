import { useCallback, useMemo, useState, type ReactNode } from 'react'
import type { DataClient } from '../api/data-client.ts'
import { DataContext } from './data-context.ts'
import { useLoadable } from './use-loadable.ts'

// 앱 전체에서 meta.json 을 한 번 읽고, 그 버전을 다른 데이터 훅이 캐시 버스팅에 쓴다
export const DataProvider = ({ client, children }: { client: DataClient; children: ReactNode }) => {
  const loadMeta = useCallback(() => client.meta(), [client])
  const meta = useLoadable('meta', loadMeta)
  // 공급자마다 하나(시험끼리 섞이지 않게). 클라이언트의 같은 URL 한 번만 받기와 짝을 이룬다
  const [resolved] = useState(() => new Map<string, unknown>())
  const value = useMemo(() => ({ client, meta, resolved }), [client, meta, resolved])
  return <DataContext value={value}>{children}</DataContext>
}
