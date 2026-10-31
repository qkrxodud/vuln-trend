// 검색 (WBS 5.9, P19). search-index.json(1년치, 발행일 내림차순)을 브라우저에서 거른다
import type { SearchEntry } from '../types/search.ts'

export interface SearchResult {
  /** 보여 줄 결과 (limit 건까지) */
  matches: SearchEntry[]
  /** 맞는 전체 건수 */
  total: number
}

export const SEARCH_LIMIT = 10

const CVE_ID_PATTERN = /^CVE-\d{4}-\d{4,}$/i

// 낮을수록 앞: 번호 정확히 → 번호 일부 → 제품명 → 제목
const rankOf = (entry: SearchEntry, query: string): number | null => {
  const id = entry.id.toLowerCase()
  if (id === query) return 0
  if (id.includes(query)) return 1
  if (entry.product?.toLowerCase().includes(query) === true) return 2
  if (entry.title.toLowerCase().includes(query)) return 3
  return null
}

export const searchEntries = (index: SearchEntry[], rawQuery: string, limit = SEARCH_LIMIT): SearchResult => {
  const query = rawQuery.trim().toLowerCase()
  if (query === '') return { matches: [], total: 0 }

  const ranked = index
    .map((entry) => ({ entry, rank: rankOf(entry, query) }))
    .filter((item): item is { entry: SearchEntry; rank: number } => item.rank !== null)
    .sort((a, b) => a.rank - b.rank)
  return { matches: ranked.slice(0, limit).map((item) => item.entry), total: ranked.length }
}

/** 검색어가 완전한 CVE 번호면 대문자로 맞춰 돌려준다. 아니면 `null` */
export const findDirectCveId = (rawQuery: string): string | null => {
  const query = rawQuery.trim()
  return CVE_ID_PATTERN.test(query) ? query.toUpperCase() : null
}
