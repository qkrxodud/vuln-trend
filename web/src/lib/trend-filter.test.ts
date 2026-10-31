import { describe, expect, it } from 'vitest'
import { cveDetail } from '../test/fixtures.ts'
import type { TrendRow } from '../types/trend.ts'
import { matchesTrendFilter } from './trend-filter.ts'

const row = (overrides: Partial<TrendRow>): TrendRow => ({ ...cveDetail, ...overrides })

describe('트렌드 목록 필터', () => {
  it('"전체"는 모든 취약점을 보인다', () => {
    expect(matchesTrendFilter(row({ is_kev: false, severity: 'LOW' }), 'all')).toBe(true)
  })

  it('"KEV"는 실제 악용이 확인된 취약점만 보인다', () => {
    expect(matchesTrendFilter(row({ is_kev: true }), 'kev')).toBe(true)
    expect(matchesTrendFilter(row({ is_kev: false }), 'kev')).toBe(false)
  })

  it('"Critical"은 CVSS 9.0 이상 취약점만 보인다', () => {
    expect(matchesTrendFilter(row({ severity: 'CRITICAL' }), 'critical')).toBe(true)
    expect(matchesTrendFilter(row({ severity: 'HIGH' }), 'critical')).toBe(false)
  })
})
