// KPI 카드 숫자 표기 (기획서 8장 KPI 표, D11·D12)
import type { Meta } from '../types/meta.ts'
import type { DailyCount } from '../types/stats.ts'
import { completeDays, lastDayIsPartial } from './daily-window.ts'
import { formatKstTime } from './kst-time.ts'
const COUNT_FORMAT = new Intl.NumberFormat('ko-KR')

export const formatCount = (value: number): string => COUNT_FORMAT.format(value)

/** 가운데 값. 짝수 개면 가운데 둘의 평균, 비어 있으면 0 */
export const median = (values: number[]): number => {
  if (values.length === 0) return 0
  const sorted = [...values].sort((a, b) => a - b)
  const middle = Math.floor(sorted.length / 2)
  return sorted.length % 2 === 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2
}

/** "오늘 새로 나온 CVE" 카드 (design/v6 a-top) */
export interface TodayNewSummary {
  /** meta.today.new (24시간 롤링) */
  today: number
  /** stats.daily 의 마지막 날이 갱신일이면 집계 중 */
  partial: boolean
  /** 갱신 시각 `18:49` (KST) */
  asOf: string
  yesterday: number
  /** 완결된 날들의 하루 중앙값(반올림). 완결일이 없으면 `null` */
  usual: number | null
  usualDays: number
  /** 스파크라인에 쓸 14일 값 (오늘 포함) */
  series: number[]
}

// 오늘 막대는 갱신 시각까지만 센 값이라 평소(중앙값) 계산에서 뺀다 (design/v6 T2)
export const todayNewSummary = (meta: Meta, daily: DailyCount[]): TodayNewSummary => {
  const partial = lastDayIsPartial(daily, meta.updated_at)
  const complete = completeDays(daily, meta.updated_at)
  return {
    today: meta.today.new,
    partial,
    asOf: formatKstTime(meta.updated_at),
    yesterday: meta.yesterday.new,
    usual: complete.length === 0 ? null : Math.round(median(complete.map((day) => day.new))),
    usualDays: complete.length,
    series: daily.map((day) => day.new),
  }
}
