import { formatEpssPercent, formatEpssRank, isNewKev } from '../../lib/list-model.ts'
import { severityLabel } from '../../lib/severity.ts'
import { dueInDays, kevAddedDaysAgo, productName, URGENT_WITHIN_DAYS } from '../../lib/urgency.ts'
import type { TrendRow } from '../../types/trend.ts'
import { DueLabel, KevBadge, NewKevBadge, PocBadge, RansomBadge } from '../signal/SignalBadges.tsx'

interface WatchRowProps {
  row: TrendRow
  rank: number
  today: string
  selected: boolean
  onSelect: (cveId: string) => void
}

const dueSpeech = (row: TrendRow, today: string): string => {
  const due = dueInDays(row, today)
  if (due === null) return ''
  if (due < 0) return `, CISA 기한 ${-due}일 지남`
  return `, CISA 기한 D-${due}${due <= URGENT_WITHIN_DAYS ? ' 임박' : ''}`
}

// 행 하나를 화면 읽기 프로그램에 한 문장으로 들려준다. 안쪽 글자는 그림과 같은 내용이라 숨긴다
const speechOf = (row: TrendRow, rank: number, today: string): string => {
  const kev = isNewKev(row, today) ? ', 새 KEV' : row.is_kev ? ', KEV' : ''
  return `${rank}위 ${row.id}, 점수 ${row.score}점, ${productName(row)}${dueSpeech(row, today)}${row.kev_ransomware ? ', 랜섬웨어' : ''}${kev}`
}

const Facts = ({ row, today }: { row: TrendRow; today: string }) => {
  const addedAgo = kevAddedDaysAgo(row, today)
  return (
    <span className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-ink-3">
      <span>
        {severityLabel(row.severity)}
        {row.cvss === null ? null : (
          <>
            {' · CVSS '}
            <b className="font-semibold text-ink tabular-nums">{row.cvss.toFixed(1)}</b>
          </>
        )}
      </span>
      {row.epss === null ? (
        <span>EPSS 없음</span>
      ) : (
        <span>
          EPSS <b className="font-semibold text-ink tabular-nums">{formatEpssPercent(row.epss)}</b>
          {row.epss_percentile === null ? null : ` (${formatEpssRank(row.epss_percentile)})`}
        </span>
      )}
      {row.is_kev === false ? <span>KEV 아님</span> : null}
      {row.is_kev && addedAgo !== null ? (
        <span>
          CISA 등재 <b className="font-semibold text-ink">{addedAgo}일 전</b>
        </span>
      ) : null}
    </span>
  )
}

// 목록 행 (design/v6 c-list .vtc-row): 순위·점수 | ID·배지·제품·CISA 기한 / 요약 / 사실 칩. why_now 는 상세에만 (CONSENSUS 3절)
export const WatchRow = ({ row, rank, today, selected, onSelect }: WatchRowProps) => {
  const addedAgo = kevAddedDaysAgo(row, today)
  return (
    <button
      type="button"
      data-id={row.id}
      aria-current={selected}
      aria-label={speechOf(row, rank, today)}
      onClick={() => onSelect(row.id)}
      className="grid w-full grid-cols-[60px_minmax(0,1fr)] items-start gap-3.5 rounded-[10px] border border-border bg-surface py-3.5 pr-4 pl-3 text-left transition-colors hover:border-axis focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink aria-[current=true]:border-2 aria-[current=true]:border-ink aria-[current=true]:py-[13px] aria-[current=true]:pr-[15px] aria-[current=true]:pl-[11px] aria-[current=true]:shadow-[0_2px_8px_rgba(22,17,58,.10)]"
    >
      <span aria-hidden="true" className="flex flex-col items-start gap-1">
        <span className="text-xs font-semibold text-ink-3">{rank}위</span>
        <span className="text-[26px] leading-none font-extrabold tracking-tight text-ink">
          {row.score}
          <small className="ml-0.5 text-xs font-semibold text-ink-3">점</small>
        </span>
        <span className="h-1 w-11 overflow-hidden rounded-sm bg-border">
          <i className="block h-full bg-ink" style={{ width: `${Math.max(0, Math.min(100, row.score))}%` }} />
        </span>
      </span>
      <span aria-hidden="true" className="flex min-w-0 flex-col gap-1.5">
        <span className="flex min-w-0 flex-wrap items-center gap-1.5">
          <span className="mr-0.5 font-mono text-sm font-semibold text-ink">{row.id}</span>
          {row.kev_ransomware ? <RansomBadge /> : null}
          {isNewKev(row, today) && addedAgo !== null ? (
            <NewKevBadge daysAgo={addedAgo} />
          ) : row.is_kev ? (
            <KevBadge />
          ) : null}
          {row.poc_count > 0 ? <PocBadge count={row.poc_count} /> : null}
          <span
            title={productName(row)}
            className={`max-w-[220px] truncate rounded-full bg-surface-sunk px-2 py-0.5 text-xs text-ink-2 ${row.primary_product === null ? 'italic' : ''}`}
          >
            {productName(row)}
          </span>
          <span className="ml-auto">
            <DueLabel row={row} today={today} />
          </span>
        </span>
        <span className="text-sm leading-normal text-ink-2 [overflow-wrap:anywhere]">
          {row.summary_what ?? row.title}
        </span>
        <Facts row={row} today={today} />
      </span>
    </button>
  )
}
