import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { cveDetail } from '../../test/fixtures.ts'
import type { AffectedProduct, CveDetail } from '../../types/cve.ts'
import { DetailBody } from './DetailBody.tsx'

const TODAY = '2026-11-01'

const detail: CveDetail = {
  ...cveDetail,
  id: 'CVE-2026-97229',
  title: '정규식 검증 미흡으로 인증되지 않은 공격자가 코드를 실행할 수 있습니다',
  score: 63,
  score_breakdown: { kev: 50, ransomware: 0, epss: 8.2, news: 0, poc: 0, cvss: 10, decay: -5.2 },
  why_now: 'CISA 악용 목록 등재',
  is_kev: true,
  kev_due_date: '2026-11-01',
  kev_added_at: '2026-10-29',
  severity: 'CRITICAL',
  cvss: 10,
  cvss_version: '3.1',
  epss: 0.2731,
  epss_percentile: 0.98,
  primary_product: 'GitLab',
  published_at: '2026-10-28T09:00:00+09:00',
  description: 'GitLab has remediated an issue.',
  summary: {
    what: '정규식 검증 미흡으로 원격 코드가 실행됩니다.',
    who: 'GitLab CE/EE 19.2.0 이상 19.2.8 미만',
    action: '19.2.8 이상으로 업데이트하세요.',
    model: 'gpt-5-luna',
    generated_at: '2026-11-01T18:00:00+09:00',
  },
  affected: [
    {
      vendor: 'gitlab',
      product: 'gitlab',
      display_name: 'GitLab',
      slug: 'gitlab',
      ecosystem: null,
      vulnerable_range: '>=19.2.0, <19.2.8',
      fixed_version: null,
    },
  ],
  references: [
    { url: 'https://nvd.nist.gov/vuln/detail/CVE-2026-97229', source: 'nvd', title: null },
    { url: 'https://docs.gitlab.com/advisory', source: 'vendor', title: null },
  ],
}

const renderBody = (overrides: Partial<CveDetail> = {}, rank: number | null = 6) =>
  render(<DetailBody detail={{ ...detail, ...overrides }} today={TODAY} rank={rank} level="panel" />)

const section = (name: string) => screen.getByRole('region', { name })
const fact = (name: string) => {
  const term = screen.getByText(name, { selector: 'dt' })
  return term.nextElementSibling as HTMLElement
}

describe('상세 머리', () => {
  it('ID·배지·제품, 제목, 심각도·CVSS(버전)과 순위·점수를 보인다', () => {
    renderBody()

    expect(screen.getByText('CVE-2026-97229')).toBeInTheDocument()
    expect(screen.getByText('새 KEV · 3일 전')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 3, name: detail.title })).toBeInTheDocument()
    expect(screen.getByText('Critical · CVSS 10.0 (v3.1)')).toBeInTheDocument()
    expect(screen.getByText('6위 · 63점')).toBeInTheDocument()
  })

  it('순위를 모르거나 CVSS 가 없으면 점수만, "CVSS 없음"으로 적는다', () => {
    renderBody({ cvss: null, cvss_version: null, severity: 'UNRATED' }, null)

    expect(screen.getByText('미평가 · CVSS 없음')).toBeInTheDocument()
    expect(screen.getByText('63점')).toBeInTheDocument()
  })
})

describe('사실 네 칸', () => {
  it('CISA 조치 기한·KEV 등재·EPSS·공개일을 값과 날짜로 보인다', () => {
    renderBody()

    expect(fact('CISA 조치 기한')).toHaveTextContent('D-0 2026-11-01')
    expect(fact('CISA KEV 등재')).toHaveTextContent('3일 전 2026-10-29')
    expect(fact('악용 가능성 (EPSS)')).toHaveTextContent('27% 상위 2%')
    expect(fact('공개일')).toHaveTextContent('2026-10-28')
  })

  it('기한이 지났으면 지난 날 수를, KEV 가 아니면 없음을 적는다', () => {
    const { unmount } = renderBody({ kev_due_date: '2026-10-22' })
    expect(fact('CISA 조치 기한')).toHaveTextContent('10일 지남 2026-10-22')
    unmount()

    renderBody({ is_kev: false, kev_due_date: null, kev_added_at: null, epss: null, epss_percentile: null })
    expect(fact('CISA 조치 기한')).toHaveTextContent('없음 (KEV 아님)')
    expect(fact('CISA KEV 등재')).toHaveTextContent('KEV 아님')
    expect(fact('악용 가능성 (EPSS)')).toHaveTextContent('EPSS 점수 없음')
  })

  it('KEV 인데 등재일을 모르면 "등재일 정보 없음"이다', () => {
    renderBody({ kev_added_at: null })

    expect(fact('CISA KEV 등재')).toHaveTextContent('등재일 정보 없음')
  })
})

