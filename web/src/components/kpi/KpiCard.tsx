import type { ReactNode } from 'react'
import { describeDelta, formatCount, formatDelta } from '../../lib/kpi.ts'

export interface KpiCardProps {
  label: string
  /** 카드 아래 한 줄 설명 (집계 기준) */
  description: string
  today: number
  yesterday: number
  /** KEV 신규 숫자만 KEV 빨강 (기획서 8장 색상 규칙) */
  tone?: 'default' | 'kev'
  /** 필터를 거는 카드면 눌림 상태와 누를 때 동작. 없으면 누를 수 없는 카드 */
  toggle?: { pressed: boolean; onToggle: () => void }
}

const CARD_BASE = 'flex w-full flex-col gap-1.5 rounded-2xl border px-[22px] py-5 text-left'

const CardBody = ({ label, description, today, yesterday, tone = 'default', toggle }: KpiCardProps) => {
  const hint = toggle === undefined ? `어제 ${formatCount(yesterday)}` : toggle.pressed ? '필터 해제' : '눌러서 필터'
  return (
    <>
      <span className="flex items-center justify-between text-[15px] text-ink-soft">
        <span>{label}</span>
        <span className="text-xs text-ink-faint">{hint}</span>
      </span>
      <span className="flex items-baseline gap-2.5">
        <span
          className={`text-[40px] leading-tight font-bold tracking-tight tabular-nums ${tone === 'kev' ? 'text-kev' : 'text-ink'}`}
        >
          {formatCount(today)}
        </span>
        <span aria-hidden="true" className="rounded-full bg-chip px-2 py-0.5 text-[13px] font-semibold text-ink-soft">
          {formatDelta(today, yesterday)}
        </span>
        <span className="sr-only">{describeDelta(today, yesterday)}</span>
      </span>
      <span className="text-xs text-ink-faint">{description}</span>
    </>
  )
}

// KPI 카드 하나. 필터 카드는 전체가 버튼이고, 아니면 정적인 카드다
export const KpiCard = (props: KpiCardProps): ReactNode => {
  const { toggle } = props
  if (toggle === undefined) {
    return (
      <div data-kpi className={`${CARD_BASE} border-transparent bg-paper`}>
        <CardBody {...props} />
      </div>
    )
  }
  return (
    <div data-kpi>
      <button
        type="button"
        aria-pressed={toggle.pressed}
        onClick={toggle.onToggle}
        className={`${CARD_BASE} transition hover:-translate-y-0.5 focus-visible:outline-2 focus-visible:outline-accent ${
          toggle.pressed ? 'border-accent bg-accent-soft' : 'border-transparent bg-paper'
        }`}
      >
        <CardBody {...props} />
      </button>
    </div>
  )
}
