import type { ReactNode } from 'react'
import { describeDelta, formatCount, formatDelta } from '../../lib/kpi.ts'

export interface KpiCardProps {
  label: string
  /** 카드 아래 한 줄 설명 (집계 기준) */
  description: string
  today: number
  /** 어제 값. null 이면 증감과 "어제 N" 을 숨긴다 */
  yesterday: number | null
  /** KEV 신규 숫자만 KEV 빨강 (기획서 8장 색상 규칙) */
  tone?: 'default' | 'kev'
  /** 필터를 거는 카드면 눌림 상태와 누를 때 동작. 없으면 누를 수 없는 카드 */
  toggle?: { pressed: boolean; onToggle: () => void }
}

const CARD_BASE = 'flex h-full w-full flex-col gap-1.5 rounded-2xl border px-4 py-4 text-left sm:px-[22px] sm:py-5'

// 어제 대비 증감. 어제 값이 없으면(고위험 첫 스냅샷 등) 배지와 읽기 문장을 함께 숨긴다
const Delta = ({ today, yesterday }: { today: number; yesterday: number | null }) => {
  if (yesterday === null) return null
  return (
    <>
      <span aria-hidden="true" className="rounded-full bg-chip px-2 py-0.5 text-[13px] font-semibold text-ink-soft">
        {formatDelta(today, yesterday)}
      </span>
      <span className="sr-only">{describeDelta(today, yesterday)}</span>
    </>
  )
}

const hintFor = (yesterday: number | null, toggle: KpiCardProps['toggle']): string | null => {
  if (toggle !== undefined) return toggle.pressed ? '필터 해제' : '눌러서 필터'
  if (yesterday === null) return null
  return `어제 ${formatCount(yesterday)}`
}

const CardBody = ({ label, description, today, yesterday, tone = 'default', toggle }: KpiCardProps) => {
  const hint = hintFor(yesterday, toggle)
  return (
    <>
      <span className="flex flex-wrap items-center justify-between gap-x-2 text-sm text-ink-soft sm:text-[15px]">
        <span>{label}</span>
        {hint === null ? null : <span className="text-xs text-ink-faint">{hint}</span>}
      </span>
      <span className="flex items-baseline gap-2.5">
        <span
          className={`text-[32px] leading-tight sm:text-[40px] font-bold tracking-tight tabular-nums ${tone === 'kev' ? 'text-kev' : 'text-ink'}`}
        >
          {formatCount(today)}
        </span>
        <Delta today={today} yesterday={yesterday} />
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
    <div data-kpi className="h-full">
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
