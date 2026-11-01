// "어느 쪽이 더 위험한가" 4칸 건수표 (사용자 피드백 2026-11-01: 산점도가 보기 어려움 → 4칸표).
// 칸 경계는 산점도와 같은 EPSS 30%·CVSS 7.0. 칸은 사실만 말하고, 빨강은 ◆ 기한 임박 전용
import type { IsoDate } from '../types/common.ts'
import type { TrendRow } from '../types/trend.ts'
import { dueInDays, isUrgent } from './urgency.ts'

export const EPSS_SPLIT = 0.3
export const CVSS_SPLIT = 7

export type Quadrant = 'hot' | 'severe' | 'likely' | 'low'

/** 칸 이름 (요약 줄·화면 읽기) */
export const QUADRANT_NAME: Record<Quadrant, string> = {
  hot: '악용 가능성 높음·심각',
  severe: '심각·악용 가능성 낮음',
  likely: '악용 가능성 높음·덜 심각',
  low: '둘 다 낮음',
}

/** 2×2 순서: 윗줄 심각(낮음 | 높음), 아랫줄 덜 심각(낮음 | 높음) */
export const QUADRANT_ORDER: Quadrant[] = ['severe', 'hot', 'low', 'likely']

export const REPRESENTATIVES = 3

export const quadrantOf = (row: TrendRow): Quadrant | null => {
  if (row.epss === null || row.cvss === null) return null
  const likely = row.epss >= EPSS_SPLIT
  if (row.cvss >= CVSS_SPLIT) return likely ? 'hot' : 'severe'
  return likely ? 'likely' : 'low'
}

export interface QuadrantCell {
  key: Quadrant
  count: number
  /** 대표 3건: ◆ 기한 임박(남은 날 순) 먼저, 그다음 점수순 */
  representatives: TrendRow[]
  /** 대표 밖 건수 */
  more: number
}

export interface QuadrantGrid {
  cells: Record<Quadrant, QuadrantCell>
  /** EPSS·CVSS 가 없어 칸에 못 넣은 건수 */
  unscored: number
}

const byUrgencyThenScore = (today: IsoDate) => (a: TrendRow, b: TrendRow) => {
  const aUrgent = isUrgent(a, today)
  const bUrgent = isUrgent(b, today)
  if (aUrgent !== bUrgent) return aUrgent ? -1 : 1
  if (aUrgent) return (dueInDays(a, today) ?? 0) - (dueInDays(b, today) ?? 0) || b.score - a.score
  return b.score - a.score
}

export const quadrantGrid = (rows: TrendRow[], today: IsoDate): QuadrantGrid => {
  const cellOf = (key: Quadrant): QuadrantCell => {
    const members = rows.filter((row) => quadrantOf(row) === key).sort(byUrgencyThenScore(today))
    return {
      key,
      count: members.length,
      representatives: members.slice(0, REPRESENTATIVES),
      more: Math.max(0, members.length - REPRESENTATIVES),
    }
  }
  return {
    cells: { hot: cellOf('hot'), severe: cellOf('severe'), likely: cellOf('likely'), low: cellOf('low') },
    unscored: rows.filter((row) => quadrantOf(row) === null).length,
  }
}

/** "97229" — CVE 번호 끝자리 */
export const shortCveId = (id: string): string => id.replace(/^CVE-\d{4}-/, '')
