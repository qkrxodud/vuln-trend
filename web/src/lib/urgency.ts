// v6 강조 규칙 (design/v6/CONSENSUS.md 1·2절): 빨강 = CISA 조치 기한이 기준일부터 0~3일 안에 끝남 하나.
// 기준일은 브라우저 날짜가 아니라 meta.updated_at 의 KST 날짜다 (데이터가 하루 한 번 바뀌므로)
import type { IsoDate, IsoDateTime } from '../types/common.ts'
import type { Meta } from '../types/meta.ts'
import type { TrendRow } from '../types/trend.ts'
import { formatKstDate } from './kst-time.ts'

/** 빨강 범위 D-0~D-n. 사용자 결정(CONSENSUS 6절 5번)으로 D-0 만으로 좁히면 이 값만 바꾼다 */
export const URGENT_WITHIN_DAYS = 3
/** "새 KEV" = 등재 0~n일 전 */
export const NEW_KEV_WITHIN_DAYS = 7
export const UNKNOWN_PRODUCT = '제품 미상'

const DAY_MS = 86_400_000

export const referenceDate = (updatedAt: IsoDateTime): IsoDate => formatKstDate(updatedAt)

/** to − from (날). 날짜 문자열만 보므로 시간대와 상관없다 */
export const daysBetween = (from: IsoDate, to: IsoDate): number =>
  Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / DAY_MS)

/** CISA 조치 기한까지 남은 날. 지났으면 음수, KEV 가 아니거나 기한이 없으면 `null` */
export const dueInDays = (row: TrendRow, today: IsoDate): number | null => {
  if (row.is_kev === false || row.kev_due_date === null) return null
  return daysBetween(today, row.kev_due_date)
}

export const isUrgent = (row: TrendRow, today: IsoDate): boolean => {
  const due = dueInDays(row, today)
  return due !== null && due >= 0 && due <= URGENT_WITHIN_DAYS
}

/** 기한이 가까운 순, 같으면 점수 높은 순 */
export const urgentRows = (rows: TrendRow[], today: IsoDate): TrendRow[] =>
  rows
    .filter((row) => isUrgent(row, today))
    .sort((a, b) => (dueInDays(a, today) ?? 0) - (dueInDays(b, today) ?? 0) || b.score - a.score)

export const overdueCount = (rows: TrendRow[], today: IsoDate): number =>
  rows.filter((row) => {
    const due = dueInDays(row, today)
    return due !== null && due < 0
  }).length

/** 임박 범위 밖에서 가장 가까운 기한 */
export const nextDueRow = (rows: TrendRow[], today: IsoDate): TrendRow | null => {
  const later = rows
    .filter((row) => (dueInDays(row, today) ?? -1) > URGENT_WITHIN_DAYS)
    .sort((a, b) => (dueInDays(a, today) ?? 0) - (dueInDays(b, today) ?? 0))
  return later[0] ?? null
}

/** 등재 며칠 전인지. 등재일을 모르면(배치가 아직 안 내보냄 포함) `null` */
export const kevAddedDaysAgo = (row: TrendRow, today: IsoDate): number | null => {
  const addedAt = row.kev_added_at ?? null
  if (addedAt === null) return null
  return daysBetween(addedAt, today)
}

export const productName = (row: TrendRow): string => row.primary_product ?? UNKNOWN_PRODUCT

export interface KevAddedCounts {
  recent: number
  previous: number
  /** all = meta 의 전체 KEV 집계, watchlist = 주목 목록 행으로 센 값 */
  scope: 'all' | 'watchlist'
}

const WEEK = 7

/** 최근 7일(기준일 포함) / 그 전 7일 KEV 등재 수. 배치 집계가 먼저, 없으면 주목 목록 행으로 센다 */
export const kevAddedCounts = (meta: Meta, rows: TrendRow[], today: IsoDate): KevAddedCounts | null => {
  const recent = meta.kev_added_7d ?? null
  const previous = meta.kev_added_prev_7d ?? null
  if (recent !== null && previous !== null) return { recent, previous, scope: 'all' }

  const ages = rows.map((row) => kevAddedDaysAgo(row, today)).filter((age) => age !== null)
  if (ages.length === 0) return null
  return {
    recent: ages.filter((age) => age >= 0 && age < WEEK).length,
    previous: ages.filter((age) => age >= WEEK && age < WEEK * 2).length,
    scope: 'watchlist',
  }
}
