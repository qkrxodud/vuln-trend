import { useMeta } from '../../hooks/use-data.ts'
import { useNow } from '../../hooks/use-now.ts'
import { isStale } from '../../lib/kst-time.ts'
import { SearchBox } from './SearchBox.tsx'
import { StaleBanner } from './StaleBanner.tsx'
import { TrendFilterTabs } from './TrendFilterTabs.tsx'
import { UpdatedAt } from './UpdatedAt.tsx'

// 머리글: 로고 · 검색 · 마지막 갱신, 그 아래 필터와 오래된 데이터 안내 (기획서 8장, WBS 5.3)
// meta 를 못 받았을 때의 오류 안내는 본문 영역이 보이므로 머리글은 갱신 시각 자리만 비운다
/** showFilter: 목록이 있는 메인에서만 필터를 보인다. 상세 페이지는 끈다 */
export const SiteHeader = ({ showFilter = true }: { showFilter?: boolean }) => {
  const meta = useMeta()
  const now = useNow()
  const updatedAt = meta.status === 'ready' ? meta.data.updated_at : null

  return (
    <header className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3.5">
        <a href={import.meta.env.BASE_URL} className="flex items-center gap-2.5 text-ink no-underline">
          <span
            aria-hidden="true"
            className="grid size-7 place-items-center rounded-lg bg-brand text-[15px] font-bold text-white"
          >
            V
          </span>
          <span className="text-2xl font-bold tracking-tight">VulnTrend</span>
        </a>
        <SearchBox />
        {updatedAt === null ? null : <UpdatedAt updatedAt={updatedAt} now={now} />}
      </div>
      {showFilter ? <TrendFilterTabs /> : null}
      {updatedAt !== null && isStale(updatedAt, now) ? <StaleBanner /> : null}
    </header>
  )
}
