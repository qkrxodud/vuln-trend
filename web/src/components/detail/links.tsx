import type { ReactNode } from 'react'
import { nvdUrl } from '../../lib/cve-detail.ts'

export const LinkTag = ({ children }: { children: ReactNode }) => (
  <span className="rounded bg-chip px-1.5 py-0.5 text-[11px] font-semibold text-ink-soft">{children}</span>
)

export const ExternalLink = ({ href, tag, children }: { href: string; tag: string; children: ReactNode }) => (
  <a href={href} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-accent">
    <LinkTag>{tag}</LinkTag>
    {children}
  </a>
)

export const NvdLink = ({ cveId }: { cveId: string }) => (
  <ExternalLink href={nvdUrl(cveId)} tag="NVD">
    NVD 원문 {cveId}
  </ExternalLink>
)
