import { describe, expect, it } from 'vitest'
import type { SearchEntry } from '../types/search.ts'
import { findDirectCveId, searchEntries } from './search.ts'

// search-index.json 은 발행일 내림차순
const index: SearchEntry[] = [
  { id: 'CVE-2026-42301', title: 'Skia 힙 오버플로', product: 'Chrome' },
  { id: 'CVE-2026-41990', title: 'netfilter use-after-free in chrome-like sandbox', product: 'Linux Kernel' },
  { id: 'CVE-2026-41203', title: 'V8 타입 혼동', product: 'Chrome' },
  { id: 'CVE-2026-40117', title: 'SSL-VPN 메모리 쓰기 오류', product: 'FortiOS' },
  { id: 'CVE-2026-39950', title: '스택 버퍼 오버플로', product: null },
]

const ids = (query: string) => searchEntries(index, query).matches.map((entry) => entry.id)

describe('검색', () => {
  it('검색어가 비어 있으면 아무것도 찾지 않는다', () => {
    expect(searchEntries(index, '   ')).toEqual({ matches: [], total: 0 })
  })

  it('CVE 번호 일부로 찾는다', () => {
    expect(ids('41203')).toEqual(['CVE-2026-41203'])
  })

  it('대소문자와 앞뒤 공백을 가리지 않는다', () => {
    expect(ids('  cve-2026-40117 ')).toEqual(['CVE-2026-40117'])
  })

  it('제품명으로 찾고, 제품이 맞는 건을 제목만 맞는 건보다 앞에 둔다', () => {
    expect(ids('chrome')).toEqual(['CVE-2026-42301', 'CVE-2026-41203', 'CVE-2026-41990'])
  })

  it('CVE 번호가 정확히 같은 건을 맨 앞에 둔다', () => {
    const withSimilar: SearchEntry[] = [
      { id: 'CVE-2026-412031', title: '비슷한 번호', product: null },
      { id: 'CVE-2026-41203', title: 'V8 타입 혼동', product: 'Chrome' },
    ]

    expect(searchEntries(withSimilar, 'CVE-2026-41203').matches.map((entry) => entry.id)).toEqual([
      'CVE-2026-41203',
      'CVE-2026-412031',
    ])
  })

  it('결과는 정해진 수까지만 돌려주고, 전체 건수를 함께 알린다', () => {
    const many: SearchEntry[] = Array.from({ length: 15 }, (_, index) => ({
      id: `CVE-2026-5${String(index).padStart(4, '0')}`,
      title: 'Chrome 취약점',
      product: 'Chrome',
    }))

    const result = searchEntries(many, 'chrome', 10)

    expect(result.matches).toHaveLength(10)
    expect(result.total).toBe(15)
  })
})

describe('CVE 번호 바로 가기', () => {
  it('검색어가 완전한 CVE 번호면 대문자로 맞춰 돌려준다', () => {
    expect(findDirectCveId(' cve-2025-1234 ')).toBe('CVE-2025-1234')
  })

  it('완전한 CVE 번호가 아니면 돌려주지 않는다', () => {
    expect(findDirectCveId('CVE-2025')).toBeNull()
    expect(findDirectCveId('chrome')).toBeNull()
  })
})
