// categories.json — 공격 유형·제품군 분류별 건수와 트렌드 점수 상위 (사용자 결정 2026-11-01, 백엔드 08e39ac)
import type { TrendRow } from './trend.ts'

export interface Category {
  /** `rce`, `network` … 배치가 고정 순서로 모든 키를 싣는다(0건 포함) */
  key: string
  /** 화면에 그대로 쓰는 이름. 화면은 키→이름 표를 따로 두지 않는다 */
  label: string
  /** 오늘 포함 최근 7개 한국 달력일 공개 수 (recent.json 과 같은 창) */
  count_7d: number
  /** 최근 30개 한국 달력일 공개 수 */
  count_30d: number
  /** 그 분류의 KEV 전체(누적) 수 */
  kev_count: number
  /** 그 분류 안 트렌드 점수 순 최대 20건. 모두 상세 파일이 있다 */
  top: TrendRow[]
}

export interface Categories {
  attack_types: Category[]
  product_families: Category[]
}
