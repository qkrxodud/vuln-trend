import { describe, expect, it } from 'vitest'
import { cveDetail } from '../test/fixtures.ts'
import type { AffectedProduct } from '../types/cve.ts'
import {
  addedFact,
  affectedName,
  dueFact,
  formatScorePart,
  referenceRows,
  scoreAxis,
  scoreRows,
  scoreSegment,
} from './detail-model.ts'

const TODAY = '2026-11-01'
const kevDetail = { ...cveDetail, is_kev: true, kev_due_date: '2026-11-03', kev_added_at: '2026-10-29' }

describe('CISA 조치 기한 칸', () => {
  it('3일 안이면 임박, 그 뒤면 남은 날, 지났으면 지난 날을 날짜와 함께 준다', () => {
    expect(dueFact(kevDetail, TODAY)).toEqual({ kind: 'urgent', days: 2, date: '2026-11-03' })
    expect(dueFact({ ...kevDetail, kev_due_date: '2026-11-10' }, TODAY)).toEqual({
      kind: 'later',
      days: 9,
      date: '2026-11-10',
    })
    expect(dueFact({ ...kevDetail, kev_due_date: '2026-10-22' }, TODAY)).toEqual({
      kind: 'overdue',
      days: 10,
      date: '2026-10-22',
    })
  })

  it('KEV 가 아니면 기한이 없다', () => {
    expect(dueFact({ ...cveDetail, is_kev: false, kev_due_date: null }, TODAY)).toEqual({ kind: 'not-kev' })
  })
})

describe('CISA KEV 등재 칸', () => {
  it('등재 며칠 전인지 날짜와 함께 주고, KEV 인데 등재일을 모르면 모른다고 한다', () => {
    expect(addedFact(kevDetail, TODAY)).toEqual({ kind: 'known', daysAgo: 3, date: '2026-10-29' })
    expect(addedFact({ ...kevDetail, kev_added_at: null }, TODAY)).toEqual({ kind: 'unknown' })
    expect(addedFact({ ...cveDetail, is_kev: false, kev_added_at: null }, TODAY)).toEqual({ kind: 'not-kev' })
  })
})

describe('영향 범위', () => {
  const item = (overrides: Partial<AffectedProduct>): AffectedProduct => ({
    vendor: 'gitlab',
    product: 'gitlab',
    display_name: 'GitLab',
    slug: 'gitlab',
    ecosystem: null,
    vulnerable_range: '<19.2.8',
    fixed_version: null,
    ...overrides,
  })

  it('표시 이름이 없으면 "제조사 / 제품"으로 적는다', () => {
    expect(affectedName(item({}))).toBe('GitLab')
    expect(affectedName(item({ display_name: null, vendor: 'siemens', product: 'industrial_edge_hub' }))).toBe(
      'siemens / industrial_edge_hub',
    )
  })
})

describe('참고 링크', () => {
  it('제조사 공지를 먼저 두고, 출처 글자와 제목(없으면 도메인)을 준다', () => {
    const rows = referenceRows('CVE-2026-1', [
      { url: 'https://nvd.nist.gov/vuln/detail/CVE-2026-1', source: 'nvd', title: null },
      { url: 'https://www.example.com/news/1', source: 'news', title: '보안 뉴스' },
      { url: 'https://docs.gitlab.com/advisory', source: 'vendor', title: null },
    ])

    expect(rows).toEqual([
      { url: 'https://docs.gitlab.com/advisory', source: '제조사', text: 'docs.gitlab.com' },
      { url: 'https://nvd.nist.gov/vuln/detail/CVE-2026-1', source: 'NVD', text: 'nvd.nist.gov' },
      { url: 'https://www.example.com/news/1', source: '뉴스', text: '보안 뉴스' },
    ])
  })

  it('NVD 원문이 없으면 넣는다', () => {
    expect(referenceRows('CVE-2026-1', [])).toEqual([
      { url: 'https://nvd.nist.gov/vuln/detail/CVE-2026-1', source: 'NVD', text: 'nvd.nist.gov' },
    ])
  })
})

describe('점수 구성 (0 기준 발산 막대)', () => {
  it('일곱 항을 점수식 순서로 주고, 0 인 항도 빼지 않는다', () => {
    const rows = scoreRows({ kev: 50, ransomware: 0, epss: 8.2, news: 0, poc: 0, cvss: 10, decay: -5.2 })

    expect(rows.map((row) => row.label)).toEqual([
      'KEV 등재',
      '랜섬웨어 악용',
      'EPSS (악용 가능성 × 30)',
      '뉴스 언급',
      'PoC',
      'CVSS (그대로)',
      '시간 감쇠 (30일마다 절반)',
    ])
    expect(rows.map((row) => row.value)).toEqual([50, 0, 8.2, 0, 0, 10, -5.2])
  })

  it('왼쪽 폭은 감쇠를 10 단위로 올린 값이고 최소 20, 오른쪽은 100 고정이다', () => {
    expect(scoreAxis(-5.2).left).toBe(20)
    expect(scoreAxis(-85.2).left).toBe(90)
    expect(scoreAxis(-85.2).right).toBe(100)
  })

  it('왼쪽이 좁으면 음수 눈금 글자는 생략한다', () => {
    expect(scoreAxis(-5.2).ticks).toEqual([0, 50, 100])
    expect(scoreAxis(-85.2).ticks).toEqual([-90, 0, 50, 100])
  })

  it('더하는 항은 0 에서 오른쪽으로, 감쇠는 0 에서 왼쪽으로 그린다', () => {
    const axis = scoreAxis(-20)

    expect(axis.x(0)).toBeCloseTo((20 / 120) * 100)
    expect(scoreSegment(50, axis)).toEqual({ left: axis.x(0), width: (50 / 120) * 100 })
    expect(scoreSegment(-20, axis)).toEqual({ left: 0, width: (20 / 120) * 100 })
    expect(scoreSegment(0, axis)).toBeNull()
  })

  it('점수 칸은 부호와 소수 1자리로 적는다', () => {
    expect(formatScorePart(50)).toBe('+50.0')
    expect(formatScorePart(-5.2)).toBe('−5.2')
    expect(formatScorePart(0)).toBe('0.0')
  })
})
