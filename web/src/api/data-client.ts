// 배치가 쓴 정적 JSON(web/public/data)을 읽는다. 기획서 7장 캐시 원칙(P16):
// meta.json 은 매번 서버에 확인(no-cache), 나머지는 ?v=<meta.version> 으로 새 배치 때만 다시 받는다.
import type { Categories } from '../types/categories.ts'
import type { CveDetail } from '../types/cve.ts'
import type { Meta } from '../types/meta.ts'
import type { SearchIndex } from '../types/search.ts'
import type { Stats } from '../types/stats.ts'
import type { RecentList, TrendIndex } from '../types/trend.ts'
import { DataLoadError } from './data-load-error.ts'

type FetchFn = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>

export interface DataClient {
  meta(): Promise<Meta>
  stats(version: string): Promise<Stats>
  trendIndex(version: string): Promise<TrendIndex>
  /** 최근 7일 새 CVE. 그 화면에 들어올 때만 읽는다 */
  recent(version: string): Promise<RecentList>
  searchIndex(version: string): Promise<SearchIndex>
  /** 공격 유형·제품군 분류. 배치가 아직 내보내지 않았으면(404) `null` */
  categories(version: string): Promise<Categories | null>
  /** 상세 파일을 만들지 않은 CVE(P6)는 `null` */
  cveDetail(cveId: string, version: string): Promise<CveDetail | null>
}

const NOT_FOUND = 404

/**
 * fetchFn: 브라우저 fetch (테스트는 가짜 서버)
 * baseUrl: 사이트 기본 경로. 기본값은 빌드의 base (`/vuln-trend/`, 도메인 연결 시 `/`)
 */
export const createDataClient = (fetchFn: FetchFn, baseUrl: string = import.meta.env.BASE_URL): DataClient => {
  const dataUrl = (path: string): string => `${baseUrl}data/${path}`
  const versionedUrl = (path: string, version: string): string => `${dataUrl(path)}?v=${encodeURIComponent(version)}`

  // 같은 URL 은 한 번만 받는다. 실패한 요청은 지워서 다시 시도할 때 새로 받게 한다
  const inFlight = new Map<string, Promise<unknown>>()

  const request = async (url: string, init: RequestInit): Promise<Response> => {
    try {
      return await fetchFn(url, init)
    } catch (cause) {
      throw new DataLoadError(url, null, { cause })
    }
  }

  const readJson = async <T>(url: string, init: RequestInit, missingAsNull: boolean): Promise<T | null> => {
    const response = await request(url, init)
    if (missingAsNull && response.status === NOT_FOUND) return null
    if (response.ok === false) throw new DataLoadError(url, response.status)
    return (await response.json()) as T
  }

  const load = <T>(url: string, init: RequestInit = {}, missingAsNull = false): Promise<T | null> => {
    const cached = inFlight.get(url)
    if (cached !== undefined) return cached as Promise<T | null>

    const pending = readJson<T>(url, init, missingAsNull)
    inFlight.set(url, pending)
    pending.catch(() => inFlight.delete(url))
    return pending
  }

  const loadRequired = async <T>(url: string, init?: RequestInit): Promise<T> => (await load<T>(url, init)) as T

  return {
    meta: () => loadRequired<Meta>(dataUrl('meta.json'), { cache: 'no-cache' }),
    stats: (version) => loadRequired<Stats>(versionedUrl('stats.json', version)),
    trendIndex: (version) => loadRequired<TrendIndex>(versionedUrl('index.json', version)),
    recent: (version) => loadRequired<RecentList>(versionedUrl('recent.json', version)),
    searchIndex: (version) => loadRequired<SearchIndex>(versionedUrl('search-index.json', version)),
    categories: (version) => load<Categories>(versionedUrl('categories.json', version), {}, true),
    cveDetail: (cveId, version) => load<CveDetail>(versionedUrl(`cve/${cveId}.json`, version), {}, true),
  }
}
