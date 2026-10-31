// 데이터 파일만 보고 판정하는 Gate G 항목 (WBS 9장): search-index.json 크기, 한국어 요약 검수 보조
import type { CveDetail, Summary } from '../../src/types/cve.ts'
import type { GateResult } from './report.ts'

const MB = 1024 * 1024
// 기획서 7장: 1년치 3~4MB. 넘으면 연도별 분할을 검토한다
export const SEARCH_INDEX_LIMIT_BYTES = 4 * MB

const SIZE_ITEM = 'search-index.json 크기'

export const checkSearchIndexSize = (bytes: number | null): GateResult => {
  if (bytes === null) return { item: SIZE_ITEM, status: 'fail', detail: '파일 없음' }
  const size = `${(bytes / MB).toFixed(2)}MB`
  if (bytes > SEARCH_INDEX_LIMIT_BYTES) return { item: SIZE_ITEM, status: 'fail', detail: `${size} > 4MB 한도` }
  return { item: SIZE_ITEM, status: 'pass', detail: `${size} ≤ 4MB` }
}

export interface SummarySuspect {
  id: string
  field: 'what' | 'who' | 'action'
  reason: string
  excerpt: string
}

// 요약에 있으면 안 되는 것들(기획서 9장 "명령어 생성 금지"). 사람이 최종 판단하므로 넓게 잡는다
const COMMAND_PATTERNS: { reason: string; pattern: RegExp }[] = [
  { reason: '코드 블록', pattern: /```/ },
  { reason: '인라인 코드', pattern: /`[^`]+`/ },
  { reason: '셸 프롬프트', pattern: /(?:^|\s)[$#]\s*[a-z]/ },
  { reason: 'sudo', pattern: /\bsudo\b/ },
  { reason: '내려받기 명령', pattern: /\b(?:curl|wget|Invoke-WebRequest)\b/i },
  { reason: '파이프·명령 연결', pattern: /&&|\|\|?\s*(?:sh|bash|zsh)\b/ },
  { reason: '파일 경로', pattern: /(?:^|[\s("'])(?:~|\.{1,2})?\/[\w.-]+\/[\w./-]*/ },
  { reason: '윈도우 경로', pattern: /\b[A-Za-z]:\\/ },
  { reason: '설정 키=값', pattern: /\b[\w.-]+\s*=\s*["'\w.-]+/ },
  { reason: '명령줄 옵션', pattern: /(?:^|\s)--[a-z][\w-]*/i },
  { reason: 'URL', pattern: /https?:\/\// },
]

const VERSION = /(?<![\d.])v?(\d+(?:\.\d+)+)(?!\d)/g
const FIELDS: SummarySuspect['field'][] = ['what', 'who', 'action']
const EXCERPT_RADIUS = 20

const excerptAround = (text: string, index: number, length: number): string => {
  const start = Math.max(0, index - EXCERPT_RADIUS)
  const end = Math.min(text.length, index + length + EXCERPT_RADIUS)
  return `${start > 0 ? '…' : ''}${text.slice(start, end)}${end < text.length ? '…' : ''}`
}

// 버전 숫자의 출처로 인정하는 원문: NVD 설명과 영향 제품(범위·수정 버전)
const sourceTextOf = (detail: CveDetail): string =>
  [
    detail.description,
    ...detail.affected.flatMap((product) => [
      product.product,
      product.vulnerable_range ?? '',
      product.fixed_version ?? '',
    ]),
  ].join('\n')

const scoreTexts = (detail: CveDetail): string[] =>
  detail.cvss === null ? [] : [String(detail.cvss), detail.cvss.toFixed(1)]

const auditField = (detail: CveDetail, field: SummarySuspect['field'], text: string): SummarySuspect[] => {
  const commands = COMMAND_PATTERNS.flatMap(({ reason, pattern }) => {
    const match = pattern.exec(text)
    if (match === null) return []
    return [{ id: detail.id, field, reason, excerpt: excerptAround(text, match.index, match[0].length) }]
  })
  const source = sourceTextOf(detail)
  const scores = scoreTexts(detail)
  const versions = [...text.matchAll(VERSION)]
    .filter((match) => source.includes(match[1]) === false && scores.includes(match[1]) === false)
    .map((match) => ({
      id: detail.id,
      field,
      reason: `원문에 없는 버전 ${match[1]}`,
      excerpt: excerptAround(text, match.index, match[0].length),
    }))
  return [...commands, ...versions]
}

export const auditSummary = (detail: CveDetail): SummarySuspect[] => {
  const summary: Summary | null = detail.summary
  if (summary === null) return []
  return FIELDS.flatMap((field) => auditField(detail, field, summary[field]))
}

const SUMMARY_ITEM = '한국어 요약 검수 (명령어·없는 버전)'

// 사람이 최종 판단한다. 의심이 있으면 실패가 아니라 "확인 필요"로 두고 근거를 낸다
export const checkSummaries = (details: CveDetail[]): GateResult => {
  const summarized = details.filter((detail) => detail.summary !== null)
  if (summarized.length === 0) {
    return { item: SUMMARY_ITEM, status: 'review', detail: '요약 0건 — 검수할 요약이 없음 (기획 20건)' }
  }
  const suspects = summarized.flatMap(auditSummary)
  const detail = `요약 ${summarized.length}건 훑음 (기획 20건), 의심 ${suspects.length}건`
  if (suspects.length === 0) return { item: SUMMARY_ITEM, status: 'pass', detail }
  return {
    item: SUMMARY_ITEM,
    status: 'review',
    detail,
    evidence: suspects.map((suspect) => `${suspect.id} ${suspect.field}: ${suspect.reason} — ${suspect.excerpt}`),
  }
}
