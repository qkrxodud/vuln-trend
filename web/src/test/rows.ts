// 목록 테스트용 행 만들기. 계약 타입 그대로라 필드가 바뀌면 컴파일에서 잡힌다
import type { TrendRow } from '../types/trend.ts'

let sequence = 0

export const trendRow = (overrides: Partial<TrendRow> = {}): TrendRow => {
  sequence += 1
  return {
    id: `CVE-2026-${String(10000 + sequence)}`,
    title: 'Type confusion in V8 allows remote code execution',
    score: 50,
    score_breakdown: { kev: 0, ransomware: 0, epss: 10, news: 0, poc: 0, cvss: 8.8, decay: -1 },
    why_now: 'CVSS 8.8, 최근 공개',
    is_kev: false,
    kev_ransomware: false,
    kev_due_date: null,
    poc_count: 0,
    epss: 0.12,
    epss_percentile: 0.95,
    cvss: 8.8,
    cvss_version: '3.1',
    severity: 'HIGH',
    primary_product: 'Chrome',
    summary_what: null,
    ...overrides,
  }
}

/** 점수 내림차순 n 건 */
export const rankedRows = (count: number, overrides: Partial<TrendRow> = {}): TrendRow[] =>
  Array.from({ length: count }, (_, index) => trendRow({ score: 100 - index, ...overrides }))
