// 빌드한 사이트를 브라우저로 열어 본 결과를 판정한다 (Gate G 화면 쪽). 관찰은 run-browser.ts 가 한다
import type { GateResult } from './report.ts'

// 기획서 8장 "푸터 고지 (필수)" 목록 순서
export const FOOTER_NOTICES: { name: string; patterns: RegExp[] }[] = [
  {
    name: 'NVD 비인증 문구',
    patterns: [/This product uses the NVD API but is not endorsed or certified by the NVD\./],
  },
  { name: 'EPSS FIRST 출처', patterns: [/FIRST\.org/, /Exploit Prediction Scoring System/, /Jacobs et al\./] },
  { name: 'CVE 상표', patterns: [/CVE는 The MITRE Corporation의 등록 상표/] },
  { name: '데이터 출처 4종', patterns: [/NVD/, /CISA KEV/, /FIRST EPSS/, /OSV\.dev \/ GitHub Advisory Database/] },
  { name: '면책(AI 요약 포함)', patterns: [/정확성·완전성을 보장하지 않습니다/, /AI가 생성/] },
]

const FOOTER_ITEM = '푸터 고지 5종'

export const checkFooterText = (text: string | null): GateResult => {
  if (text === null) return { item: FOOTER_ITEM, status: 'fail', detail: '푸터(contentinfo)를 찾지 못함' }
  const missing = FOOTER_NOTICES.filter((notice) => notice.patterns.some((pattern) => pattern.test(text) === false))
  if (missing.length > 0) {
    return { item: FOOTER_ITEM, status: 'fail', detail: `빠짐: ${missing.map((notice) => notice.name).join(', ')}` }
  }
  return { item: FOOTER_ITEM, status: 'pass', detail: '5종 모두 보임' }
}

const STALE_ITEM = 'stale 배너'

export const checkStaleBanner = (seen: { stale: boolean; fresh: boolean }): GateResult => {
  if (seen.stale === false) {
    return { item: STALE_ITEM, status: 'fail', detail: 'updated_at 을 48시간 전으로 바꿨는데 배너가 안 보임' }
  }
  if (seen.fresh) return { item: STALE_ITEM, status: 'fail', detail: '방금 갱신한 새 데이터에도 배너가 보임' }
  return { item: STALE_ITEM, status: 'pass', detail: '48시간 전 데이터에서 보이고, 방금 갱신한 데이터에서는 안 보임' }
}

const CACHE_ITEM = '캐시: 새 version 반영'

const versionedDataFiles = (urls: string[]) => urls.filter((url) => /[?&]v=/.test(url))

export const checkCacheBusting = (
  seen: { before: string[]; after: string[] },
  versions: { before: string; after: string },
): GateResult => {
  const after = versionedDataFiles(seen.after)
  if (versionedDataFiles(seen.before).length === 0 || after.length === 0) {
    return { item: CACHE_ITEM, status: 'fail', detail: '데이터 파일 요청에 ?v= 가 없음' }
  }
  const expected = `v=${encodeURIComponent(versions.after)}`
  const stale = after.filter((url) => url.includes(expected) === false)
  if (stale.length > 0) {
    return { item: CACHE_ITEM, status: 'fail', detail: `새로고침 뒤에도 옛 version: ${stale.join(', ')}` }
  }
  return {
    item: CACHE_ITEM,
    status: 'pass',
    detail: `?v=${versions.before} → ?v=${versions.after} (${after.length}개 파일)`,
  }
}

const LAZY_ITEM = 'search-index.json 지연 로드'

const isSearchIndex = (url: string) => url.includes('search-index.json')

export const checkSearchIndexLazy = (seen: { beforeFocus: string[]; afterFocus: string[] }): GateResult => {
  if (seen.beforeFocus.some(isSearchIndex)) {
    return { item: LAZY_ITEM, status: 'fail', detail: '포커스 전에 search-index.json 을 받음' }
  }
  if (seen.afterFocus.some(isSearchIndex) === false) {
    return { item: LAZY_ITEM, status: 'fail', detail: '포커스 뒤에도 search-index.json 을 받지 않음' }
  }
  return { item: LAZY_ITEM, status: 'pass', detail: '포커스 전 0회, 포커스 뒤 요청' }
}

const RECENT_ITEM = 'recent.json 지연 로드 (/new)'
const RECENT_FILE = 'data/recent.json'

// 최근 7일 새 CVE(약 3,400건)는 /new 에 들어올 때만, 메인과 같은 ?v=<meta.version> 규칙으로 받는다 (2026-11-01)
export const checkRecentLazy = (seen: { onMain: string[]; onNew: string[] }, version: string): GateResult => {
  if (seen.onMain.some((url) => url.includes(RECENT_FILE))) {
    return { item: RECENT_ITEM, status: 'fail', detail: '메인 첫 화면에서 recent.json 을 받음' }
  }
  const onNew = seen.onNew.filter((url) => url.includes(RECENT_FILE))
  if (onNew.length === 0)
    return { item: RECENT_ITEM, status: 'fail', detail: '/new 에 들어가도 recent.json 을 받지 않음' }
  if (onNew.some((url) => url.includes(`?v=${encodeURIComponent(version)}`)) === false) {
    return { item: RECENT_ITEM, status: 'fail', detail: `recent.json 이 지금 version(${version})으로 요청되지 않음` }
  }
  return { item: RECENT_ITEM, status: 'pass', detail: `메인 0회, /new 에서 ?v=${version} 로 요청` }
}
