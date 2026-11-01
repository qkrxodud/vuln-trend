// cve/CVE-*.json — 상세 1건. 기획서 7장·8장 상세 패널/상세 페이지, WBS 4.2 (P6)
import type { IsoDate, IsoDateTime } from './common.ts'
import type { TrendRow } from './trend.ts'

/** 영향 제품 한 줄. affected_product 테이블 한 행 */
export interface AffectedProduct {
  vendor: string
  product: string
  /** product_alias 표시 이름. 매핑이 없으면 `null` (화면은 product 원문) */
  display_name: string | null
  /** product_alias slug. 매핑이 없으면 `null` */
  slug: string | null
  /** OSV 생태계. MVP(NVD CPE 만)에서는 `null` */
  ecosystem: string | null
  /** 사람이 읽는 범위. 예: `>= 120.0, < 130.0.6723.58` */
  vulnerable_range: string | null
  /** OSV 수집 뒤에 채움. MVP 에서는 `null` */
  fixed_version: string | null
  /** 이 제품의 수정 버전들(가지마다 하나). 조치 피드백 계약(2026-11-01)에서 더한 필드, 배치가 내보내기 전에는 없음 */
  fix_versions?: string[]
}

export type ReferenceSource = 'nvd' | 'vendor' | 'news' | 'poc'

/** 참고 링크. reference_link 테이블 한 행 */
export interface ReferenceLink {
  url: string
  source: ReferenceSource
  /** 뉴스 제목 등. 없으면 `null` */
  title: string | null
  /** NVD 참고 링크 태그(Patch·Vendor Advisory·Mitigation·Release Notes·Broken Link 등). 배치가 내보내기 전에는 없음 */
  tags?: string[]
}

/** 한국어 요약. summary 테이블 한 행. AI 생성 면책을 함께 보인다 */
export interface Summary {
  what: string
  who: string
  /** "X 이상으로 업데이트" 수준까지만. 명령어 없음 */
  action: string
  model: string
  generated_at: IsoDateTime
}

/** 상세 1건 = 목록 행 필드 전부 + 상세 전용 필드 */
export interface CveDetail extends TrendRow {
  /** NVD 영어 원문 설명 */
  description: string
  published_at: IsoDateTime
  last_modified_at: IsoDateTime
  kev_added_at: IsoDate | null
  /** CISA KEV requiredAction 원문(영문). KEV 가 아니면 `null`, 배치가 내보내기 전에는 없음 */
  kev_required_action?: string | null
  /** 요약이 없으면 `null` */
  summary: Summary | null
  affected: AffectedProduct[]
  /** 벤더 어드바이저리 먼저, 그 다음 nvd·news·poc */
  references: ReferenceLink[]
}
