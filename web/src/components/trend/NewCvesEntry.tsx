import { Link } from 'react-router'
import { useStats } from '../../hooks/use-data.ts'
import { formatCount } from '../../lib/kpi.ts'

// 주목 목록 아래 "최근 7일 새 CVE n건 모두 보기 →" 입구. 건수는 stats(이미 받은 것)로 세어 recent.json 을 받지 않는다
export const NewCvesEntry = () => {
  const stats = useStats()
  if (stats.status !== 'ready') return null
  const total = Object.values(stats.data.severity_7d).reduce((sum, count) => sum + count, 0)
  return (
    <Link
      to="/new"
      className="flex items-center justify-between gap-3 rounded-[10px] border border-border bg-surface px-5 py-4 text-[15px] font-semibold text-ink hover:border-ink-3"
    >
      <span>{`최근 7일 새 CVE ${formatCount(total)}건 모두 보기 →`}</span>
    </Link>
  )
}
