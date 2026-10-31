import { TREND_FILTERS, type TrendFilter } from '../../lib/trend-filter.ts'
import { useTrendFilter } from '../../hooks/use-trend-filter.ts'

// 전체 / KEV / Critical. 헤더와 목록 옆에서 같은 상태를 쓴다 (R1). 목록 옆은 건수를 함께 적는다
export const TrendFilterTabs = ({ counts }: { counts?: Record<TrendFilter, number> }) => {
  const { filter, setFilter } = useTrendFilter()
  return (
    <div role="group" aria-label="취약점 필터" className="inline-flex gap-1 self-start rounded-full bg-chip p-1">
      {TREND_FILTERS.map((option) => {
        const pressed = option.value === filter
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={pressed}
            onClick={() => setFilter(option.value)}
            className={
              pressed
                ? 'rounded-full bg-white px-3.5 py-1.5 text-[13px] font-semibold text-ink shadow-sm'
                : 'rounded-full px-3.5 py-1.5 text-[13px] text-ink-soft hover:text-ink'
            }
          >
            {counts === undefined ? option.label : `${option.label} ${counts[option.value]}`}
          </button>
        )
      })}
    </div>
  )
}
