import { describe, expect, it } from 'vitest'
import type { ReferenceLink } from '../types/cve.ts'
import { groupReferences } from './references.ts'

describe('상세 페이지 참고 링크', () => {
  it('벤더 어드바이저리 → NVD → 뉴스 → PoC 순서로 묶는다', () => {
    const references: ReferenceLink[] = [
      { url: 'https://news.example/a', source: 'news', title: '긴급 패치 기사' },
      { url: 'https://nvd.nist.gov/vuln/detail/CVE-2026-41203', source: 'nvd', title: null },
      { url: 'https://vendor.example/advisory', source: 'vendor', title: null },
    ]

    expect(groupReferences('CVE-2026-41203', references)).toEqual([
      { source: 'vendor', label: '벤더', links: [{ url: 'https://vendor.example/advisory', title: 'vendor.example' }] },
      {
        source: 'nvd',
        label: 'NVD',
        links: [{ url: 'https://nvd.nist.gov/vuln/detail/CVE-2026-41203', title: 'NVD 원문 CVE-2026-41203' }],
      },
      { source: 'news', label: '뉴스', links: [{ url: 'https://news.example/a', title: '긴급 패치 기사' }] },
    ])
  })

  it('참고 링크에 NVD 가 없어도 NVD 원문 링크는 항상 넣는다', () => {
    expect(groupReferences('CVE-2026-41203', [])).toEqual([
      {
        source: 'nvd',
        label: 'NVD',
        links: [{ url: 'https://nvd.nist.gov/vuln/detail/CVE-2026-41203', title: 'NVD 원문 CVE-2026-41203' }],
      },
    ])
  })
})
