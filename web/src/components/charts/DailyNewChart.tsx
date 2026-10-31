import { Bar, BarChart, CartesianGrid, Tooltip, XAxis, YAxis } from 'recharts'
import { formatCount } from '../../lib/kpi.ts'
import { dailyBarTitle, toDailyBars, type DailyBar } from '../../lib/charts.ts'
import type { DailyCount } from '../../types/stats.ts'

const TITLE = '최근 14일 일별 신규 건수'

const LegendSwatch = ({ colorClass, label }: { colorClass: string; label: string }) => (
  <span className="flex items-center gap-1.5">
    <span aria-hidden="true" className={`size-2.5 rounded-[3px] ${colorClass}`} />
    {label}
  </span>
)

// 툴팁: 마우스를 올리거나, 차트에 키보드 포커스를 두고 ←→ 로 옮겨도 열린다 (Recharts accessibilityLayer, S3)
interface DailyTooltipProps {
  active?: boolean
  payload?: readonly { payload?: unknown }[]
}

const DailyTooltip = ({ active, payload }: DailyTooltipProps) => {
  const bar = payload?.[0]?.payload as DailyBar | undefined
  if (active !== true || bar === undefined) return null
  return (
    <div className="flex flex-col gap-0.5 rounded-[10px] bg-ink px-3 py-2 text-xs whitespace-nowrap text-white">
      <span className="text-[#b9b9bd]">{dailyBarTitle(bar)}</span>
      <span className="font-semibold">신규 {formatCount(bar.total)}건</span>
      <span className="text-[#e8a898]">Critical {formatCount(bar.critical)}건</span>
    </div>
  )
}

interface DayTickProps {
  x?: number | string
  y?: number | string
  /** 보이는 눈금의 순번. 막대 순번과 다를 수 있어 쓰지 않는다 */
  index?: number
  payload?: { value: string }
  bars: DailyBar[]
}

// x축 날짜. 가장 최근 날 라벨만 진하게 (디자인 원본). 갱신한 날이면 아래에 "진행 중"을 덧붙인다
// 좁은 화면은 라벨을 건너뛰어 눈금 순번이 막대 순번과 다르다. 막대는 라벨로 찾는다
export const DayTick = ({ x, y, payload, bars }: DayTickProps) => {
  const bar = bars.find((candidate) => candidate.label === payload?.value)
  const latest = bar?.isLatest === true
  return (
    <text
      x={x}
      y={y}
      dy={14}
      textAnchor="middle"
      fontSize={11}
      fontWeight={latest ? 700 : 400}
      fill={latest ? 'var(--color-ink)' : 'var(--color-ink-faint)'}
      className="tabular-nums"
    >
      {payload?.value}
      {bar === undefined || bar.inProgressUntil === null ? null : (
        <tspan x={x} dy={13} fontSize={10} fontWeight={400} fill="var(--color-ink-faint)">
          진행 중
        </tspan>
      )}
    </text>
  )
}

// 그림을 못 보는 방문자를 위한 같은 내용의 표
const DailyTable = ({ bars }: { bars: DailyBar[] }) => (
  <table className="sr-only">
    <caption>{TITLE}</caption>
    <thead>
      <tr>
        <th scope="col">날짜</th>
        <th scope="col">신규</th>
        <th scope="col">Critical</th>
      </tr>
    </thead>
    <tbody>
      {bars.map((bar) => (
        <tr key={bar.date}>
          <th scope="row">{dailyBarTitle(bar)}</th>
          <td>{bar.total}</td>
          <td>{bar.critical}</td>
        </tr>
      ))}
    </tbody>
  </table>
)

export const DailyNewChart = ({ daily, updatedAt }: { daily: DailyCount[]; updatedAt: string | null }) => {
  const bars = toDailyBars(daily, updatedAt)
  return (
    <div className="flex min-w-0 flex-col gap-3.5">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex flex-col gap-0.5">
          <h2 className="text-[17px] font-semibold">{TITLE}</h2>
          <p className="text-xs text-ink-faint">공개일(KST) 기준 · 매 수집마다 14일 재계산</p>
        </div>
        <div className="flex gap-3.5 text-[13px] text-ink-soft">
          <LegendSwatch colorClass="bg-accent" label="전체" />
          <LegendSwatch colorClass="bg-sev-critical" label="Critical" />
        </div>
      </div>
      <BarChart
        responsive
        accessibilityLayer
        data={bars}
        style={{ width: '100%', height: 260 }}
        margin={{ top: 8, right: 0, bottom: 0, left: 0 }}
        title={TITLE}
      >
        <CartesianGrid vertical={false} stroke="var(--color-line)" />
        <XAxis
          dataKey="label"
          tickLine={false}
          axisLine={false}
          interval="preserveEnd"
          tick={<DayTick bars={bars} />}
          height={38}
        />
        <YAxis width={36} tickLine={false} axisLine={false} allowDecimals={false} tick={{ fontSize: 12 }} />
        <Tooltip
          content={({ active, payload }) => <DailyTooltip active={active} payload={payload} />}
          cursor={{ fill: 'var(--color-paper)' }}
          isAnimationActive={false}
        />
        <Bar dataKey="nonCritical" stackId="day" fill="var(--color-accent)" name="Critical 외" />
        <Bar dataKey="critical" stackId="day" fill="var(--color-sev-critical)" radius={[6, 6, 0, 0]} name="Critical" />
      </BarChart>
      <DailyTable bars={bars} />
    </div>
  )
}
