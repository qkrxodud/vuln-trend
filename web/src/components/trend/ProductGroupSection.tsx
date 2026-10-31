import type { ProductGroup } from '../../lib/trend-list.ts'
import { Badge } from './Badge.tsx'
import { TrendRowCard } from './TrendRowCard.tsx'

interface ProductGroupSectionProps {
  group: ProductGroup
  open: boolean
  onToggle: () => void
  selectedId: string | null
  onSelect: (cveId: string) => void
}

// 소프트웨어별 보기의 제품 묶음 하나. 머리를 눌러 접고 편다
export const ProductGroupSection = ({ group, open, onToggle, selectedId, onSelect }: ProductGroupSectionProps) => (
  <div className="mb-2 flex flex-col gap-2">
    <button
      type="button"
      aria-expanded={open}
      onClick={onToggle}
      className="flex flex-wrap items-center gap-2.5 rounded-xl bg-paper px-4 py-3 text-left hover:bg-chip focus-visible:outline-2 focus-visible:outline-accent"
    >
      <span className="text-base font-bold">{group.name}</span>
      <span className="shrink-0 text-[13px] whitespace-nowrap text-ink-muted">{group.rows.length}건</span>
      {group.kevCount > 0 ? <Badge tone="kev">KEV {group.kevCount}</Badge> : null}
      <span className="ml-auto text-[13px] text-ink-muted">
        최고 점수 <b className="text-ink">{group.topScore}</b>
      </span>
      <span className="w-9 text-right text-xs text-ink-faint">{open ? '접기' : '펼치기'}</span>
    </button>
    {open
      ? group.rows.map((row) => (
          <TrendRowCard key={row.id} row={row} selected={row.id === selectedId} onSelect={onSelect} />
        ))
      : null}
  </div>
)
