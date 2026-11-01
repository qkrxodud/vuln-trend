import type { ReactNode } from 'react'

// v6 KPI 카드 틀 (design/v6 a-top .vt-card.kpi). 제목은 카드 이름이자 화면 읽기용 이름
export const KpiArticle = ({
  id,
  title,
  className = '',
  children,
}: {
  id: string
  title: string
  className?: string
  children: ReactNode
}) => (
  <article
    aria-labelledby={id}
    className={`flex min-w-0 flex-col gap-1.5 rounded-[10px] border border-border bg-surface p-5 ${className}`}
  >
    <h2 id={id} className="text-sm font-semibold text-ink-2">
      {title}
    </h2>
    {children}
  </article>
)

export const KpiValue = ({ value, children }: { value: string; children?: ReactNode }) => (
  <div className="text-[32px] leading-[1.05] font-bold tracking-tight text-ink tabular-nums">
    <span>{value}</span>
    <span className="ml-[3px] text-base font-semibold text-ink-2">건</span>
    {children}
  </div>
)
