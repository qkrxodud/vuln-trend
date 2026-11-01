import { NavLink } from 'react-router'
import { useMeta } from '../../hooks/use-data.ts'
import { useMineNews } from '../../hooks/use-mine-news.ts'
import { useNow } from '../../hooks/use-now.ts'
import { isStale } from '../../lib/kst-time.ts'
import { SearchBox } from './SearchBox.tsx'
import { StaleBanner } from './StaleBanner.tsx'
import { UpdatedAt } from './UpdatedAt.tsx'

// 머리글 (design/v6 a-top): 로고 · 한 줄 소개 · 검색 · 수집 상태 줄, 그 아래 오래된 데이터 안내.
// 검색창은 v6 시안에 없지만 남긴다(사용자 결정 Q2, 2026-11-01). 목록 필터는 목록 쪽에만 둔다
// meta 를 못 받았을 때의 오류 안내는 본문 영역이 보이므로 머리글은 상태 줄만 비운다
// 두 화면: 주목 목록(/) 과 최근 7일 새 CVE(/new). 지금 화면에 aria-current="page" (NavLink)
const NAV =
  'rounded-full px-3 py-1 font-semibold text-ink-2 hover:text-ink aria-[current=page]:bg-ink aria-[current=page]:text-white'

// "내 제품" 탭: 마지막 방문 뒤 새로 생긴 내 제품 취약점 수를 배지로. 배지는 탭 모서리에 겹쳐 띄워 생기고 없어져도 줄이 밀리지 않는다
const MineTab = () => {
  const count = useMineNews()
  const fresh = typeof count === 'number' && count > 0 ? count : null
  return (
    <NavLink to="/mine" className={`relative ${NAV}`} aria-label={fresh === null ? '내 제품' : `내 제품 새 ${fresh}건`}>
      내 제품
      {fresh === null ? null : (
        <span
          aria-hidden="true"
          className="absolute -top-2 left-full -translate-x-3 rounded-full bg-ink-2 px-1.5 text-[11px] leading-4 font-semibold text-white tabular-nums"
        >
          {fresh}
        </span>
      )}
    </NavLink>
  )
}

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
      <nav aria-label="화면" className="flex flex-wrap gap-1 text-sm">
        <NavLink to="/" end className={NAV}>
          지금 주목할 취약점
        </NavLink>
        <NavLink to="/new" className={NAV}>
          최근 7일 새 CVE
        </NavLink>
        <MineTab />
      </nav>
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
