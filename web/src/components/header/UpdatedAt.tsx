import { formatKstDateTime, formatTimeAgo } from '../../lib/kst-time.ts'

// 수집 상태 줄: "✓ 수집 완료 · 마지막 갱신 2026-11-01 18:49 KST (4분 전) · 하루 1회 갱신 · 출처 …".
// 36시간을 넘으면 "수집 완료"라고 쓰지 않는다 (배너가 이유를 설명). 펄스·초 카운터 없음 (D5)
export const UpdatedAt = ({ updatedAt, now, stale }: { updatedAt: string; now: Date; stale: boolean }) => (
  <p className="flex flex-wrap items-center gap-x-2.5 gap-y-1 text-[13px] text-ink-2">
    {stale ? (
      <span className="font-semibold text-ink">갱신 지연</span>
    ) : (
      <span className="inline-flex items-center gap-1 font-semibold text-ink">
        <span aria-hidden="true" className="font-bold text-ok">
          ✓
        </span>
        <span>수집 완료</span>
      </span>
    )}
    <span className="whitespace-nowrap">
      마지막 갱신{' '}
      <time dateTime={updatedAt} className="font-semibold text-ink tabular-nums">
        {formatKstDateTime(updatedAt)}
      </time>{' '}
      <span>({formatTimeAgo(updatedAt, now)})</span>
    </span>
    <span>· 하루 1회 갱신 · 출처 NVD · CISA KEV · EPSS</span>
  </p>
)
