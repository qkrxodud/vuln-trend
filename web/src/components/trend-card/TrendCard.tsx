import { useState, type CSSProperties, type KeyboardEvent } from 'react'
import { useMeta, useStats } from '../../hooks/use-data.ts'
import { formatMonthDay } from '../../lib/charts.ts'
import { completeDays, lastDayIsPartial } from '../../lib/daily-window.ts'
import { formatCount } from '../../lib/kpi.ts'
import { formatKstTime } from '../../lib/kst-time.ts'
import {
  axisLabels,
  formatChange,
  severityParts,
  spikeNote,
  trendAnswer,
  trendPanel,
  type TrendPanel,
  type Verdict,
} from '../../lib/trend-stats.ts'
import type { Meta } from '../../types/meta.ts'
import type { SeverityDistribution, Stats } from '../../types/stats.ts'
import { LoadableView } from '../state/LoadableView.tsx'

const DAY_NAMES = '일월화수목금토'
const VERDICT_WORD: Record<Verdict, string> = { more: '많음', less: '적음', similar: '비슷' }
const CARD = 'flex min-w-0 flex-col gap-3.5 rounded-[10px] border border-border bg-surface p-5'

const dayName = (date: string) => DAY_NAMES[new Date(`${date}T00:00:00Z`).getUTCDay()]
const dayLabel = (date: string) => `${formatMonthDay(date)} (${dayName(date)})`

// 잘라 그린 막대 꼭대기의 지그재그 절단 표시 (design/v6 .bar.cut::after)
const ZIGZAG = 'polygon(0 30%,25% 0,50% 30%,75% 0,100% 30%,100% 70%,75% 40%,50% 70%,25% 40%,0 70%)'
// 집계 중인 오늘 막대 빗금 (tokens.css --partial)
const PARTIAL: CSSProperties = {
  background: 'repeating-linear-gradient(135deg, var(--color-neutral-mark) 0 2px, transparent 2px 5px)',
  boxShadow: 'inset 0 0 0 1.5px var(--color-ink-2)',
}

interface PanelSpec {
  name: string
  color: string
  height: number
}

const SPECS: Record<TrendPanel['key'], PanelSpec> = {
  new: { name: '새 CVE', color: 'var(--color-neutral-mark)', height: 96 },
  critical: { name: 'Critical', color: 'var(--color-sev-crit)', height: 72 },
}

const PanelStat = ({ panel }: { panel: TrendPanel }) => {
  const spec = SPECS[panel.key]
  return (
    <div role="group" aria-label={spec.name} className="flex flex-col justify-end gap-0.5 pb-0.5">
      <span className="flex items-center gap-1.5 text-xs font-semibold text-ink-2">
        <span aria-hidden="true" className="size-2.5 rounded-sm" style={{ background: spec.color }} />
        {spec.name}
      </span>
      <span className="text-2xl leading-[1.1] font-bold text-ink tabular-nums">
        {formatCount(Math.round(panel.recent))}
        <small className="ml-[3px] text-xs font-normal text-ink-3">최근 7일</small>
      </span>
      <span className="text-xs leading-[1.4] text-ink-2">
        하루 중앙값
        <br />
        평소 {formatCount(Math.round(panel.usual))} · {VERDICT_WORD[panel.verdict]} ({formatChange(panel.ratio)})
      </span>
      <span className="text-xs text-ink-3">세로 눈금 0~{formatCount(panel.cap)}</span>
    </div>
  )
}

interface PlotProps {
  panel: TrendPanel
  dayCount: number
  active: number | null
  focusable: boolean
  onActive: (index: number | null) => void
}

