import { formatDecay, formatPoints, toScoreFactors } from '../../lib/score-factors.ts'
import type { ScoreBreakdown } from '../../types/trend.ts'

const ROW = 'grid grid-cols-[130px_minmax(0,1fr)_52px] items-center gap-2.5 text-[13px]'

// 점수 구성: 0 이 아닌 항 + 시간 감쇠 (막대는 회색, 기획서 8장 색상 규칙)
export const ScoreBreakdownList = ({ breakdown }: { breakdown: ScoreBreakdown }) => (
  <ul aria-label="점수 구성" className="flex flex-col gap-2.5">
    {toScoreFactors(breakdown).map((factor) => (
      <li key={factor.key} className={ROW}>
        <span className="text-ink-soft">{factor.label}</span>
        <span aria-hidden="true" className="h-2 overflow-hidden rounded-full bg-line">
          <span className="block h-full rounded-full bg-ink-muted" style={{ width: `${factor.widthPercent}%` }} />
        </span>
        <span className="text-right font-semibold tabular-nums">{formatPoints(factor.value)}</span>
      </li>
    ))}
    <li className={`${ROW} text-ink-muted`}>
      <span>시간 감쇠</span>
      <span className="text-xs">30일마다 절반</span>
      <span className="text-right font-semibold tabular-nums">{formatDecay(breakdown.decay)}</span>
    </li>
  </ul>
)
