import { SEVERITY_BADGE } from '../../lib/severity.ts'
import { formatCvss, formatDueDate, formatEpss, formatTopPercent } from '../../lib/trend-list.ts'
import type { TrendRow } from '../../types/trend.ts'
import { Badge } from './Badge.tsx'

interface TrendRowCardProps {
  row: TrendRow
  selected: boolean
  onSelect: (cveId: string) => void
}

// 목록 한 줄: ID·배지·기한 / 요약(없으면 원문 제목) / 점수·EPSS·CVSS (기획서 8장 목록 행)
export const TrendRowCard = ({ row, selected, onSelect }: TrendRowCardProps) => {
  const severity = SEVERITY_BADGE[row.severity]
  const topPercent = formatTopPercent(row.epss_percentile)
  return (
    <button
      type="button"
      data-cve-id={row.id}
      aria-pressed={selected}
      onClick={() => onSelect(row.id)}
      className={`flex flex-col gap-2.5 rounded-[14px] border-[1.5px] px-[18px] py-4 text-left transition hover:border-[#a9c4b8] focus-visible:outline-2 focus-visible:outline-accent ${
        selected ? 'border-accent bg-accent-soft' : 'border-line bg-white'
      }`}
    >
      <span className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-[15px] font-semibold">{row.id}</span>
        {row.is_kev ? <Badge tone="kev">KEV</Badge> : null}
        {row.kev_ransomware ? <Badge tone="ransomware">랜섬웨어</Badge> : null}
        <Badge style={{ background: severity.background, color: severity.color }}>{severity.label}</Badge>
        {row.primary_product === null ? null : <Badge tone="neutral">{row.primary_product}</Badge>}
        {/* 오른쪽 끝: KEV 기한, 좁은 화면에서는 상세 페이지로 간다는 표시(디자인 원본 vt-mobile-only)도 함께 */}
        <span className="ml-auto flex items-center gap-3 text-xs font-semibold whitespace-nowrap">
          {row.is_kev && row.kev_due_date !== null ? (
            <span className="text-kev">KEV 패치 기한 {formatDueDate(row.kev_due_date)}</span>
          ) : null}
          <span aria-hidden="true" className="text-accent lg:hidden">
            상세 →
          </span>
        </span>
      </span>
      {row.summary_what === null ? (
        <span className="text-[13.5px] text-ink-soft">
          <span className="mr-1.5 rounded bg-chip px-1.5 py-px text-[11px] font-semibold text-ink-faint">원문</span>
          {row.title}
        </span>
      ) : (
        <span className="text-sm text-ink">{row.summary_what}</span>
      )}
      <span className="flex flex-wrap items-center gap-3.5 text-[13px] text-ink-muted tabular-nums">
        <span aria-hidden="true" className="h-1.5 min-w-[120px] flex-1 overflow-hidden rounded-full bg-line">
          <span className="block h-full rounded-full bg-ink-muted" style={{ width: `${Math.min(100, row.score)}%` }} />
        </span>
        <span>
          점수 <b className="text-ink">{row.score}</b>
        </span>
        <span>
          EPSS <b className="text-ink">{formatEpss(row.epss)}</b>
          {topPercent === null ? null : <span className="text-ink-faint"> ({topPercent})</span>}
        </span>
        <span>
          CVSS <b className="text-ink">{formatCvss(row.cvss, row.cvss_version)}</b>
        </span>
      </span>
    </button>
  )
}
