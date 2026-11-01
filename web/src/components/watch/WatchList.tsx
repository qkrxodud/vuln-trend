import { useState } from 'react'
import { REST_PAGE, scoreRanks, SIGNALS, URGENT_PAGE, type ListTiers } from '../../lib/list-model.ts'
import { URGENT_WITHIN_DAYS } from '../../lib/urgency.ts'
import type { TrendRow } from '../../types/trend.ts'
import { Diamond } from '../signal/UrgentBadge.tsx'
import { WatchRow } from './WatchRow.tsx'

interface WatchListProps {
  tiers: ListTiers
  /** 순위는 주목 목록 전체 기준 */
  allRows: TrendRow[]
  signal: (typeof SIGNALS)[number]['key']
  today: string
  selectedId: string | null
  onSelect: (cveId: string) => void
}

const SHOW_MORE =
  'self-stretch rounded-[10px] border border-dashed border-axis bg-surface p-2.5 text-[13px] font-semibold text-ink hover:border-ink-3'

const TierHeading = ({ children, note }: { children: React.ReactNode; note: string }) => (
  <h3 className="m-0 flex flex-wrap items-center gap-2 text-[15px] font-bold text-ink">
    {children}
    <span className="text-xs font-medium text-ink-2">{note}</span>
  </h3>
)

// 묶음 둘: 기한 임박 고정(10건 + 더 보기) / 나머지 점수순(20건씩 더 보기).
// 필터가 바뀌면 부모가 key 를 바꿔 더 보기 상태를 처음으로 되돌린다
export const WatchList = ({ tiers, allRows, signal, today, selectedId, onSelect }: WatchListProps) => {
  const [urgentAll, setUrgentAll] = useState(false)
  const [restShown, setRestShown] = useState(REST_PAGE)
  const ranks = scoreRanks(allRows)
  const row = (item: TrendRow) => (
    <WatchRow
      key={item.id}
      row={item}
      rank={ranks.get(item.id) ?? 0}
      today={today}
      selected={item.id === selectedId}
      onSelect={onSelect}
    />
  )

  // 고른 행(산점도·KPI 에서 고름)이 더 보기 뒤에 가려져 있으면 그 행까지 펼친다 (design/v6 c-list select)
  const selectedUrgent = tiers.urgent.findIndex((item) => item.id === selectedId)
  const selectedRest = tiers.rest.findIndex((item) => item.id === selectedId)
  const urgentShown = urgentAll || selectedUrgent >= URGENT_PAGE ? tiers.urgent : tiers.urgent.slice(0, URGENT_PAGE)
  const restLimit = Math.max(restShown, Math.ceil((selectedRest + 1) / REST_PAGE) * REST_PAGE)
  const restVisible = tiers.rest.slice(0, restLimit)
  const restName =
    tiers.urgent.length > 0 ? '나머지' : signal === 'all' ? '목록' : SIGNALS.find((s) => s.key === signal)?.label

  return (
    <div className="flex min-w-0 flex-col gap-[18px]">
      {tiers.urgent.length === 0 ? null : (
        <section aria-label={`CISA 기한 임박 ${tiers.urgent.length}건`} className="flex flex-col gap-2">
          <TierHeading note={`오늘부터 ${URGENT_WITHIN_DAYS}일 안에 기한이 끝남 · 기한 빠른 순`}>
            <Diamond />
            CISA 기한 임박 {tiers.urgent.length}건
          </TierHeading>
          {urgentShown.map(row)}
          {tiers.urgent.length > urgentShown.length ? (
            <button type="button" className={SHOW_MORE} onClick={() => setUrgentAll(true)}>
              기한 임박 {tiers.urgent.length - urgentShown.length}건 더 보기
            </button>
          ) : null}
        </section>
      )}
      {tiers.rest.length === 0 ? null : (
        <section aria-label={`${restName} ${tiers.rest.length}건`} className="flex flex-col gap-2">
          <TierHeading note="점수 높은 순">
            {restName} {tiers.rest.length}건
          </TierHeading>
          {restVisible.map(row)}
          {tiers.rest.length > restVisible.length ? (
            <button type="button" className={SHOW_MORE} onClick={() => setRestShown(restLimit + REST_PAGE)}>
              {Math.min(REST_PAGE, tiers.rest.length - restVisible.length)}건 더 보기{' '}
              <span className="text-xs font-normal text-ink-3 tabular-nums">
                ({restVisible.length}/{tiers.rest.length})
              </span>
            </button>
          ) : null}
        </section>
      )}
    </div>
  )
}
