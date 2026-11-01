import { formatCount, todayNewSummary } from '../../lib/kpi.ts'
import type { Meta } from '../../types/meta.ts'
import type { DailyCount } from '../../types/stats.ts'
import { KpiArticle, KpiValue } from './KpiCard.parts.tsx'
import { Sparkline } from './Sparkline.tsx'

// "오늘 새로 나온 CVE": 큰 숫자는 meta 24시간 신규, 오늘 막대는 집계 중이라 평소 계산에서 뺀다 (design/v6 T1·T2)
export const TodayNewCard = ({ meta, daily }: { meta: Meta; daily: DailyCount[] }) => {
  const summary = todayNewSummary(meta, daily)
  return (
    <KpiArticle id="kpi-today-new" title="오늘 새로 나온 CVE">
      <div className="flex items-end justify-between gap-3">
        <div className="flex min-w-0 flex-col gap-1">
          <KpiValue value={formatCount(summary.today)}>
            {summary.partial ? (
              <span className="ml-1.5 rounded bg-surface-sunk px-1.5 py-0.5 align-middle text-xs font-semibold tracking-normal text-ink-2">
                집계 중
              </span>
            ) : null}
          </KpiValue>
          <p className="text-[13px] leading-[1.45] text-ink-2">
            {summary.asOf} KST 기준 · 어제 하루{' '}
            <b className="font-semibold text-ink">{formatCount(summary.yesterday)}</b>건
            {summary.usual === null ? null : (
              <>
                <br />
                평소({summary.usualDays}일 중앙값) {formatCount(summary.usual)}건
              </>
            )}
          </p>
        </div>
        {summary.series.length < 2 ? null : (
          <div className="flex flex-col items-end">
            <Sparkline values={summary.series} partial={summary.partial} />
            <p className="max-w-28 text-right text-xs leading-[1.35] text-ink-3">
              {summary.series.length}일 · 가로선 평소
              {summary.partial ? (
                <>
                  <br />
                  점선 = 오늘(집계 중)
                </>
              ) : null}
            </p>
          </div>
        )}
      </div>
    </KpiArticle>
  )
}
