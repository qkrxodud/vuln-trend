// v6 상세 (패널·/cve 페이지 공통, design/v6 c-list renderDetail · CONSENSUS 4절 상세).
// 데이터에 없는 것(조치 절차·영향 자산)은 만들지 않고, 필드마다 빈 상태 문구를 따로 둔다
import type { IsoDate } from '../types/common.ts'
import type { AffectedProduct, CveDetail, ReferenceLink, ReferenceSource } from '../types/cve.ts'
import type { ScoreBreakdown } from '../types/trend.ts'
import { nvdUrl } from './cve-detail.ts'
import { dueInDays, kevAddedDaysAgo, URGENT_WITHIN_DAYS } from './urgency.ts'

/** CISA 조치 기한 칸 */
export type DueFact = { kind: 'not-kev' } | { kind: 'overdue' | 'urgent' | 'later'; days: number; date: IsoDate }

/** CISA KEV 등재 칸 */
export type AddedFact = { kind: 'not-kev' } | { kind: 'unknown' } | { kind: 'known'; daysAgo: number; date: IsoDate }

export const dueFact = (detail: CveDetail, today: IsoDate): DueFact => {
  const days = dueInDays(detail, today)
  if (days === null || detail.kev_due_date === null) return { kind: 'not-kev' }
  if (days < 0) return { kind: 'overdue', days: -days, date: detail.kev_due_date }
  return { kind: days <= URGENT_WITHIN_DAYS ? 'urgent' : 'later', days, date: detail.kev_due_date }
}

export const addedFact = (detail: CveDetail, today: IsoDate): AddedFact => {
  if (detail.is_kev === false) return { kind: 'not-kev' }
  const daysAgo = kevAddedDaysAgo(detail, today)
  if (daysAgo === null || detail.kev_added_at === null) return { kind: 'unknown' }
  return { kind: 'known', daysAgo, date: detail.kev_added_at }
}

/** 원문 설명이 이보다 길면 4줄로 접고 "원문 전체 보기" */
export const DESCRIPTION_CLAMP = 280

/** 영향 제품 이름: 표시 이름, 없으면 "vendor / product" */
export const affectedName = (item: AffectedProduct): string => item.display_name ?? `${item.vendor} / ${item.product}`

export interface ReferenceRow {
  url: string
  /** 출처 글자: 제조사 / NVD / 뉴스 / PoC */
  source: string
  /** 제목, 없으면 도메인 */
  text: string
}

/** 제조사 공지를 먼저, NVD 원문은 없으면 넣는다 */
const SOURCE_LABEL: Record<ReferenceSource, string> = { vendor: '제조사', nvd: 'NVD', news: '뉴스', poc: 'PoC' }

const hostOf = (url: string): string => new URL(url).hostname.replace(/^www\./, '')

export const referenceRows = (cveId: string, references: ReferenceLink[]): ReferenceRow[] => {
  const hasNvd = references.some((reference) => reference.source === 'nvd')
  const all = hasNvd ? references : [...references, { url: nvdUrl(cveId), source: 'nvd' as const, title: null }]
  const vendorFirst = [...all].sort((a, b) => Number(b.source === 'vendor') - Number(a.source === 'vendor'))
  return vendorFirst.map((reference) => ({
    url: reference.url,
    source: SOURCE_LABEL[reference.source],
    text: reference.title ?? hostOf(reference.url),
  }))
}

export interface ScoreRow {
  key: keyof ScoreBreakdown
  label: string
  value: number
}

const SCORE_PARTS: { key: keyof ScoreBreakdown; label: string }[] = [
  { key: 'kev', label: 'KEV 등재' },
  { key: 'ransomware', label: '랜섬웨어 악용' },
  { key: 'epss', label: 'EPSS (악용 가능성 × 30)' },
  { key: 'news', label: '뉴스 언급' },
  { key: 'poc', label: 'PoC' },
  { key: 'cvss', label: 'CVSS (그대로)' },
  { key: 'decay', label: '시간 감쇠 (30일마다 절반)' },
]

export const scoreRows = (breakdown: ScoreBreakdown): ScoreRow[] =>
  SCORE_PARTS.map(({ key, label }) => ({ key, label, value: breakdown[key] }))

/** 발산 막대 축 −L…0…+100. L = 감쇠를 10 단위로 올림, 최소 20 */
export interface ScoreAxis {
  left: number
  right: number
  /** 값 → 가로 위치 % */
  x: (value: number) => number
  ticks: number[]
}

const SCORE_RIGHT = 100
const MIN_LEFT = 20

// 오른쪽은 점수 척도 0~100 고정(CVE 끼리 비교), 왼쪽은 감쇠에 맞춘다 (CHART-REVIEW D2)
export const scoreAxis = (decay: number): ScoreAxis => {
  const left = Math.max(MIN_LEFT, Math.ceil(-Math.min(0, decay) / 10) * 10)
  const span = left + SCORE_RIGHT
  return {
    left,
    right: SCORE_RIGHT,
    x: (value: number) => ((value + left) / span) * 100,
    // 왼쪽이 좁으면(−20·−30) 음수 눈금 글자가 0 과 겹치므로 생략한다. 값은 점수 칸에 있다
    ticks: left >= 40 ? [-left, 0, 50, SCORE_RIGHT] : [0, 50, SCORE_RIGHT],
  }
}

/** 막대 한 칸의 위치·폭 %. 0 이면 `null` */
export const scoreSegment = (value: number, axis: ScoreAxis): { left: number; width: number } | null => {
  const span = axis.left + axis.right
  if (value > 0) return { left: axis.x(0), width: (Math.min(value, axis.right) / span) * 100 }
  if (value < 0) return { left: axis.x(Math.max(value, -axis.left)), width: (Math.min(-value, axis.left) / span) * 100 }
  return null
}

/** `+50.0` / `−5.2` / `0.0` */
export const formatScorePart = (value: number): string => {
  if (value > 0) return `+${value.toFixed(1)}`
  if (value < 0) return `−${(-value).toFixed(1)}`
  return '0.0'
}
