import { SPARK_SIZE, sparkline } from '../../lib/sparkline.ts'

// 14일 스파크라인: 완결일 실선 + 채운 끝점, 집계 중인 오늘은 점선 + 빈 점, 평소 가로선 (design/v6 T2·B5)
export const Sparkline = ({ values, partial }: { values: number[]; partial: boolean }) => {
  const line = sparkline(values, partial)
  if (line === null) return null
  const { width, height, pad } = SPARK_SIZE
  return (
    <svg viewBox={`0 0 ${width} ${height}`} aria-hidden="true" className="h-10 w-28 shrink-0 overflow-visible">
      <line x1={pad} x2={width - pad} y1={line.usualY} y2={line.usualY} className="stroke-ink-3" strokeWidth={1} />
      <path
        d={line.solid}
        fill="none"
        className="stroke-neutral-mark"
        strokeWidth={2}
        strokeLinejoin="round"
        strokeLinecap="round"
      />
      {line.partial === null || line.today === null ? null : (
        <>
          <path d={line.partial} fill="none" className="stroke-neutral-mark" strokeWidth={1.5} strokeDasharray="2 3" />
          <circle
            cx={line.today.x}
            cy={line.today.y}
            r={3}
            className="fill-surface stroke-neutral-mark"
            strokeWidth={1.5}
          />
        </>
      )}
      <circle cx={line.end.x} cy={line.end.y} r={4} className="fill-ink-2 stroke-surface" strokeWidth={2} />
    </svg>
  )
}
