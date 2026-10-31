// 차트 데이터 모양 만들기 (기획서 8장 차트 표, S1·P14)
import type { DailyCount, SeverityDistribution } from '../types/stats.ts'

export interface DailyBar {
  date: string
  /** x축 라벨 `10/30` (S1) */
  label: string
  total: number
  critical: number
  /** 막대 아래칸: Critical 이 아닌 신규. 위칸 Critical 과 합치면 total */
  nonCritical: number
  isLatest: boolean
}

export type SeverityKey = keyof SeverityDistribution

export interface SeverityShare {
  key: SeverityKey
  label: string
  count: number
  /** 전체 대비 정수 % */
  percent: number
}

/** 도넛·범례 순서와 이름. 색은 심각도 5색 고정 (기획서 8장 색상 규칙) */
export const SEVERITY_ORDER: { key: SeverityKey; label: string }[] = [
  { key: 'critical', label: 'Critical' },
  { key: 'high', label: 'High' },
  { key: 'medium', label: 'Medium' },
  { key: 'low', label: 'Low' },
  { key: 'unrated', label: '미평가' },
]

/** `2026-10-30` → `10/30`, `2026-11-03` → `11/3` */
export const formatMonthDay = (isoDate: string): string => {
  const [, month, day] = isoDate.split('-')
  return `${Number(month)}/${Number(day)}`
}

export const toDailyBars = (daily: DailyCount[]): DailyBar[] =>
  daily.map((day, index) => ({
    date: day.date,
    label: formatMonthDay(day.date),
    total: day.new,
    critical: day.critical,
    nonCritical: day.new - day.critical,
    isLatest: index === daily.length - 1,
  }))

export const toSeverityShares = (distribution: SeverityDistribution): { total: number; shares: SeverityShare[] } => {
  const total = SEVERITY_ORDER.reduce((sum, { key }) => sum + distribution[key], 0)
  const shares = SEVERITY_ORDER.map(({ key, label }) => ({
    key,
    label,
    count: distribution[key],
    percent: total === 0 ? 0 : Math.round((distribution[key] / total) * 100),
  }))
  return { total, shares }
}
