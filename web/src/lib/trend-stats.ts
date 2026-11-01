// "늘고 있나" 추이 (design/v6 a-top · CONSENSUS 5절 · CHART-REVIEW T1~T3).
// 축은 선형, 위를 자른다(상한 = 평소 × 2.2 올림). 평소 = 완결일 중앙값, 비교 = 최근 7일 중앙값
import type { IsoDateTime } from '../types/common.ts'
import type { DailyCount, SeverityDistribution } from '../types/stats.ts'
import { formatMonthDay } from './charts.ts'
import { completeDays, lastDayIsPartial } from './daily-window.ts'
import { formatCount, median } from './kpi.ts'
import { niceCeil } from './sparkline.ts'

export type TrendKey = 'new' | 'critical'
export type Verdict = 'more' | 'similar' | 'less'

const MORE = 1.15
const LESS = 0.85
const CAP_FACTOR = 2.2
const SPIKE_FACTOR = 2
const WEEK = 7

/** ±15% 를 넘으면 많음·적음 */
export const verdictOf = (ratio: number): Verdict => {
  if (ratio >= MORE) return 'more'
  if (ratio <= LESS) return 'less'
  return 'similar'
}

/** `+12%` / `−18%` / `같음` */
export const formatChange = (ratio: number): string => {
  const percent = Math.round((ratio - 1) * 100)
  if (percent > 0) return `+${percent}%`
  if (percent < 0) return `−${-percent}%`
  return '같음'
}

export interface TrendBar {
  date: string
  value: number
  /** 막대 높이 % (상한에서 자름) */
  height: number
  /** 상한을 넘어 위를 잘라 그린 막대 */
  cut: boolean
  /** 집계 중인 오늘 */
  partial: boolean
  weekend: boolean
}

export interface TrendPanel {
  key: TrendKey
  bars: TrendBar[]
  /** 평소 = 완결일 하루 중앙값 */
  usual: number
  /** 최근 7일(완결) 하루 중앙값 */
  recent: number
  ratio: number
  verdict: Verdict
  /** 세로 눈금 상한 */
  cap: number
  /** 평소 가로선 높이 % */
  usualHeight: number
}

const isWeekend = (date: string): boolean => {
  const day = new Date(`${date}T00:00:00Z`).getUTCDay()
  return day === 0 || day === 6
}

export const trendPanel = (daily: DailyCount[], key: TrendKey, updatedAt: IsoDateTime): TrendPanel => {
  const complete = completeDays(daily, updatedAt).map((day) => day[key])
  const partial = lastDayIsPartial(daily, updatedAt)
  const usual = median(complete)
  const recent = median(complete.slice(-WEEK))
  const ratio = usual === 0 ? 1 : recent / usual
  const cap = niceCeil(usual * CAP_FACTOR)
  return {
    key,
    bars: daily.map((day, index) => ({
      date: day.date,
      value: day[key],
      height: (Math.min(day[key], cap) / cap) * 100,
      cut: day[key] > cap,
      partial: partial && index === daily.length - 1,
      weekend: isWeekend(day.date),
    })),
    usual,
    recent,
    ratio,
    verdict: verdictOf(ratio),
    cap,
    usualHeight: (usual / cap) * 100,
  }
}

const SHORT: Record<Verdict, string> = { more: '많고', less: '적고', similar: '비슷하고' }
const END: Record<Verdict, string> = { more: '많습니다', less: '적습니다', similar: '비슷합니다' }
const SAME: Record<Verdict, string> = {
  more: '평소보다 늘었습니다',
  less: '평소보다 줄었습니다',
  similar: '평소와 비슷합니다',
}

/** 카드 제목의 답: 두 줄이 같으면 한 문장, 다르면 따로 말한다 (B1) */
export const trendAnswer = (fresh: TrendPanel, critical: TrendPanel): string => {
  if (fresh.verdict === critical.verdict) return SAME[fresh.verdict]
  const freshPart = fresh.verdict === 'similar' ? '평소와 비슷하고' : `평소보다 ${SHORT[fresh.verdict]}`
  return `새 CVE 는 ${freshPart}, Critical 은 ${END[critical.verdict]}`
}

/** 마지막 완결일이 평소의 2배 이상이면 한 줄로 알린다 */
export const spikeNote = (daily: DailyCount[], updatedAt: IsoDateTime): string | null => {
  const complete = completeDays(daily, updatedAt)
  const last = complete.at(-1)
  const usual = median(complete.map((day) => day.new))
  if (last === undefined || usual === 0 || last.new < usual * SPIKE_FACTOR) return null
  return `다만 ${formatMonthDay(last.date)}은 평소의 ${(last.new / usual).toFixed(1)}배(${formatCount(last.new)}건)로 급증했습니다.`
}

/** x축 글자: 마지막 날부터 하루 건너 하나, 달이 바뀌면 "11/1", 집계 중인 오늘은 "오늘". 비우는 칸은 `null` */
export const axisLabels = (daily: DailyCount[], updatedAt: IsoDateTime): (string | null)[] => {
  const partial = lastDayIsPartial(daily, updatedAt)
  const last = daily.length - 1
  return daily.map((day, index) => {
    if (index % 2 !== last % 2) return null
    if (index === last && partial) return '오늘'
    const previous = daily[index - 2]
    const monthTurned = previous !== undefined && previous.date.slice(5, 7) !== day.date.slice(5, 7)
    return monthTurned ? formatMonthDay(day.date) : String(Number(day.date.slice(8, 10)))
  })
}

export interface SeverityPart {
  key: keyof SeverityDistribution
  label: string
  count: number
}

const SEVERITY_PARTS: { key: keyof SeverityDistribution; label: string }[] = [
  { key: 'critical', label: 'Critical' },
  { key: 'high', label: 'High' },
  { key: 'medium', label: 'Medium' },
  { key: 'low', label: 'Low' },
  { key: 'unrated', label: '등급 없음' },
]

/** 심각도 한 줄 막대 조각. 0건은 뺀다. 미평가는 "등급 없음" */
export const severityParts = (distribution: SeverityDistribution): SeverityPart[] =>
  SEVERITY_PARTS.map(({ key, label }) => ({ key, label, count: distribution[key] })).filter((part) => part.count > 0)
