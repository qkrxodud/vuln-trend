// 공격 유형·제품군 분류 (사용자 결정 2026-11-01). 이름은 categories.json 의 label 을 그대로 쓴다
import type { Categories, Category } from '../types/categories.ts'
import type { TrendRow } from '../types/trend.ts'

/** 주소의 축: /type/<key> = 공격 유형, /family/<key> = 제품군 */
export type CategoryAxis = 'type' | 'family'

/** 필터의 "분류 전" 값 */
export const UNCLASSIFIED = 'unclassified'

export const AXIS_NAME: Record<CategoryAxis, string> = { type: '공격 유형', family: '제품군' }

export const axisCategories = (categories: Categories, axis: CategoryAxis): Category[] =>
  axis === 'type' ? categories.attack_types : categories.product_families

export const findCategory = (categories: Categories, axis: CategoryAxis, key: string): Category | null =>
  axisCategories(categories, axis).find((category) => category.key === key) ?? null

export const categoryPath = (axis: CategoryAxis, key: string): string => `/${axis}/${key}`

/** 칩 순서: 최근 7일 건수 많은 순, 같으면 배치가 준 순서 (적은 분류는 뒤로 — 숨기지 않는다) */
export const chipOrder = (list: Category[]): Category[] => [...list].sort((a, b) => b.count_7d - a.count_7d)

/** 최근 7일 0건이면 칩을 흐리게 둔다 */
export const isQuiet = (category: Category): boolean => category.count_7d === 0

/** 행의 분류 키들. 비었거나 배치가 내보내기 전이면 [] = 분류 전 */
export const rowKeys = (row: TrendRow, axis: CategoryAxis): string[] => {
  if (axis === 'type') return row.attack_types ?? []
  const family = row.product_family ?? null
  return family === null ? [] : [family]
}

/** key 가 `null` 이면 모두, UNCLASSIFIED 면 분류 전만 */
export const matchesCategory = (row: TrendRow, axis: CategoryAxis, key: string | null): boolean => {
  if (key === null) return true
  const keys = rowKeys(row, axis)
  return key === UNCLASSIFIED ? keys.length === 0 : keys.includes(key)
}

/** 분류마다 행 수. 분류 전은 UNCLASSIFIED 로 센다 */
export const countByCategory = (rows: TrendRow[], axis: CategoryAxis): Map<string, number> => {
  const counts = new Map<string, number>()
  rows.forEach((row) => {
    const keys = rowKeys(row, axis)
    ;(keys.length === 0 ? [UNCLASSIFIED] : keys).forEach((key) => counts.set(key, (counts.get(key) ?? 0) + 1))
  })
  return counts
}
