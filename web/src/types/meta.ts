// meta.json — 기획서 7장, WBS 4.2 (D11, P16). 항상 `cache: 'no-cache'` 로 읽는다
import type { IsoDateTime } from './common.ts'

/** KPI 4개. 모두 24시간 롤링 창(기획서 8장 KPI 표) */
export interface KpiCounts {
  /** 24시간 내 `published_at` 신규 CVE 수 */
  new: number
  /** 24시간 내 `kev_added_at` KEV 등재 수 */
  kev_new: number
  /** 24시간 신규 중 CVSS 9.0 이상 */
  critical: number
  /** 전체 중 EPSS 0.5 이상 (스냅샷) */
  high_risk: number
}

export interface Meta {
  /** 배치가 JSON 을 쓴 시각. 36시간을 넘으면 "데이터가 오래됐습니다" 배너 */
  updated_at: IsoDateTime
  /** 캐시 버스팅 값. 나머지 파일을 `?v=<version>` 으로 읽는다 */
  version: string
  /** 지금 기준 24시간 창 */
  today: KpiCounts
  /** 48~24시간 전 창 (어제 대비 증감용) */
  yesterday: KpiCounts
}
