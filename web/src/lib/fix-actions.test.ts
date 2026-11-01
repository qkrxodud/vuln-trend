import { describe, expect, it } from 'vitest'
import { cveDetail } from '../test/fixtures.ts'
import { trendRow } from '../test/rows.ts'
import type { AffectedProduct, CveDetail } from '../types/cve.ts'
import {
  compareVersions,
  formatVersions,
  patchLinks,
  requiredAction,
  rowFixLine,
  updateTargets,
} from './fix-actions.ts'

const affected = (overrides: Partial<AffectedProduct>): AffectedProduct => ({
  vendor: 'gitlab',
  product: 'gitlab',
  display_name: 'GitLab',
  slug: 'gitlab',
  ecosystem: null,
  vulnerable_range: null,
  fixed_version: null,
  ...overrides,
})

describe('버전 비교', () => {
  it('마디를 숫자로 비교한다 (19.10 이 19.9 보다 높다)', () => {
    expect(compareVersions('19.10.0', '19.9.5')).toBeGreaterThan(0)
    expect(compareVersions('8.2.35', '8.3.0')).toBeLessThan(0)
    expect(compareVersions('1.2.0', '1.2')).toBeGreaterThan(0)
    expect(compareVersions('1.0.0-rc1', '1.0.0-rc2')).toBeLessThan(0)
  })
})

describe('목록 행의 조치 한 줄', () => {
  it('수정 버전이 여럿이면 가장 높은 것 하나와 나머지 수를 준다', () => {
    expect(
      rowFixLine(trendRow({ fix_versions: ['19.2.8', '19.4.2', '19.3.5'], patch_url: 'https://docs.gitlab.com/a' })),
    ).toEqual({
      highest: '19.4.2',
      others: 2,
      patchUrl: 'https://docs.gitlab.com/a',
    })
  })

  it('버전만 있거나 링크만 있어도 줄을 만든다', () => {
    expect(rowFixLine(trendRow({ fix_versions: ['1.2.7'], patch_url: null }))).toEqual({
      highest: '1.2.7',
      others: 0,
      patchUrl: null,
    })
    expect(rowFixLine(trendRow({ fix_versions: [], patch_url: 'https://x.example/p' }))).toEqual({
      highest: null,
      others: 0,
      patchUrl: 'https://x.example/p',
    })
  })

  it('둘 다 없으면(배치가 아직 내보내지 않은 경우 포함) 줄을 숨긴다', () => {
    expect(rowFixLine(trendRow({ fix_versions: [], patch_url: null }))).toBeNull()
    expect(rowFixLine(trendRow({ fix_versions: undefined, patch_url: undefined }))).toBeNull()
  })
})

describe('상세의 업데이트 대상', () => {
  it('제품별로 수정 버전을 낮은 것부터 모으고, 수정 버전이 없는 제품은 뺀다', () => {
    const detail: CveDetail = {
      ...cveDetail,
      affected: [
        affected({ fix_versions: ['19.4.2', '19.2.8'] }),
        affected({ fix_versions: ['19.3.5', '19.2.8'] }),
        affected({ display_name: null, vendor: 'siemens', product: 'hub', fix_versions: [] }),
        affected({ display_name: 'PHP', fix_versions: undefined }),
      ],
    }

    expect(updateTargets(detail)).toEqual([{ name: 'GitLab', versions: ['19.2.8', '19.3.5', '19.4.2'] }])
  })

  it('버전 목록을 "이상"으로 이어 적는다', () => {
    expect(formatVersions(['19.2.8', '19.3.5', '19.4.2'])).toBe('19.2.8 / 19.3.5 / 19.4.2 이상')
  })
})

describe('패치·제조사 공지 링크', () => {
  it('NVD 태그가 패치·제조사 공지·완화·릴리스 노트인 링크만, 깨진 링크는 빼고, 같은 주소는 한 번만 준다', () => {
    const detail: CveDetail = {
      ...cveDetail,
      references: [
        { url: 'https://docs.gitlab.com/advisory', source: 'vendor', title: null, tags: ['Vendor Advisory', 'Patch'] },
        { url: 'https://docs.gitlab.com/advisory', source: 'nvd', title: null, tags: ['Patch'] },
        { url: 'https://example.com/old', source: 'vendor', title: 'Old', tags: ['Patch', 'Broken Link'] },
        { url: 'https://example.com/mitigate', source: 'vendor', title: '완화 안내', tags: ['Mitigation'] },
        { url: 'https://example.com/news', source: 'news', title: '기사', tags: ['Press/Media Coverage'] },
        { url: 'https://example.com/notags', source: 'vendor', title: null },
      ],
    }

    expect(patchLinks(detail)).toEqual([
      { url: 'https://docs.gitlab.com/advisory', kind: '제조사 공지', text: 'docs.gitlab.com' },
      { url: 'https://example.com/mitigate', kind: '완화 방법', text: '완화 안내' },
    ])
  })
})

describe('CISA 요구 조치', () => {
  it('원문이 있으면 그대로, 없거나 비었으면 없다', () => {
    expect(
      requiredAction({ ...cveDetail, kev_required_action: '  Apply mitigations per vendor instructions.  ' }),
    ).toBe('Apply mitigations per vendor instructions.')
    expect(requiredAction({ ...cveDetail, kev_required_action: '' })).toBeNull()
    expect(requiredAction({ ...cveDetail, kev_required_action: undefined })).toBeNull()
  })
})
