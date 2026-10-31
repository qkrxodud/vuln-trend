// search-index.json — 기획서 7장 (P19). 검색창 포커스 시 lazy load, 1년치 3~4MB

/** 검색 한 건. id·제목·제품명으로 클라이언트에서 거른다 */
export interface SearchEntry {
  id: string
  title: string
  /** primary_product. 없으면 `null` */
  product: string | null
}

/** search-index.json: 발행일 내림차순 전체 CVE */
export type SearchIndex = SearchEntry[]