// 막대 한 줄. 넘는 날은 위를 자르고 값을, 오늘은 빗금 + 값을 적는다. 이어진 두 값 글자는 위아래로 엇갈린다
const Plot = ({ panel, dayCount, active, focusable, onActive }: PlotProps) => {
  const spec = SPECS[panel.key]
  const labelled = panel.bars.map((bar) => bar.cut || bar.partial)
  const raised = labelled.reduce<boolean[]>((acc, has, index) => {
    acc.push(has && index > 0 && labelled[index - 1] && acc[index - 1] === false)
    return acc
  }, [])
  const cutNote = panel.bars.some((bar) => bar.cut) ? ` 세로축 ${formatCount(panel.cap)}건 위는 잘라 그림.` : ''
  const keyHint = focusable ? ' 화살표 키로 날짜를 옮겨 값을 읽습니다.' : ''

  const onKeyDown = (event: KeyboardEvent) => {
    const current = active ?? dayCount - 1
    if (event.key === 'ArrowLeft') {
      event.preventDefault()
      onActive(Math.max(0, current - 1))
    }
    if (event.key === 'ArrowRight') {
      event.preventDefault()
      onActive(Math.min(dayCount - 1, current + 1))
    }
    if (event.key === 'Escape') onActive(null)
  }

  return (
    <div
      role="img"
      aria-label={`${spec.name} 하루 건수 ${dayCount}일 막대. 평소(중앙값) ${formatCount(Math.round(panel.usual))}건.${cutNote}${keyHint}`}
      tabIndex={focusable ? 0 : undefined}
      onFocus={focusable ? () => onActive(active ?? dayCount - 1) : undefined}
      onBlur={focusable ? () => onActive(null) : undefined}
      onKeyDown={focusable ? onKeyDown : undefined}
      onMouseLeave={() => onActive(null)}
      className={`relative flex items-end border-b border-axis focus-visible:rounded focus-visible:outline-2 focus-visible:outline-ink ${raised.some(Boolean) ? 'mt-8' : 'mt-4'}`}
      style={{ height: spec.height }}
    >
      {panel.bars.map((bar, index) => (
        <div
          key={bar.date}
          onMouseMove={() => onActive(index)}
          onClick={() => onActive(index)}
          className={`relative flex h-full min-w-0 flex-1 items-end justify-center ${
            active === index ? 'bg-[#e6e5ec]' : bar.weekend ? 'bg-surface-sunk' : ''
          }`}
        >
          <div
            className={`relative min-h-0.5 w-[64%] max-w-5 ${bar.cut ? '' : 'rounded-t'} ${bar.partial ? 'min-h-2' : ''}`}
            style={{ height: `${bar.height}%`, ...(bar.partial ? PARTIAL : { background: spec.color }) }}
          >
            {bar.cut ? (
              <span
                aria-hidden="true"
                className="absolute -inset-x-0.5 top-1.5 h-[7px] bg-surface"
                style={{ clipPath: ZIGZAG }}
              />
            ) : null}
            {labelled[index] ? (
              <span
                className={`pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 text-xs whitespace-nowrap tabular-nums ${
                  bar.partial ? 'font-medium text-ink-2' : 'font-bold text-ink'
                } ${raised[index] ? 'mb-[17px]' : 'mb-0.5'}`}
              >
                {formatCount(bar.value)}
              </span>
            ) : null}
          </div>
        </div>
      ))}
      <div
        className="pointer-events-none absolute inset-x-0 border-t border-ink-2"
        style={{ bottom: `${panel.usualHeight}%` }}
      >
        <span className="absolute bottom-0.5 left-0 rounded-[3px] bg-white/90 px-[3px] text-xs whitespace-nowrap text-ink-2 tabular-nums">
          평소 {formatCount(Math.round(panel.usual))}
        </span>
      </div>
    </div>
  )
}

const SEVERITY_COLOR: Record<keyof SeverityDistribution, string> = {
  critical: 'var(--color-sev-crit)',
  high: 'var(--color-sev-high)',
  medium: 'var(--color-sev-med)',
  low: 'var(--color-sev-low)',
  unrated: 'var(--color-neutral-wash)',
}

