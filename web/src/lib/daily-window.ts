// 일별 통계 중 "오늘(집계 중)" 판정 하나. KPI 스파크라인·평소와 추이 차트가 같이 쓴다 (D2 지시: 기준을 하나로)
import type { IsoDateTime } from '../types/common.ts'
import type { DailyCount } from '../types/stats.ts'
import { formatKstDate } from './kst-time.ts'

/** 마지막 날이 갱신일(KST)이면 그날은 갱신 시각까지만 센 값이다 */
export const lastDayIsPartial = (daily: DailyCount[], updatedAt: IsoDateTime): boolean =>
  daily.at(-1)?.date === formatKstDate(updatedAt)

/** 집계가 끝난 날들 (집계 중인 오늘을 뺀다) */
export const completeDays = (daily: DailyCount[], updatedAt: IsoDateTime): DailyCount[] =>
  lastDayIsPartial(daily, updatedAt) ? daily.slice(0, -1) : daily
