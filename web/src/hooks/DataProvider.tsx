import { useCallback, useMemo, type ReactNode } from 'react'
import type { DataClient } from '../api/data-client.ts'
import { DataContext } from './data-context.ts'
import { useLoadable } from './use-loadable.ts'

// 앱 전체에서 meta.json 을 한 번 읽고, 그 버전을 다른 데이터 훅이 캐시 버스팅에 쓴다
export const DataProvider = ({ client, children }: { client: DataClient; children: ReactNode }) => {
  const loadMeta = useCallback(() => client.meta(), [client])
  const meta = useLoadable('meta', loadMeta)
  const value = useMemo(() => ({ client, meta }), [client, meta])
  return <DataContext value={value}>{children}</DataContext>
}
