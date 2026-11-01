import { mineRows, newSince } from '../lib/mine.ts'
import { useRecentIf, useTrendIndex } from './use-data.ts'
import { useMineOptional } from './use-mine.ts'

/** 마지막 /mine 방문 뒤 새로 생긴 내 제품 취약점 수. 내 제품이 없으면 `null`, 아직 세는 중이면 `undefined` */
export const useMineNews = (): number | null | undefined => {
  const mine = useMineOptional()
  const products = mine?.mine.products ?? []
  const index = useTrendIndex()
  const recent = useRecentIf(products.length > 0)
  if (mine === null || products.length === 0) return null
  if (index.status !== 'ready' || recent.status !== 'ready') return undefined
  return newSince(mineRows([index.data, recent.data], products), mine.mine.seenAt)
}
