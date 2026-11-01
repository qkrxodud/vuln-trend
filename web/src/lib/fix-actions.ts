// "어떻게 조치하나" (사용자 피드백 2026-11-01: 조치 방법이 없다).
// 데이터에 있는 것만 쓴다: 수정 버전(fix_versions), 패치·제조사 공지 링크(NVD tags), CISA 요구 조치(원문), AI 요약의 조치
import type { CveDetail } from '../types/cve.ts'
import type { TrendRow } from '../types/trend.ts'
import { affectedName } from './detail-model.ts'

/** 버전 비교: 숫자 마디는 숫자로, 아니면 글자로. a<b 면 음수 */
const SEGMENT = /[.\-+_]/

export const compareVersions = (a: string, b: string): number => {
  const left = a.split(SEGMENT)
  const right = b.split(SEGMENT)
  for (let index = 0; index < Math.max(left.length, right.length); index += 1) {
    const x = left[index]
    const y = right[index]
    if (x === undefined) return -1
    if (y === undefined) return 1
    const order =
      /^\d+$/.test(x) && /^\d+$/.test(y) ? Number(x) - Number(y) : x.localeCompare(y, 'en', { numeric: true })
    if (order !== 0) return order
  }
  return 0
}

export interface RowFixLine {
  /** 가장 높은 수정 버전. 없으면 `null` */
  highest: string | null
  /** 그 밖의 버전 수 ("외 n개") */
  others: number
  patchUrl: string | null
}

/** 목록 행 한 줄 "→ 19.4.2 이상으로 업데이트 · 패치 공지 ↗". 둘 다 없으면 `null` */
export const rowFixLine = (row: TrendRow): RowFixLine | null => {
  const versions = [...new Set(row.fix_versions ?? [])].sort(compareVersions)
  const patchUrl = row.patch_url ?? null
  if (versions.length === 0 && patchUrl === null) return null
  return { highest: versions.at(-1) ?? null, others: Math.max(0, versions.length - 1), patchUrl }
}

export interface UpdateTarget {
  name: string
  /** 낮은 것부터 */
  versions: string[]
}

/** ① 업데이트: 제품별 수정 버전 */
export const updateTargets = (detail: CveDetail): UpdateTarget[] => {
  const byName = new Map<string, Set<string>>()
  detail.affected.forEach((item) => {
    const versions = item.fix_versions ?? []
    if (versions.length === 0) return
    const name = affectedName(item)
    byName.set(name, new Set([...(byName.get(name) ?? []), ...versions]))
  })
  return [...byName.entries()].map(([name, versions]) => ({ name, versions: [...versions].sort(compareVersions) }))
}

/** "19.2.8 / 19.3.5 / 19.4.2 이상" */
export const formatVersions = (versions: string[]): string => `${versions.join(' / ')} 이상`

export interface PatchLink {
  url: string
  /** 패치 / 제조사 공지 / 완화 방법 / 릴리스 노트 */
  kind: string
  text: string
}

/** ② 패치·제조사 공지: NVD 태그가 Patch·Vendor Advisory·Mitigation·Release Notes 인 링크. Broken Link 는 뺀다 */
const PATCH_TAGS: Record<string, string> = {
  Patch: '패치',
  'Vendor Advisory': '제조사 공지',
  Mitigation: '완화 방법',
  'Release Notes': '릴리스 노트',
}
const BROKEN = 'Broken Link'

const hostOf = (url: string): string => new URL(url).hostname.replace(/^www\./, '')

export const patchLinks = (detail: CveDetail): PatchLink[] => {
  const seen = new Set<string>()
  return detail.references.flatMap((reference) => {
    const tags = reference.tags ?? []
    const tag = tags.find((name) => PATCH_TAGS[name] !== undefined)
    if (tag === undefined || tags.includes(BROKEN) || seen.has(reference.url)) return []
    seen.add(reference.url)
    return [{ url: reference.url, kind: PATCH_TAGS[tag], text: reference.title ?? hostOf(reference.url) }]
  })
}

/** ③ CISA 요구 조치 원문. 없거나 비었으면 `null` */
export const requiredAction = (detail: CveDetail): string | null => {
  const action = (detail.kev_required_action ?? '').trim()
  return action === '' ? null : action
}
