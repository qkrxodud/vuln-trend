import { useMeta } from '../../hooks/use-data.ts'
import { useNow } from '../../hooks/use-now.ts'
import { isStale } from '../../lib/kst-time.ts'
import { SearchBox } from './SearchBox.tsx'
import { StaleBanner } from './StaleBanner.tsx'
import { UpdatedAt } from './UpdatedAt.tsx'

// 머리글 (design/v6 a-top): 로고 · 한 줄 소개 · 검색 · 수집 상태 줄, 그 아래 오래된 데이터 안내.
// 검색창은 v6 시안에 없지만 남긴다(사용자 결정 Q2, 2026-11-01). 목록 필터는 목록 쪽에만 둔다
// meta 를 못 받았을 때의 오류 안내는 본문 영역이 보이므로 머리글은 상태 줄만 비운다
export const SiteHeader = () => {
  const meta = useMeta()
  const now = useNow()
  const updatedAt = meta.status === 'ready' ? meta.data.updated_at : null

  return (
    <header className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-3">
        <a href={import.meta.env.BASE_URL} className="flex items-center gap-2.5 text-ink no-underline">
          <span
            aria-hidden="true"
            className="grid size-7 place-items-center rounded-lg bg-ink text-[15px] font-extrabold text-white"
          >
            V
          </span>
          <span className="text-[22px] font-extrabold tracking-tight">VulnTrend</span>
          <span className="hidden text-[13px] text-ink-3 sm:inline">오늘 고칠 취약점</span>
        </a>
        <SearchBox />
      </div>
      {/* meta 가 오기 전에도 상태 줄 높이(좁으면 두 줄)를 잡아 두어 아래가 밀리지 않게 한다 */}
      {updatedAt === null ? (
        <p aria-hidden="true" className="m-0 min-h-[42px] sm:min-h-[19px]" />
      ) : (
        <UpdatedAt updatedAt={updatedAt} now={now} stale={isStale(updatedAt, now)} />
      )}
      {updatedAt !== null && isStale(updatedAt, now) ? <StaleBanner /> : null}
    </header>
  )
}
