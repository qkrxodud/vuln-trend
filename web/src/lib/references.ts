// 상세 페이지 참고 링크 묶음 (기획서 8장: 벤더 어드바이저리, 뉴스, PoC). NVD 원문은 항상 넣는다
import type { ReferenceLink, ReferenceSource } from '../types/cve.ts'
import { nvdUrl } from './cve-detail.ts'

export interface ReferenceGroup {
  source: ReferenceSource
  label: string
  links: { url: string; title: string }[]
}

const GROUP_ORDER: { source: ReferenceSource; label: string }[] = [
  { source: 'vendor', label: '벤더' },
  { source: 'nvd', label: 'NVD' },
  { source: 'news', label: '뉴스' },
  { source: 'poc', label: 'PoC' },
]

const titleOf = (cveId: string, reference: ReferenceLink): string => {
  if (reference.source === 'nvd') return `NVD 원문 ${cveId}`
  return reference.title ?? new URL(reference.url).hostname
}

export const groupReferences = (cveId: string, references: ReferenceLink[]): ReferenceGroup[] => {
  const hasNvd = references.some((reference) => reference.source === 'nvd')
  const all = hasNvd ? references : [...references, { url: nvdUrl(cveId), source: 'nvd' as const, title: null }]
  return GROUP_ORDER.map(({ source, label }) => ({
    source,
    label,
    links: all
      .filter((reference) => reference.source === source)
      .map((reference) => ({ url: reference.url, title: titleOf(cveId, reference) })),
  })).filter((group) => group.links.length > 0)
}