describe('무엇이 문제인가', () => {
  it('AI 요약의 무엇·영향을 생성 정보·면책과 함께 보이고, 그 아래 원문을 둔다', () => {
    renderBody()
    const what = section('무엇이 문제인가')

    expect(what).toHaveTextContent('무엇 정규식 검증 미흡으로 원격 코드가 실행됩니다.')
    expect(what).toHaveTextContent('영향 GitLab CE/EE 19.2.0 이상 19.2.8 미만')
    expect(what).toHaveTextContent('AI 요약 · gpt-5-luna · 2026-11-01 — 원문과 다를 수 있습니다')
    expect(within(what).getByText('GitLab has remediated an issue.', { exact: false })).toHaveAttribute('lang', 'en')
  })

  it('한국어 요약이 없으면 그렇다고 밝히고 영문 원문을 보인다', () => {
    renderBody({ summary: null, summary_what: null })

    expect(section('무엇이 문제인가')).toHaveTextContent('한국어 요약이 아직 없습니다 · NVD 원문(영문)')
    expect(section('무엇이 문제인가')).toHaveTextContent('GitLab has remediated an issue.')
  })

  it('원문이 길면 접어 두고 "원문 전체 보기"로 편다', () => {
    renderBody({ description: 'x'.repeat(300) })
    const toggle = within(section('무엇이 문제인가')).getByRole('button', { name: '원문 전체 보기' })

    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    fireEvent.click(toggle)
    expect(within(section('무엇이 문제인가')).getByRole('button', { name: '원문 접기' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
  })

  it('왜 지금? 을 따로 적는다', () => {
    renderBody()

    expect(screen.getByText('왜 지금?').parentElement).toHaveTextContent('왜 지금? CISA 악용 목록 등재')
  })
})

describe('점수 구성', () => {
  it('일곱 항과 "= 점수"를 표로 보이고, 0 인 항은 흐리게 둔다', () => {
    renderBody()
    const table = within(section('점수 구성')).getByRole('table')
    const rows = within(table).getAllByRole('row').slice(1)

    expect(rows.map((row) => row.textContent)).toEqual([
      'KEV 등재+50.0',
      '랜섬웨어 악용0.0',
      'EPSS (악용 가능성 × 30)+8.2',
      '뉴스 언급0.0',
      'PoC0.0',
      'CVSS (그대로)+10.0',
      '시간 감쇠 (30일마다 절반)−5.2',
      '= 점수63',
    ])
    expect(rows[1]).toHaveAttribute('data-zero', 'true')
  })
})

describe('영향 범위', () => {
  const many = (count: number): AffectedProduct[] =>
    Array.from({ length: count }, (_, i) => ({
      vendor: 'siemens',
      product: `product_${i + 1}`,
      display_name: null,
      slug: null,
      ecosystem: null,
      vulnerable_range: null,
      fixed_version: i === 0 ? '2.0.1' : null,
    }))

  it('제품 수를 적고, 이름이 없으면 "제조사 / 제품", 범위가 없으면 그렇다고, 수정 버전은 있을 때만 적는다', () => {
    renderBody({ affected: many(2) })
    const affected = section('영향 범위')

    expect(affected).toHaveTextContent('2개 제품')
    expect(affected).toHaveTextContent('siemens / product_1')
    expect(affected).toHaveTextContent('영향 버전 범위 정보 없음')
    expect(within(affected).getAllByText(/^수정:/)).toHaveLength(1)
  })

  it('5개가 넘으면 5개만 보이고 "그 외 n개 더 보기"로 편다', () => {
    renderBody({ affected: many(8) })
    const affected = section('영향 범위')

    expect(within(affected).getAllByRole('listitem')).toHaveLength(5)
    fireEvent.click(within(affected).getByRole('button', { name: '그 외 3개 더 보기' }))
    expect(within(affected).getAllByRole('listitem')).toHaveLength(8)
    expect(within(affected).getByRole('button', { name: '접기' })).toHaveAttribute('aria-expanded', 'true')
  })

  it('범위가 여럿이면 "||" 사이에서 줄이 바뀌도록 조각마다 따로 둔다', () => {
    renderBody({ affected: [{ ...many(1)[0], vulnerable_range: '<22.23.4 || >=23.0.0, <24.22.1' }] })

    expect(within(section('영향 범위')).getByText('>=23.0.0, <24.22.1')).toHaveClass('inline-block')
  })

  it('영향 제품을 모르면 참고 링크의 원문을 보라고 한다', () => {
    renderBody({ affected: [] })

    expect(section('영향 범위')).toHaveTextContent(
      '영향 제품 정보가 아직 없습니다. 아래 참고 링크의 원문을 확인하세요.',
    )
  })
})

describe('어떻게 조치하나', () => {
  const withFixes: Partial<CveDetail> = {
    affected: [
      {
        vendor: 'gitlab',
        product: 'gitlab',
        display_name: 'GitLab',
        slug: 'gitlab',
        ecosystem: null,
        vulnerable_range: '>=19.2.0, <19.2.8',
        fixed_version: null,
        fix_versions: ['19.4.2', '19.2.8', '19.3.5'],
      },
    ],
    references: [
      { url: 'https://docs.gitlab.com/advisory', source: 'vendor', title: null, tags: ['Vendor Advisory', 'Patch'] },
      { url: 'https://example.com/old', source: 'vendor', title: 'Old', tags: ['Patch', 'Broken Link'] },
    ],
    kev_required_action: 'Apply mitigations per vendor instructions or discontinue use of the product.',
  }

  it('사실 칸 바로 아래, 문제 설명보다 먼저 둔다', () => {
    renderBody(withFixes)
    const headings = screen.getAllByRole('heading', { level: 4 }).map((heading) => heading.textContent)

    expect(headings.indexOf('어떻게 조치하나')).toBeLessThan(headings.indexOf('무엇이 문제인가'))
    expect(screen.queryByRole('region', { name: '조치 안내' })).toBeNull()
  })

  it('① 제품별 업데이트 버전 ② 패치·제조사 공지 링크(깨진 링크 제외) ③ CISA 요구 조치(원문, 접힘) ④ AI 요약의 조치를 차례로 보인다', () => {
    renderBody(withFixes)
    const fix = section('어떻게 조치하나')

    expect(fix).toHaveTextContent('GitLab 19.2.8 / 19.3.5 / 19.4.2 이상')
    expect(
      within(fix)
        .getAllByRole('link')
        .map((link) => link.textContent),
    ).toEqual(['docs.gitlab.com (새 창) ↗'])
    expect(within(fix).getByRole('link')).toHaveAttribute('target', '_blank')
    expect(fix).toHaveTextContent('제조사 공지')
    const cisa = within(fix).getByText('CISA 요구 조치(원문)').closest('details')
    expect(cisa).not.toHaveAttribute('open')
    expect(within(cisa as HTMLElement).getByText(/Apply mitigations/)).toHaveAttribute('lang', 'en')
    expect(fix).toHaveTextContent('19.2.8 이상으로 업데이트하세요.')
    expect(fix).toHaveTextContent('AI 요약의 조치 — 실제 조치는 제조사 공지를 따르세요')
  })

  it('조치 정보가 하나도 없으면 데이터에 없다고 밝힌다', () => {
    renderBody({ summary: null, references: [], kev_required_action: null })

    expect(section('어떻게 조치하나')).toHaveTextContent(
      '수정 버전·조치 정보가 데이터에 없습니다. 아래 제조사 공지에서 패치 여부를 확인하세요.',
    )
  })
})

describe('참고 링크', () => {
  it('제조사 공지를 먼저 출처·도메인으로 보이고, 새 창으로 연다', () => {
    renderBody()
    const links = within(section('참고 링크')).getAllByRole('link')

    expect(links.map((link) => link.textContent)).toEqual(['docs.gitlab.com (새 창) ↗', 'nvd.nist.gov (새 창) ↗'])
    expect(links[0]).toHaveAttribute('target', '_blank')
    expect(links[0]).toHaveAttribute('rel', 'noopener noreferrer')
    expect(section('참고 링크')).toHaveTextContent('제조사')
    expect(section('참고 링크')).toHaveTextContent('2개')
  })
})
