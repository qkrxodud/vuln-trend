import { useCallback, useContext } from 'react'
import type { DataClient } from '../api/data-client.ts'
import type { CveDetail } from '../types/cve.ts'
import type { Meta } from '../types/meta.ts'
import type { SearchIndex } from '../types/search.ts'
import type { Stats } from '../types/stats.ts'
import type { TrendIndex } from '../types/trend.ts'
import { DataContext, type DataContextValue } from './data-context.ts'
import type { Loadable } from './loadable.ts'
import { useLoadable } from './use-loadable.ts'

const useDataContext = (): DataContextValue => {
  const context = useContext(DataContext)
  if (context === null) throw new Error('Data hooks must be used inside <DataProvider>')
  return context
}

/**
 * meta 버전이 정해진 뒤에 name 데이터를 읽는다.
 * name 이 `null` 이면 요청하지 않는다(idle). meta 가 실패하면 그 실패(다시 시도 포함)를 그대로 보인다.
 */
const useVersioned = <T>(
  name: string | null,
  load: (client: DataClient, version: string) => Promise<T>,
): Loadable<T> => {
  const { client, meta, resolved } = useDataContext()
  const version = meta.status === 'ready' ? meta.data.version : null
  const loadVersion = useCallback(() => load(client, version ?? ''), [load, client, version])
  const key = name === null || version === null ? null : `${name}@${version}`
  const result = useLoadable(key, loadVersion, resolved)

  if (name === null) return { status: 'idle' }
  if (meta.status === 'error') return meta
  if (version === null) return { status: 'loading' }
  return result
}

const loadStats = (client: DataClient, version: string) => client.stats(version)
const loadTrendIndex = (client: DataClient, version: string) => client.trendIndex(version)
const loadSearchIndex = (client: DataClient, version: string) => client.searchIndex(version)

export const useMeta = (): Loadable<Meta> => useDataContext().meta

export const useStats = (): Loadable<Stats> => useVersioned('stats', loadStats)

export const useTrendIndex = (): Loadable<TrendIndex> => useVersioned('index', loadTrendIndex)

/** 1년치 수 MB 라 검색창을 쓰기 시작할 때(enabled)만 받는다 (P19) */
export const useSearchIndex = (enabled: boolean): Loadable<SearchIndex> =>
  useVersioned(enabled ? 'search-index' : null, loadSearchIndex)

/** 고른 CVE 의 상세. 상세 파일이 없는 CVE 는 `ready` + `null` (NVD 링크로 안내) */
export const useCveDetail = (cveId: string | null): Loadable<CveDetail | null> => {
  const loadDetail = useCallback(
    (client: DataClient, version: string) => client.cveDetail(cveId ?? '', version),
    [cveId],
  )
  return useVersioned(cveId === null ? null : `cve/${cveId}`, loadDetail)
}
