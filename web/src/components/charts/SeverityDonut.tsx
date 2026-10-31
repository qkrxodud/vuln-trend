import { useState } from 'react'
import { Cell, Pie, PieChart } from 'recharts'
import { toSeverityShares, type SeverityKey } from '../../lib/charts.ts'
import { formatCount } from '../../lib/kpi.ts'
import type { SeverityDistribution } from '../../types/stats.ts'

// 심각도 5색 고정 (기획서 8장 색상 규칙). index.css 의 --color-sev-* 와 같은 값
const SEVERITY_FILL: Record<SeverityKey, string> = {
  critical: 'var(--color-sev-critical)',
  high: 'var(--color-sev-high)',
  medium: 'var(--color-sev-medium)',
  low: 'var(--color-sev-low)',
  unrated: 'var(--color-sev-unrated)',
}

const DONUT_SIZE = 200

export const SeverityDonut = ({ distribution }: { distribution: SeverityDistribution }) => {
  const { total, shares } = toSeverityShares(distribution)
  const [activeIndex, setActiveIndex] = useState<number | null>(null)
  const active = activeIndex === null ? null : shares[activeIndex]
  const clear = () => setActiveIndex(null)

  return (
    <div className="flex min-w-0 flex-[1_1_300px] flex-col gap-3.5">
      <div className="flex flex-col gap-0.5">
        <h2 className="text-[17px] font-semibold">최근 7일 심각도 분포</h2>
        <p className="text-xs text-ink-faint">CVSS 미평가(Awaiting Analysis) 포함</p>
      </div>
      <div className="flex flex-wrap items-center gap-6">
        <div className="relative shrink-0" style={{ width: DONUT_SIZE, height: DONUT_SIZE }}>
          <PieChart width={DONUT_SIZE} height={DONUT_SIZE} title="최근 7일 신규 CVE 심각도 분포 도넛 차트">
            <Pie
              data={shares}
              dataKey="count"
              nameKey="label"
              innerRadius={62}
              outerRadius={88}
              startAngle={90}
              endAngle={-270}
              paddingAngle={1}
              stroke="none"
              isAnimationActive={false}
              onMouseEnter={(_, index) => setActiveIndex(index)}
              onMouseLeave={clear}
            >
              {shares.map((share, index) => (
                <Cell
                  key={share.key}
                  fill={SEVERITY_FILL[share.key]}
                  opacity={activeIndex === null || activeIndex === index ? 1 : 0.45}
                />
              ))}
            </Pie>
          </PieChart>
          <div
            aria-label="도넛 가운데 값"
            aria-live="polite"
            className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center"
          >
            <span className="text-[13px] text-ink-muted">{active === null ? '최근 7일' : active.label}</span>
            <span className="text-[30px] font-bold tracking-tight tabular-nums">
              {active === null ? formatCount(total) : `${active.percent}%`}
            </span>
            <span className="text-xs text-ink-faint">{active === null ? '건' : `${formatCount(active.count)}건`}</span>
          </div>
        </div>
        <ul aria-label="심각도 범례" className="flex min-w-[120px] flex-1 flex-col gap-2.5">
          {shares.map((share, index) => (
            <li key={share.key}>
              <button
                type="button"
                onMouseEnter={() => setActiveIndex(index)}
                onMouseLeave={clear}
                onFocus={() => setActiveIndex(index)}
                onBlur={clear}
                className={`flex w-full items-center gap-2 rounded-md px-1 py-0.5 text-left text-sm focus-visible:outline-2 focus-visible:outline-accent ${
                  activeIndex === null || activeIndex === index ? '' : 'opacity-45'
                }`}
              >
                <span
                  aria-hidden="true"
                  className="size-2.5 rounded-[3px]"
                  style={{ background: SEVERITY_FILL[share.key] }}
                />
                <span className="flex-1">{share.label}</span>
                <span className="text-xs text-ink-faint">{formatCount(share.count)}건</span>
                <span className="w-10 text-right font-semibold tabular-nums">{share.percent}%</span>
              </button>
            </li>
          ))}
        </ul>
      </div>
    </div>
  )
}
