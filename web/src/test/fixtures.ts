// 테스트용 최소 데이터. 계약 타입을 그대로 써서 필드가 바뀌면 컴파일에서 잡힌다
import type { CveDetail } from '../types/cve.ts'
import type { Meta } from '../types/meta.ts'
import type { Stats } from '../types/stats.ts'

export const metaWithVersion = (version: string): Meta => ({
  updated_at: '2026-10-30T09:00:00+09:00',
  version,
  today: { new: 142, kev_new: 3, critical: 11, high_risk: 318 },
  yesterday: { new: 133, kev_new: 1, critical: 7, high_risk: 313 },
})

export const stats: Stats = {
  daily: [{ date: '2026-10-30', new: 142, critical: 11 }],
  severity_7d: { critical: 54, high: 231, medium: 302, low: 41, unrated: 236 },
}

export const cveDetail: CveDetail = {
  id: 'CVE-2026-41203',
  title: 'V8 엔진 타입 혼동으로 원격 코드가 실행됩니다.',
  score: 98,
  score_breakdown: { kev: 50, ransomware: 0, epss: 27.3, news: 0, poc: 0, cvss: 8.8, decay: -2.1 },
  why_now: 'CISA 악용 목록 등재',
  is_kev: true,
  kev_ransomware: false,
  kev_due_date: '2026-11-13',
  poc_count: 0,
  epss: 0.91,
  epss_percentile: 0.997,
  cvss: 8.8,
  cvss_version: '3.1',
  severity: 'HIGH',
  primary_product: 'Chrome',
  summary_what: 'V8 엔진 타입 혼동으로 원격 코드가 실행됩니다.',
  description: 'Type confusion in V8 in Google Chrome.',
  published_at: '2026-10-28T09:00:00+09:00',
  last_modified_at: '2026-10-28T09:00:00+09:00',
  kev_added_at: '2026-10-29',
  summary: null,
  affected: [],
  references: [],
}
