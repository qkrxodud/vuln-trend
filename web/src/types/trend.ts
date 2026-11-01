// index.json · recent.json 의 행 — 기획서 7장, WBS 4.2 index.json 필드와 1:1
import type { CvssVersion, IsoDate, Severity } from './common.ts'

/**
 * 트렌드 점수 항별 기여도. 소수 1자리(R6).
 * kev~cvss 는 감쇠 전 점수, decay 는 감쇠로 빠진 점수(0 이하).
 * 그래서 kev + ransomware + epss + news + poc + cvss + decay ≈ score (반올림 오차 범위).
 * news·poc 는 2단계 점수식 항이라 MVP 에서는 항상 0.
 */
export interface ScoreBreakdown {
  kev: number
  ransomware: number
  epss: number
  news: number
  poc: number
  cvss: number
  decay: number
}

export interface TrendRow {
  /** `CVE-2026-41203` */
  id: string
  /** 기획서 3장 title 규칙: GHSA summary > 요약 what 첫 줄 > description 첫 문장 80자 */
  title: string
  /** 트렌드 점수, 정수 반올림 */
  score: number
  score_breakdown: ScoreBreakdown
  /** "왜 지금?" 규칙 템플릿 문구 (기획서 6장) */
  why_now: string
  is_kev: boolean
  /** KEV knownRansomwareCampaignUse. 랜섬웨어 배지는 이 값으로 판단. KEV 가 아니면 false */
  kev_ransomware: boolean
  /** KEV `dueDate`. KEV 가 아니면 `null` */
  kev_due_date: IsoDate | null
  /**
   * KEV 등재일(`dateAdded`). KEV 가 아니면 `null`.
   * v6 계약(G1, 2026-11-01)에서 더한 필드라 배치가 내보내기 전 JSON 에는 없다 → 화면은 "등재일 정보 없음"
   */
  kev_added_at?: IsoDate | null
  /** MVP 에서는 항상 0 (PoC 탐지는 2단계) */
  poc_count: number
  /** EPSS 확률 0~1. 아직 점수가 없으면 `null` */
  epss: number | null
  /** EPSS percentile 0~1. "상위 N%" = (1 - percentile) × 100 */
  epss_percentile: number | null
  /** CVSS 기본 점수 0.0~10.0. 미평가면 `null` */
  cvss: number | null
  cvss_version: CvssVersion | null
  severity: Severity
  /** product_alias 로 정규화된 표시 이름. 매핑이 없으면 CPE product 원문, 제품 정보가 아예 없으면 `null` */
  primary_product: string | null
  /** 한국어 요약 what. 아직 없으면 `null` (화면은 title 로 대신 보인다) */
  summary_what: string | null
}

/** index.json: 트렌드 점수 내림차순 상위 100건 */
export type TrendIndex = TrendRow[]

/** recent.json: 최근 7일 신규, 행 모양은 index 와 같음 */
export type RecentList = TrendRow[]
