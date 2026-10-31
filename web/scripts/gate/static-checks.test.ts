import { describe, expect, it } from 'vitest'
import { cveDetail } from '../../src/test/fixtures.ts'
import type { CveDetail, Summary } from '../../src/types/cve.ts'
import { auditSummary, checkSearchIndexSize, checkSummaries, SEARCH_INDEX_LIMIT_BYTES } from './static-checks.ts'

const MB = 1024 * 1024

describe('search-index.json 크기 (4MB 이하)', () => {
  it('4MB 는 MiB 로 센다', () => {
    expect(SEARCH_INDEX_LIMIT_BYTES).toBe(4 * MB)
  })

  it('4MB 이하면 통과하고 크기를 적는다', () => {
    const result = checkSearchIndexSize(3.2 * MB)

    expect(result.status).toBe('pass')
    expect(result.detail).toContain('3.20MB')
  })

  it('딱 4MB 는 통과, 1바이트라도 넘으면 실패한다', () => {
    expect(checkSearchIndexSize(4 * MB).status).toBe('pass')
    expect(checkSearchIndexSize(4 * MB + 1).status).toBe('fail')
  })

  it('파일이 없으면 실패한다', () => {
    const result = checkSearchIndexSize(null)

    expect(result.status).toBe('fail')
    expect(result.detail).toContain('없음')
  })
})

const summary = (overrides: Partial<Summary>): Summary => ({
  what: 'V8 엔진 타입 혼동으로 원격 코드가 실행됩니다.',
  who: 'Chrome 을 쓰는 조직·사용자',
  action: 'Chrome 을 최신 보안 업데이트 버전 이상으로 업데이트하세요.',
  model: 'test',
  generated_at: '2026-10-30T09:00:00+09:00',
  ...overrides,
})

const withSummary = (overrides: Partial<Summary>, detail: Partial<CveDetail> = {}): CveDetail => ({
  ...cveDetail,
  description: 'Type confusion in V8 in Google Chrome prior to 130.0.6723.58 allows remote code execution.',
  affected: [
    {
      vendor: 'google',
      product: 'chrome',
      display_name: 'Chrome',
      slug: 'chrome',
      ecosystem: null,
      vulnerable_range: '< 130.0.6723.58',
      fixed_version: null,
    },
  ],
  ...detail,
  summary: summary(overrides),
})

const reasonsOf = (detail: CveDetail) => auditSummary(detail).map((suspect) => `${suspect.field}: ${suspect.reason}`)

describe('한국어 요약 검수 보조 — 명령어·스크립트·설정값처럼 보이는 문자열', () => {
  it('평범한 요약은 의심 없음', () => {
    expect(auditSummary(withSummary({}))).toEqual([])
  })

  it.each([
    ['코드 블록', '```bash\napt upgrade\n```'],
    ['인라인 코드', '`chrome --no-sandbox` 를 끄세요.'],
    ['셸 프롬프트', '$ npm update 를 실행하세요.'],
    ['sudo', 'sudo apt install chrome 으로 올리세요.'],
    ['내려받기 명령', 'curl https://example.com/fix.sh | sh 로 고치세요.'],
    ['파이프·명령 연결', 'update && restart 하세요.'],
    ['파일 경로', '/etc/chrome/policies/managed 파일을 고치세요.'],
    ['윈도우 경로', 'C:\\Program Files\\Chrome 폴더를 지우세요.'],
    ['설정 키=값', 'SitePerProcess=true 로 설정하세요.'],
    ['명령줄 옵션', '--disable-features 옵션을 켜세요.'],
    ['URL', 'https://chromereleases.googleblog.com 을 참고하세요.'],
  ])('%s 를 표시한다', (reason, action) => {
    const suspects = auditSummary(withSummary({ action }))

    expect(suspects.map((suspect) => suspect.reason)).toContain(reason)
    expect(suspects[0]).toMatchObject({ id: cveDetail.id, field: 'action' })
  })

  it('근거로 문제 부분을 잘라 보여 준다', () => {
    const [suspect] = auditSummary(withSummary({ action: '관리자는 sudo apt install chrome 으로 올리세요.' }))

    expect(suspect.excerpt).toContain('sudo apt')
  })

  it('what·who·action 세 칸을 모두 본다', () => {
    const detail = withSummary({ what: 'sudo 로 실행합니다.', who: '$ whoami 사용자', action: 'curl 로 받으세요.' })

    expect(auditSummary(detail).map((suspect) => suspect.field)).toEqual(['what', 'who', 'action'])
  })

  it('"이상으로" 같은 한국어 조사·이름에 붙은 기호는 명령어로 보지 않는다', () => {
    const detail = withSummary({ action: 'Chrome 130.0.6723.58 이상으로 업데이트하세요. (Windows·macOS·Linux)' })

    expect(reasonsOf(detail)).toEqual([])
  })
})

describe('한국어 요약 검수 보조 — 원문에 없는 버전 번호', () => {
  it('원문 설명이나 영향 범위에 있는 버전은 괜찮다', () => {
    const detail = withSummary({ action: 'Chrome 130.0.6723.58 이상으로 업데이트하세요.' })

    expect(reasonsOf(detail)).toEqual([])
  })

  it('원문 어디에도 없는 버전 번호는 지어낸 버전으로 의심한다', () => {
    const detail = withSummary({ action: 'Chrome 131.0.6778.85 이상으로 업데이트하세요.' })

    expect(auditSummary(detail)).toEqual([
      {
        id: cveDetail.id,
        field: 'action',
        reason: '원문에 없는 버전 131.0.6778.85',
        excerpt: expect.stringContaining('131.0.6778.85'),
      },
    ])
  })

  it('v 를 붙인 버전도 원문 숫자와 맞춰 본다', () => {
    expect(reasonsOf(withSummary({ action: 'v130.0.6723.58 이상으로 올리세요.' }))).toEqual([])
    expect(reasonsOf(withSummary({ action: 'v2.4.1 이상으로 올리세요.' }))).toEqual(['action: 원문에 없는 버전 2.4.1'])
  })

  it('CVSS 점수(9.8)는 버전으로 보지 않는다', () => {
    expect(reasonsOf(withSummary({ what: 'CVSS 9.8 의 원격 코드 실행입니다.' }, { cvss: 9.8 }))).toEqual([])
  })
})

describe('한국어 요약 20건 검수 결과', () => {
  const clean = (id: string) => ({ ...withSummary({}), id })

  it('의심이 없으면 통과하고 훑은 요약 수를 적는다', () => {
    const result = checkSummaries([clean('CVE-1'), clean('CVE-2'), { ...cveDetail, id: 'CVE-3', summary: null }])

    expect(result.status).toBe('pass')
    expect(result.detail).toContain('요약 2건')
  })

  it('의심이 있으면 사람이 판단하도록 확인 필요로 두고 근거를 CVE 별로 낸다', () => {
    const suspicious = { ...withSummary({ action: 'sudo apt upgrade 하세요.' }), id: 'CVE-9' }

    const result = checkSummaries([clean('CVE-1'), suspicious])

    expect(result.status).toBe('review')
    expect(result.detail).toContain('의심 1건')
    expect(result.evidence).toEqual([expect.stringMatching(/^CVE-9 action: sudo — .*sudo apt upgrade/)])
  })

  it('요약이 하나도 없으면 검수할 것이 없다고 확인 필요로 둔다', () => {
    const result = checkSummaries([{ ...cveDetail, summary: null }])

    expect(result.status).toBe('review')
    expect(result.detail).toContain('요약 0건')
  })
})