// 심각도는 도넛 대신 한 줄 누적 막대 (CHART-REVIEW T-e: 등급 없음은 neutral-wash 로 채움)
const SeverityLine = ({ distribution }: { distribution: SeverityDistribution }) => {
  const parts = severityParts(distribution)
  const total = parts.reduce((sum, part) => sum + part.count, 0)
  if (total === 0) return null
  return (
    <div>
      <h3 className="m-0 mb-1.5 text-[13px] font-semibold text-ink-2">
        최근 7일 새 CVE {formatCount(total)}건의 심각도
      </h3>
      <div
        role="img"
        aria-label={parts.map((part) => `${part.label} ${formatCount(part.count)}건`).join(', ')}
        className="flex h-3.5 gap-0.5"
      >
        {parts.map((part) => (
          <i
            key={part.key}
            className="block h-full min-w-[3px] first:rounded-l last:rounded-r"
            style={{ flex: part.count, background: SEVERITY_COLOR[part.key] }}
          />
        ))}
      </div>
      <div aria-hidden="true" className="mt-1.5 flex flex-wrap gap-x-3.5 gap-y-1 text-xs text-ink-2">
        {parts.map((part) => (
          <span key={part.key} className="inline-flex items-center gap-[5px] whitespace-nowrap">
            <i className="inline-block size-2.5 rounded-sm" style={{ background: SEVERITY_COLOR[part.key] }} />
            {part.label} <b className="font-semibold text-ink tabular-nums">{formatCount(part.count)}</b>
          </span>
        ))}
      </div>
    </div>
  )
}

