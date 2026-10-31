// stats.json — 기획서 7장·8장 차트 표, WBS 4.2. daily_stat(KST 달력일) 기준
import type { IsoDate } from './common.ts'

/** 하루치 신규 건수. 막대 차트 한 칸 */
export interface DailyCount {
  /** `published_at` 의 KST 달력일 */
  date: IsoDate
  /** 그 날 발행된 신규 CVE 수 (Critical 포함 전체) */
  new: number
  /** 그 중 CVSS 9.0 이상. 막대에 적층 */
  critical: number
}

/** 최근 7개 달력일 신규의 심각도별 합. 도넛 차트 (P14: 미평가 포함) */
export interface SeverityDistribution {
  critical: number
  high: number
  medium: number
  low: number
  unrated: number
}

export interface Stats {
  /** 최근 14일, 오래된 날 → 최근 날 순서, 정확히 14개(건수 0인 날도 포함) */
  daily: DailyCount[]
  /** 최근 7일 심각도 분포 */
  severity_7d: SeverityDistribution
}
