import { formatKstShort, formatTimeAgo } from '../../lib/kst-time.ts'

// "마지막 갱신 10-30 09:00 (3시간 전)". 펄스·초 카운터 없음 (D5)
export const UpdatedAt = ({ updatedAt, now }: { updatedAt: string; now: Date }) => (
  <p className="flex items-center gap-1.5 text-sm whitespace-nowrap text-ink-muted">
    <span>마지막 갱신</span>
    <time dateTime={updatedAt} className="font-semibold text-ink tabular-nums">
      {formatKstShort(updatedAt)}
    </time>
    <span>({formatTimeAgo(updatedAt, now)})</span>
  </p>
)
