import { useState } from 'react'
import { useTrendFilter } from '../../hooks/use-trend-filter.ts'
import { matchesTrendFilter, type TrendFilter } from '../../lib/trend-filter.ts'
import { countByFilter, groupByProduct, PAGE_SIZE, type TrendView } from '../../lib/trend-list.ts'
import type { TrendRow } from '../../types/trend.ts'
import { TrendFilterTabs } from '../header/TrendFilterTabs.tsx'
import { ProductGroupSection } from './ProductGroupSection.tsx'
import { TrendRowCard } from './TrendRowCard.tsx'

interface TrendListProps {
  /** index.json: 점수 내림차순 */
  rows: TrendRow[]
  selectedId: string | null
  onSelect: (cveId: string) => void
}

const VIEWS: { value: TrendView; label: string }[] = [
  { value: 'product', label: '소프트웨어별' },
  { value: 'score', label: '점수순' },
]

const SEGMENT_PRESSED = 'rounded-full bg-white px-3.5 py-1.5 text-[13px] font-semibold text-ink shadow-sm'
const SEGMENT_IDLE = 'rounded-full px-3.5 py-1.5 text-[13px] text-ink-soft hover:text-ink'

// 지금 주목할 취약점: 상위 20건 + 더 보기, 소프트웨어별/점수순, 공유 필터 (WBS 5.6)
export const TrendList = ({ rows, selectedId, onSelect }: TrendListProps) => {
  const { filter } = useTrendFilter()
  const [view, setView] = useState<TrendView>('product')
  // "더 보기"는 그 필터에서만 유지한다. 필터가 바뀌면 다시 20건부터
  const [expandedFor, setExpandedFor] = useState<TrendFilter | null>(null)
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(new Set())

  const filtered = rows.filter((row) => matchesTrendFilter(row, filter))
  const showAll = expandedFor === filter
  const shown = showAll ? filtered : filtered.slice(0, PAGE_SIZE)
  const hiddenCount = filtered.length - shown.length
  const subtitle = `${view === 'product' ? '소프트웨어별 묶음 · 그룹은 최고 점수순' : '트렌드 점수순'} · 상위 ${shown.length}건 표시`

  const toggleGroup = (name: string) =>
    setCollapsed((previous) => {
      const next = new Set(previous)
      if (next.has(name)) next.delete(name)
      else next.add(name)
      return next
    })

  return (
    <section aria-labelledby="trend-list-title" className="flex flex-col gap-3.5">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 id="trend-list-title" className="text-[17px] font-semibold">
            지금 주목할 취약점
          </h2>
          <p className="text-[13px] text-ink-muted">{subtitle}</p>
        </div>
        <div className="ml-auto flex flex-wrap gap-2.5">
          <div role="group" aria-label="보기 전환" className="inline-flex gap-1 rounded-full bg-chip p-1">
            {VIEWS.map((option) => (
              <button
                key={option.value}
                type="button"
                aria-pressed={option.value === view}
                onClick={() => setView(option.value)}
                className={option.value === view ? SEGMENT_PRESSED : SEGMENT_IDLE}
              >
                {option.label}
              </button>
            ))}
          </div>
          <TrendFilterTabs counts={countByFilter(rows)} />
        </div>
      </div>

      <div className="flex flex-col gap-2.5">
        {shown.length === 0 ? (
          <p className="rounded-[14px] border border-dashed border-line p-7 text-center text-sm text-ink-muted">
            조건에 맞는 취약점이 없습니다. 검색어나 필터를 바꿔 보세요.
          </p>
        ) : null}
        {view === 'product'
          ? groupByProduct(shown).map((group) => (
              <ProductGroupSection
                key={group.name}
                group={group}
                open={collapsed.has(group.name) === false}
                onToggle={() => toggleGroup(group.name)}
                selectedId={selectedId}
                onSelect={onSelect}
              />
            ))
          : shown.map((row) => (
              <TrendRowCard key={row.id} row={row} selected={row.id === selectedId} onSelect={onSelect} />
            ))}
        {hiddenCount > 0 ? (
          <button
            type="button"
            onClick={() => setExpandedFor(filter)}
            className="rounded-xl border-[1.5px] border-[#d9e4df] bg-white p-3 text-sm font-semibold text-accent hover:bg-accent-soft"
          >
            더 보기 ({hiddenCount}건 더)
          </button>
        ) : null}
      </div>
    </section>
  )
}
