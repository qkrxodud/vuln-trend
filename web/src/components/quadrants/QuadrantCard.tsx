import { useMeta, useTrendIndex } from '../../hooks/use-data.ts'
import { useListFilter } from '../../hooks/use-list-filter.ts'
import {
  QUADRANT_NAME,
  QUADRANT_ORDER,
  quadrantGrid,
  shortCveId,
  type Quadrant,
  type QuadrantCell,
} from '../../lib/quadrants.ts'
import { dueInDays, isUrgent, productName, referenceDate, URGENT_WITHIN_DAYS } from '../../lib/urgency.ts'
import type { TrendRow } from '../../types/trend.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { Diamond } from '../signal/UrgentBadge.tsx'

const AXIS = 'text-xs font-semibold text-ink-2'

// 대표 한 줄: 제품 + 번호 끝자리 + CISA 기한. 기한 임박만 빨강 ◆
const Representative = ({ row, today }: { row: TrendRow; today: string }) => {
  const due = dueInDays(row, today)
  return (
    <li className="flex min-w-0 flex-wrap items-baseline gap-x-1.5 gap-y-0.5 text-[13px] leading-snug">
      <span className="min-w-0 [overflow-wrap:anywhere]">
        <b className="font-semibold text-ink">{productName(row)}</b>{' '}
        <span className="font-mono text-xs text-ink-2">{shortCveId(row.id)}</span>
      </span>
      {due === null ? null : isUrgent(row, today) ? (
        <span className="inline-flex shrink-0 items-center gap-1 rounded-full bg-urgent-wash px-1.5 text-xs font-bold whitespace-nowrap text-urgent-ink">
          <Diamond />
          D-{due}
        </span>
      ) : (
        <span className="shrink-0 text-xs whitespace-nowrap text-ink-3">{due < 0 ? `${-due}일 지남` : `D-${due}`}</span>
      )}
    </li>
  )
}

const Cell = ({ cell, today }: { cell: QuadrantCell; today: string }) => {
  const { filter, setQuadrant } = useListFilter()
  const pressed = filter.quadrant === cell.key
  const first = cell.key === 'hot'
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={`${QUADRANT_NAME[cell.key]} ${cell.count}건, ${pressed ? '목록 필터 적용 중 — 누르면 해제' : '누르면 목록을 이 칸으로 좁힘'}`}
      onClick={() => setQuadrant(pressed ? null : cell.key)}
      className={`flex min-w-0 flex-col gap-2 rounded-lg border p-3 text-left transition-colors hover:border-ink-3 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ink aria-pressed:border-ink aria-pressed:shadow-[inset_0_0_0_1px_var(--color-ink)] ${
        first ? 'border-ink-2 bg-surface-sunk' : 'border-border bg-surface'
      }`}
    >
      <span className="flex items-baseline justify-between gap-2">
        <span className="text-[28px] leading-none font-extrabold tracking-tight text-ink tabular-nums">
          {cell.count}
          <small className="ml-0.5 text-xs font-semibold text-ink-3">건</small>
        </span>
        {first ? <span className="rounded-full bg-ink px-2 py-0.5 text-xs font-semibold text-white">먼저</span> : null}
      </span>
      {cell.representatives.length === 0 ? null : (
        <ul className="m-0 flex list-none flex-col gap-1 p-0">
          {cell.representatives.map((row) => (
            <Representative key={row.id} row={row} today={today} />
          ))}
        </ul>
      )}
      {cell.more > 0 ? <span className="text-xs text-ink-3">그 외 {cell.more}건</span> : null}
      <span className="mt-auto text-xs text-ink-3">{pressed ? '해제 ✕' : '목록 보기 ›'}</span>
    </button>
  )
}

const Grid = ({ rows, today }: { rows: TrendRow[]; today: string }) => {
  const grid = quadrantGrid(rows, today)
  const kevCount = rows.filter((row) => row.is_kev).length
  const cellOf = (key: Quadrant) => <Cell key={key} cell={grid.cells[key]} today={today} />
  return (
    <>
      <div>
        <h2 id="quadrant-title" className="m-0 text-lg leading-[1.3] font-bold text-ink">
          어느 쪽이 더 위험한가
        </h2>
        <p className="mt-1 mb-0 text-[13px] text-ink-3">
          {rows.length === 0
            ? '표시할 취약점이 없습니다'
            : `빨강 ◆(CISA 기한 ${URGENT_WITHIN_DAYS}일 안)은 칸과 상관없이 먼저 · 칸을 누르면 아래 목록을 그 칸으로 좁힙니다 · 주목 ${rows.length}건 중 KEV ${kevCount}건`}
        </p>
      </div>
      {rows.length === 0 ? null : (
        <div className="flex flex-col gap-1.5">
          <div aria-hidden="true" className="grid grid-cols-2 gap-2">
            <span className={AXIS}>악용 가능성 낮음</span>
            <span className={AXIS}>악용 가능성 높음 (EPSS 30% 이상)</span>
          </div>
          <div role="group" aria-label="4칸 건수표" className="grid grid-cols-2 gap-2">
            {/* 행 이름은 칸 줄 위에 가로로 (세로 글자는 한국어가 옆으로 누워 읽기 어려움) */}
            <span aria-hidden="true" className={`${AXIS} col-span-2 mt-1`}>
              심각 (CVSS 7.0 이상)
            </span>
            {QUADRANT_ORDER.slice(0, 2).map(cellOf)}
            <span aria-hidden="true" className={`${AXIS} col-span-2 mt-1`}>
              덜 심각
            </span>
            {QUADRANT_ORDER.slice(2).map(cellOf)}
          </div>
        </div>
      )}
      {grid.unscored === 0 ? null : (
        <p className="m-0 text-xs text-ink-2">점수 없음 {grid.unscored}건 (EPSS·CVSS 없음, 목록에는 있음)</p>
      )}
    </>
  )
}

// "어느 쪽이 더 위험한가" 4칸 건수표 (사용자 피드백 2026-11-01: 산점도 대신).
// 열 = 악용 가능성(EPSS 30%), 행 = 심각도(CVSS 7.0). 칸을 누르면 목록 필터(칸 조건)를 건다
export const QuadrantCard = () => {
  const meta = useMeta()
  const index = useTrendIndex()
  return (
    <section
      aria-labelledby="quadrant-title"
      className="flex min-w-0 flex-col gap-3 rounded-[10px] border border-border bg-surface p-5"
    >
      <LoadableView loadable={meta} label="4칸 건수표">
        {(metaData) => (
          <LoadableView loadable={index} label="4칸 건수표">
            {(rows) => <Grid rows={rows} today={referenceDate(metaData.updated_at)} />}
          </LoadableView>
        )}
      </LoadableView>
    </section>
  )
}
