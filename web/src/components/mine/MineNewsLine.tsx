import { Link } from 'react-router'
import { useMineNews } from '../../hooks/use-mine-news.ts'

const BOX =
  'flex min-h-[54px] items-center justify-between gap-3 rounded-[10px] border border-border bg-surface px-5 py-3 text-[15px]'

// 메인 KPI 아래 "내 제품 새 취약점 n건" 한 줄. 내 제품이 없으면 숨기고, 세는 동안에도 같은 높이를 잡아 아래가 밀리지 않게 한다
export const MineNewsLine = () => {
  const count = useMineNews()
  if (count === null) return null
  if (count === undefined) {
    return (
      <p aria-hidden="true" className={`${BOX} m-0 text-ink-3`}>
        내 제품 새 취약점 확인 중…
      </p>
    )
  }
  return (
    <Link to="/mine" className={`${BOX} font-semibold text-ink hover:border-ink-3`}>
      {`내 제품 새 취약점 ${count}건 보기 →`}
    </Link>
  )
}
