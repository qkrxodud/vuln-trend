// "내 제품" (사용자 결정 2026-11-01): 사용자가 고른 제품의 취약점만 모아 보기. 서버·로그인 없이 이 브라우저(localStorage)에만 저장
import type { IsoDateTime } from '../types/common.ts'
import type { SearchIndex } from '../types/search.ts'
import type { TrendRow } from '../types/trend.ts'

export const MINE_STORAGE_KEY = 'vuln-trend:mine'

export interface MineState {
  /** 고른 제품 이름(productName 과 같은 글자) */
  products: string[]
  /** 마지막으로 /mine 을 본 때의 meta.updated_at. 처음이면 `null` */
  seenAt: IsoDateTime | null
}

export const EMPTY_MINE: MineState = { products: [], seenAt: null }

const isRecord = (value: unknown): value is Record<string, unknown> => typeof value === 'object' && value !== null

/** 저장된 글을 읽는다. 깨졌거나 모양이 다르면 빈 상태 */
export const parseMine = (raw: string | null): MineState => {
  if (raw === null) return EMPTY_MINE
  try {
    const value: unknown = JSON.parse(raw)
    if (isRecord(value) === false || Array.isArray(value.products) === false) return EMPTY_MINE
    const products = value.products.filter((item): item is string => typeof item === 'string' && item !== '')
    const seenAt = typeof value.seenAt === 'string' ? value.seenAt : null
    return { products, seenAt }
  } catch {
    return EMPTY_MINE
  }
}

/**
 * 제품을 더하거나 뺀다. 본 때가 없을 때 처음 고르면 그때(`now` = meta.updated_at)를 본 때로 잡아
 * 처음에는 "새 0건"에서 시작한다. 그 뒤에 더 고르거나 빼도 본 때는 바꾸지 않는다 (사용자 결정 2026-11-02)
 */
export const toggleProduct = (state: MineState, product: string, now: IsoDateTime | null): MineState => {
  const has = state.products.includes(product)
  const products = has ? state.products.filter((item) => item !== product) : [...state.products, product]
  const seenAt = has ? state.seenAt : (state.seenAt ?? now)
  return { products: products.sort((a, b) => a.localeCompare(b, 'en')), seenAt }
}

export const isMine = (state: MineState, product: string | null): boolean =>
  product !== null && state.products.includes(product)

/** index ∪ recent 에서 내 제품 행(같은 번호는 한 번), 점수 정보가 있는 것 */
export const mineRows = (lists: TrendRow[][], products: string[]): TrendRow[] => {
  if (products.length === 0) return []
  const seen = new Set<string>()
  return lists.flat().filter((row) => {
    if (row.primary_product === null || products.includes(row.primary_product) === false || seen.has(row.id))
      return false
    seen.add(row.id)
    return true
  })
}

/** 마지막 방문 뒤 공개된 내 제품 행 수. 처음이면 7일 전부를 새것으로 본다 */
export const newSince = (rows: TrendRow[], seenAt: IsoDateTime | null): number => {
  if (seenAt === null) return rows.length
  const since = Date.parse(seenAt)
  return rows.filter((row) => {
    const published = row.published_at ?? null
    return published !== null && Date.parse(published) > since
  }).length
}

export interface OlderEntry {
  id: string
  title: string
  product: string
}

/** 검색 색인(지난 12개월)에서 내 제품이지만 index·recent 에 없는 것 — 점수·상세 없이 NVD 로 */
export const olderEntries = (search: SearchIndex, products: string[], known: Set<string>): OlderEntry[] =>
  search.flatMap((entry) =>
    entry.product !== null && products.includes(entry.product) && known.has(entry.id) === false
      ? [{ id: entry.id, title: entry.title, product: entry.product }]
      : [],
  )

/** 제품 고르기 후보: 검색 색인의 제품 이름(건수 많은 순, 같으면 이름순) */
export const productCandidates = (search: SearchIndex): { name: string; count: number }[] => {
  const counts = new Map<string, number>()
  search.forEach((entry) => {
    if (entry.product !== null) counts.set(entry.product, (counts.get(entry.product) ?? 0) + 1)
  })
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name, 'en'))
}
