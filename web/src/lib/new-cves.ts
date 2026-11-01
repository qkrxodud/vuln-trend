// "최근 7일 새 CVE" 화면 (사용자 결정 2026-11-01: 주목 100건 밖의 새 CVE 도 모두 보기).
// recent.json(약 3,400건)을 화면에서 거르고 정렬한다. 한 번에 50건씩 그린다
import type { IsoDate, Severity } from '../types/common.ts'
import type { TrendRow } from '../types/trend.ts'
import { formatKstDate } from './kst-time.ts'
import { productName } from './urgency.ts'

export const NEW_PAGE = 50

export type NewSort = 'latest' | 'score'

export interface NewFilter {
  /** 비어 있으면 모든 심각도 */
  severities: Severity[]
  kevOnly: boolean
  /** 수정 버전이나 패치 링크가 있는 것만 */
  fixOnly: boolean
  /** 제품 이름 일부(대소문자 무시). 빈 글자면 모든 제품 */
  product: string
  /** KST 공개일. `null` 이면 모든 날 */
  date: IsoDate | null
}

export const ALL_NEW: NewFilter = { severities: [], kevOnly: false, fixOnly: false, product: '', date: null }

export const isFiltered = (filter: NewFilter): boolean =>
  filter.severities.length > 0 ||
  filter.kevOnly ||
  filter.fixOnly ||
  filter.product.trim() !== '' ||
  filter.date !== null

export const hasFixInfo = (row: TrendRow): boolean =>
  (row.fix_versions ?? []).length > 0 || (row.patch_url ?? null) !== null

/** 공개일(KST). published_at 이 없으면 `null` */
export const publishedDate = (row: TrendRow): IsoDate | null => {
  const published = row.published_at ?? null
  return published === null ? null : formatKstDate(published)
}

export const filterNew = (rows: TrendRow[], filter: NewFilter): TrendRow[] => {
  const product = filter.product.trim().toLowerCase()
  return rows.filter(
    (row) =>
      (filter.severities.length === 0 || filter.severities.includes(row.severity)) &&
      (filter.kevOnly === false || row.is_kev) &&
      (filter.fixOnly === false || hasFixInfo(row)) &&
      (product === '' || productName(row).toLowerCase().includes(product)) &&
      (filter.date === null || publishedDate(row) === filter.date),
  )
}

/** 최신순은 공개 시각 내림차순(없으면 파일 순서 = 배치가 쓴 공개 시각 내림차순), 점수순은 점수 내림차순 */
export const sortNew = (rows: TrendRow[], sort: NewSort): TrendRow[] => {
  if (sort === 'score') return [...rows].sort((a, b) => b.score - a.score)
  return [...rows].sort((a, b) => {
    const left = a.published_at ?? null
    const right = b.published_at ?? null
    if (left === null || right === null) return 0
    return Date.parse(right) - Date.parse(left)
  })
}

export interface DateOption {
  date: IsoDate
  count: number
}

/** 날짜 필터 선택지(최근 날부터). published_at 이 하나도 없으면 빈 배열 → 날짜 필터를 숨긴다 */
export const dateOptions = (rows: TrendRow[]): DateOption[] => {
  const counts = new Map<IsoDate, number>()
  rows.forEach((row) => {
    const date = publishedDate(row)
    if (date !== null) counts.set(date, (counts.get(date) ?? 0) + 1)
  })
  return [...counts.entries()].map(([date, count]) => ({ date, count })).sort((a, b) => b.date.localeCompare(a.date))
}

/** 제품 입력 도움말(건수 많은 순, 같으면 이름순) */
export const productSuggestions = (rows: TrendRow[], limit: number): string[] => {
  const counts = new Map<string, number>()
  rows.forEach((row) => counts.set(productName(row), (counts.get(productName(row)) ?? 0) + 1))
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], 'en'))
    .slice(0, limit)
    .map(([name]) => name)
}