const TrendBody = ({ meta, stats }: { meta: Meta; stats: Stats }) => {
  const [active, setActive] = useState<number | null>(null)
  const { daily } = stats
  if (daily.length === 0) {
    return (
      <>
        <h2 id="trend-title" className="m-0 text-lg font-bold tracking-tight text-ink">
          늘고 있나
        </h2>
        <p className="m-0 text-[13px] text-ink-3">일별 통계가 아직 없습니다.</p>
      </>
    )
  }
  const updatedAt = meta.updated_at
  const partial = lastDayIsPartial(daily, updatedAt)
  const fresh = trendPanel(daily, 'new', updatedAt)
  const critical = trendPanel(daily, 'critical', updatedAt)
  const spike = spikeNote(daily, updatedAt)
  const labels = axisLabels(daily, updatedAt)
  const cutDates = [...new Set([...fresh.bars, ...critical.bars].filter((bar) => bar.cut).map((bar) => bar.date))]
  const today = daily[daily.length - 1]
  const activeDay = active === null ? null : daily[active]

  return (
    <>
      <div>
        <h2 id="trend-title" className="m-0 text-lg font-bold tracking-tight text-ink">
          늘고 있나 — {trendAnswer(fresh, critical)}
        </h2>
        <p className="mt-1 mb-0 text-[13px] text-ink-3">
          {formatMonthDay(daily[0].date)}~{formatMonthDay(today.date)} 하루 건수 · 최근 7일 중앙값을 평소(가로선,{' '}
          {completeDays(daily, updatedAt).length}일 중앙값)와 비교해 ±15% 넘으면 많음·적음 · 두 줄은 세로 눈금이 다름
        </p>
        {spike === null ? null : <p className="mt-1.5 mb-0 text-sm leading-[1.45] font-semibold text-ink">{spike}</p>}
      </div>
      <div className="relative grid grid-cols-[minmax(84px,104px)_minmax(0,1fr)] gap-x-3 gap-y-4">
        <PanelStat panel={fresh} />
        <Plot panel={fresh} dayCount={daily.length} active={active} focusable onActive={setActive} />
        <PanelStat panel={critical} />
        <Plot panel={critical} dayCount={daily.length} active={active} focusable={false} onActive={setActive} />
        <div />
        <div aria-hidden="true" className="flex">
          {labels.map((label, index) => (
            <span
              key={daily[index].date}
              className={`min-w-0 flex-1 text-center text-xs leading-[1.3] whitespace-nowrap tabular-nums ${
                index === daily.length - 1 ? 'font-bold text-ink' : 'text-ink-3'
              }`}
            >
              {label ?? ' '}
            </span>
          ))}
        </div>
        {activeDay === null ? null : (
          <div
            role="status"
            aria-label="고른 날 값"
            className="pointer-events-none absolute top-0 right-0 z-20 min-w-[130px] rounded-md bg-ink px-2.5 py-2 text-xs leading-normal whitespace-nowrap text-white"
          >
            <div className="text-[#c9c8d2]">
              {dayLabel(activeDay.date)}
              {partial && active === daily.length - 1 ? ' · 집계 중' : ''}
            </div>
            <div className="flex justify-between gap-4">
              <span>새 CVE</span>
              <b className="tabular-nums">{formatCount(activeDay.new)}건</b>
            </div>
            <div className="flex justify-between gap-4">
              <span>Critical</span>
              <b className="tabular-nums">{formatCount(activeDay.critical)}건</b>
            </div>
          </div>
        )}
      </div>
      <ul className="m-0 flex list-none flex-col gap-1 p-0 text-xs text-ink-2">
        <li className="flex items-center gap-1.5">
          <span
            aria-hidden="true"
            className="inline-block h-2.5 w-3.5 bg-surface-sunk shadow-[inset_0_0_0_1px_var(--color-border)]"
          />
          옅은 띠 = 주말 (토·일은 새 CVE 가 적게 나옵니다)
        </li>
        {cutDates.length === 0 ? null : (
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="inline-block h-2.5 w-3.5 bg-neutral-mark" />
            평소의 2배를 넘는 날은 막대 위를 잘라 그리고 실제 값을 적었습니다 ({cutDates.map(formatMonthDay).join(', ')}
            )
          </li>
        )}
        {partial ? (
          <li className="flex items-center gap-1.5">
            <span aria-hidden="true" className="inline-block h-2.5 w-3.5" style={PARTIAL} />
            오늘은 {formatKstTime(updatedAt)} 까지 모은 값이라 비교에서 뺐습니다
            {meta.today.new === today.new
              ? ''
              : ` (일별 통계 ${formatCount(today.new)}건 — 위 카드의 ${formatCount(meta.today.new)}건과 집계 시점이 다릅니다)`}
          </li>
        ) : null}
      </ul>
      <SeverityLine distribution={stats.severity_7d} />
      <div className="flex flex-wrap justify-between gap-2 text-xs text-ink-3">
        <span>막대에 올리거나 눌러 값을 봅니다 · 키보드 ← →</span>
        <details>
          <summary className="cursor-pointer text-ink-2">표로 보기</summary>
          <table className="mt-2 w-full border-collapse text-xs tabular-nums">
            <thead>
              <tr className="text-ink-2">
                <th scope="col" className="border-b border-grid px-1.5 py-1 text-left font-semibold">
                  날짜
                </th>
                <th scope="col" className="border-b border-grid px-1.5 py-1 text-right font-semibold">
                  새 CVE
                </th>
                <th scope="col" className="border-b border-grid px-1.5 py-1 text-right font-semibold">
                  Critical
                </th>
              </tr>
            </thead>
            <tbody>
              {daily.map((day, index) => (
                <tr key={day.date}>
                  <td className="border-b border-grid px-1.5 py-1 text-left">
                    {dayLabel(day.date)}
                    {partial && index === daily.length - 1 ? ' 집계 중' : ''}
                  </td>
                  <td className="border-b border-grid px-1.5 py-1 text-right">{formatCount(day.new)}</td>
                  <td className="border-b border-grid px-1.5 py-1 text-right">{formatCount(day.critical)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </details>
      </div>
    </>
  )
}

// "늘고 있나" 추이 카드 (design/v6 a-top #a-trend, CONSENSUS 5절): 새 CVE·Critical 두 줄 + 최근 7일 심각도 한 줄.
// 축은 선형, 위를 자르고, 평소(중앙값) 실선, 오늘(집계 중)은 빗금. 차트 라이브러리 없이 그린다 (사용자 결정 Q1)
export const TrendCard = () => {
  const meta = useMeta()
  const stats = useStats()
  return (
    <section aria-labelledby="trend-title" className={CARD}>
      <LoadableView loadable={meta} label="추이">
        {(metaData) => (
          <LoadableView loadable={stats} label="추이">
            {(data) => <TrendBody meta={metaData} stats={data} />}
          </LoadableView>
        )}
      </LoadableView>
    </section>
  )
}
