import { useIsDesktop } from '../../hooks/use-media-query.ts'
import { useSelectCve } from '../../hooks/use-select-cve.ts'
import { defaultSelectedId } from '../../lib/list-model.ts'
import { TREND_SECTION_ID } from '../../lib/routes.ts'
import { dueInDays, nextDueRow, overdueCount, productName, URGENT_WITHIN_DAYS, urgentRows } from '../../lib/urgency.ts'
import type { TrendRow } from '../../types/trend.ts'
import { Diamond, UrgentBadge } from '../signal/UrgentBadge.tsx'

const SHOWN = 3
const TITLE_ID = 'kpi-urgent'

const dueLabel = (due: number) => (due === 0 ? '오늘' : `${due}일 남음`)

const EmptyMessage = ({ rows, today }: { rows: TrendRow[]; today: string }) => {
  if (rows.some((row) => row.is_kev) === false) {
    return <>주목 목록에 CISA KEV 가 없습니다. 기한이 있는 취약점이 없으니 아래 목록의 점수 순서대로 보면 됩니다.</>
  }
  const next = nextDueRow(rows, today)
  if (next === null) return <>{URGENT_WITHIN_DAYS}일 안에 끝나는 CISA 기한은 없습니다.</>
  return (
    <>
      {URGENT_WITHIN_DAYS}일 안에 끝나는 CISA 기한은 없습니다. 가장 가까운 기한은 <b>D-{dueInDays(next, today)}</b>{' '}
      {productName(next)} {next.id} 입니다.
    </>
  )
}

// 목록이 보이게 내린다. 움직임 줄이기를 고른 방문자에게는 바로 옮긴다
const scrollToList = () => {
  const list = document.getElementById(TREND_SECTION_ID)
  if (list === null) return
  list.scrollIntoView?.({
    behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
    block: 'start',
  })
}

// 히어로 = "CISA 조치 기한 임박 N건" (design/v6 CONSENSUS 3절). 0건이면 다음 기한을 알린다
export const UrgentHeroCard = ({
  rows,
  today,
  selectedId,
}: {
  rows: TrendRow[]
  today: string
  selectedId: string | null
}) => {
  const select = useSelectCve()
  const isDesktop = useIsDesktop()
  const urgent = urgentRows(rows, today)
  const kevCount = rows.filter((row) => row.is_kev).length
  const currentId = isDesktop ? (selectedId ?? defaultSelectedId(rows, today)) : null

  const choose = (cveId: string) => {
    select(cveId)
    if (isDesktop) scrollToList()
  }

  return (
    <article
      aria-labelledby={TITLE_ID}
      className="col-span-full grid min-w-0 gap-x-6 gap-y-2 rounded-[10px] border border-l-4 border-border border-l-ink bg-surface p-5 min-[641px]:grid-cols-[minmax(150px,auto)_minmax(0,1fr)] min-[901px]:col-span-1"
    >
      <div className="flex flex-col gap-1.5">
        <h2 id={TITLE_ID} className="text-sm font-semibold text-ink-2">
          CISA 조치 기한 임박
        </h2>
        <div className="flex items-center gap-2.5 text-5xl leading-[1.05] font-bold tracking-tight text-ink tabular-nums">
          {urgent.length > 0 ? <Diamond size="lg" /> : null}
          <span>{urgent.length}</span>
          <span className="-ml-1.5 text-xl font-semibold text-ink-2">건</span>
        </div>
        <p className="mt-0.5 max-w-[26ch] text-[13px] leading-[1.45] text-ink-2">
          CISA 가 정한 KEV 조치 기한이 {URGENT_WITHIN_DAYS}일 안에 끝나는 취약점.
          {kevCount > 0
            ? ` 주목 ${rows.length}건 중 KEV ${kevCount}건, 그중 기한 지남 ${overdueCount(rows, today)}건.`
            : ''}
        </p>
      </div>
      <div>
        {urgent.length === 0 ? (
          <p className="pt-1 text-sm leading-normal text-ink-2">
            <EmptyMessage rows={rows} today={today} />
          </p>
        ) : (
          <>
            <ul aria-label="기한이 가까운 순서" className="flex flex-col gap-0.5">
              {urgent.slice(0, SHOWN).map((row) => {
                const due = dueInDays(row, today) ?? 0
                const name = productName(row)
                return (
                  <li key={row.id}>
                    <button
                      type="button"
                      aria-current={row.id === currentId}
                      aria-label={`${name} ${row.id}, CISA 기한 ${dueLabel(due)}, 점수 ${row.score}${row.kev_ransomware ? ', 랜섬웨어 악용' : ''}`}
                      onClick={() => choose(row.id)}
                      className="grid w-full grid-cols-[auto_minmax(0,1fr)_auto] items-center gap-2.5 rounded-lg border border-transparent px-2.5 py-1.5 text-left text-ink transition-colors hover:bg-surface-sunk focus-visible:outline-2 focus-visible:outline-ink aria-[current=true]:border-ink aria-[current=true]:bg-surface-sunk"
                    >
                      <UrgentBadge dueInDays={due} />
                      <span className="flex min-w-0 flex-col gap-px">
                        <span
                          title={name}
                          className="line-clamp-2 text-sm leading-[1.3] font-semibold [overflow-wrap:anywhere]"
                        >
                          {name}
                        </span>
                        <span className="font-mono text-xs whitespace-nowrap text-ink-2">{row.id}</span>
                      </span>
                      <span className="text-right text-xs whitespace-nowrap text-ink-3">
                        점수 <b className="text-sm font-semibold text-ink">{row.score}</b>
                      </span>
                    </button>
                  </li>
                )
              })}
            </ul>
            {urgent.length > SHOWN ? (
              <p className="px-2.5 pt-1 text-xs text-ink-3">외 {urgent.length - SHOWN}건은 아래 목록 맨 위에</p>
            ) : null}
          </>
        )}
      </div>
    </article>
  )
}
