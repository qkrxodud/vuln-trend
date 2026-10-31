import { describe, expect, it } from 'vitest'
import {
  checkCacheBusting,
  checkFooterText,
  checkSearchIndexLazy,
  checkStaleBanner,
  FOOTER_NOTICES,
} from './browser-checks.ts'

const FULL_FOOTER = [
  '데이터 출처 NVD CISA KEV FIRST EPSS OSV.dev / GitHub Advisory Database',
  'This product uses the NVD API but is not endorsed or certified by the NVD.',
  'EPSS 데이터는 FIRST.org의 Exploit Prediction Scoring System(Jacobs et al.)을 사용하며, 점수는 Empirical Security 가 생성합니다.',
  'CVE는 The MITRE Corporation의 등록 상표입니다.',
  '이 사이트는 정보 제공 목적이며 정확성·완전성을 보장하지 않습니다. 한국어 요약은 AI가 생성한 것으로 반드시 원문을 확인하세요.',
].join('\n')

describe('푸터 고지 5종 (기획서 8장 "푸터 고지")', () => {
  it('고지는 기획서 목록대로 다섯 가지다', () => {
    expect(FOOTER_NOTICES.map((notice) => notice.name)).toEqual([
      'NVD 비인증 문구',
      'EPSS FIRST 출처',
      'CVE 상표',
      '데이터 출처 4종',
      '면책(AI 요약 포함)',
    ])
  })

  it('다섯 가지가 모두 보이면 통과한다', () => {
    expect(checkFooterText(FULL_FOOTER)).toMatchObject({ status: 'pass', detail: '5종 모두 보임' })
  })

  it('하나라도 빠지면 빠진 고지 이름을 들어 실패한다', () => {
    const withoutTrademark = FULL_FOOTER.replace('CVE는 The MITRE Corporation의 등록 상표입니다.', '')

    expect(checkFooterText(withoutTrademark)).toMatchObject({ status: 'fail', detail: '빠짐: CVE 상표' })
  })

  it('데이터 출처는 네 곳이 다 있어야 한다', () => {
    const withoutOsv = FULL_FOOTER.replace('OSV.dev / GitHub Advisory Database', '')

    expect(checkFooterText(withoutOsv).detail).toBe('빠짐: 데이터 출처 4종')
  })

  it('AI 요약 면책이 없으면 면책이 빠진 것으로 본다', () => {
    const withoutAi = FULL_FOOTER.replace('한국어 요약은 AI가 생성한 것으로 반드시 원문을 확인하세요.', '')

    expect(checkFooterText(withoutAi).detail).toBe('빠짐: 면책(AI 요약 포함)')
  })

  it('푸터를 찾지 못하면 실패한다', () => {
    expect(checkFooterText(null)).toMatchObject({ status: 'fail', detail: '푸터(contentinfo)를 찾지 못함' })
  })
})

describe('stale 배너', () => {
  it('오래된 데이터에서만 배너가 보이면 통과한다', () => {
    expect(checkStaleBanner({ stale: true, fresh: false }).status).toBe('pass')
  })

  it('오래된 데이터인데 배너가 없으면 실패한다', () => {
    expect(checkStaleBanner({ stale: false, fresh: false })).toMatchObject({
      status: 'fail',
      detail: expect.stringContaining('배너가 안 보임'),
    })
  })

  it('새 데이터인데 배너가 보이면 실패한다', () => {
    expect(checkStaleBanner({ stale: true, fresh: true })).toMatchObject({
      status: 'fail',
      detail: expect.stringContaining('새 데이터에도'),
    })
  })
})

describe('캐시: version 이 바뀌면 새로고침 때 새 파일을 받는다', () => {
  const before = ['/vuln-trend/data/meta.json', '/vuln-trend/data/stats.json?v=A', '/vuln-trend/data/index.json?v=A']

  it('새로고침 뒤 데이터 요청이 모두 새 version 이면 통과한다', () => {
    const after = ['/vuln-trend/data/meta.json', '/vuln-trend/data/stats.json?v=B', '/vuln-trend/data/index.json?v=B']

    expect(checkCacheBusting({ before, after }, { before: 'A', after: 'B' })).toMatchObject({
      status: 'pass',
      detail: '?v=A → ?v=B (2개 파일)',
    })
  })

  it('옛 version 으로 받은 파일이 남으면 실패하고 그 파일을 든다', () => {
    const after = ['/vuln-trend/data/meta.json', '/vuln-trend/data/stats.json?v=B', '/vuln-trend/data/index.json?v=A']

    expect(checkCacheBusting({ before, after }, { before: 'A', after: 'B' })).toMatchObject({
      status: 'fail',
      detail: '새로고침 뒤에도 옛 version: /vuln-trend/data/index.json?v=A',
    })
  })

  it('처음부터 ?v= 를 붙이지 않았으면 실패한다', () => {
    const plain = ['/vuln-trend/data/meta.json', '/vuln-trend/data/index.json']

    expect(checkCacheBusting({ before: plain, after: plain }, { before: 'A', after: 'B' }).status).toBe('fail')
  })

  it('version 값의 특수문자는 주소 인코딩된 채로 맞춰 본다', () => {
    const after = ['/vuln-trend/data/index.json?v=2026-10-31T09%3A00']

    expect(
      checkCacheBusting(
        { before: ['/vuln-trend/data/index.json?v=A'], after },
        { before: 'A', after: '2026-10-31T09:00' },
      ).status,
    ).toBe('pass')
  })
})

describe('search-index.json 은 검색창 포커스 전에는 받지 않는다', () => {
  it('포커스 전 0번, 포커스 뒤 받으면 통과한다', () => {
    expect(
      checkSearchIndexLazy({ beforeFocus: ['/d/meta.json'], afterFocus: ['/d/meta.json', '/d/search-index.json?v=A'] }),
    ).toMatchObject({ status: 'pass' })
  })

  it('포커스 전에 받았으면 실패한다', () => {
    expect(
      checkSearchIndexLazy({ beforeFocus: ['/d/search-index.json?v=A'], afterFocus: ['/d/search-index.json?v=A'] }),
    ).toMatchObject({ status: 'fail', detail: '포커스 전에 search-index.json 을 받음' })
  })

  it('포커스해도 받지 않으면 검색이 안 되니 실패한다', () => {
    expect(checkSearchIndexLazy({ beforeFocus: [], afterFocus: [] })).toMatchObject({
      status: 'fail',
      detail: '포커스 뒤에도 search-index.json 을 받지 않음',
    })
  })
})
