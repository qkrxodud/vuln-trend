import { TREND_FILTERS } from '../../lib/trend-filter.ts'
import { useTrendFilter } from '../../hooks/use-trend-filter.ts'

// 전체 / KEV / Critical. 목록 옆 필터도 이 컴포넌트를 쓴다 (R1)
export const TrendFilterTabs = () => {
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
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
