import { useLayoutEffect, useRef } from 'react'
import { Link } from 'react-router'
import { categoryPath, chipOrder, isQuiet, type CategoryAxis } from '../../lib/categories.ts'
import { formatCount } from '../../lib/kpi.ts'
import type { Category } from '../../types/categories.ts'

const CHIP =
  'inline-flex shrink-0 items-center gap-1.5 rounded-full border border-axis bg-surface px-3 py-1 text-[13px] font-semibold whitespace-nowrap text-ink hover:border-ink-3 aria-[current=page]:border-ink aria-[current=page]:bg-ink aria-[current=page]:text-white data-[quiet=true]:font-medium data-[quiet=true]:text-ink-3'

interface CategoryChipsProps {
  axis: CategoryAxis
  list: Category[]
  /** 묶음 이름(화면 읽기용) */
  label: string
  currentKey?: string | null
  /** 한 줄로 두고 넘치면 옆으로 밀어 본다(높이 고정). 아니면 줄을 바꾼다 */
  scroll?: boolean
}

// 분류 칩: 최근 7일 건수 많은 순, 0건 분류는 흐리게 뒤로 (숨기지 않는다 — 사용자 결정 2026-11-02)
export const CategoryChips = ({ axis, list, label, currentKey = null, scroll = false }: CategoryChipsProps) => {
  const row = useRef<HTMLDivElement>(null)
  // 한 줄 칩에서는 지금 분류 칩이 보이도록 줄 안에서만 옆으로 민다(화면은 움직이지 않는다)
  useLayoutEffect(() => {
    const container = row.current
    const current = container?.querySelector<HTMLElement>('[aria-current="page"]') ?? null
    if (container === null || current === null) return
    container.scrollLeft = current.offsetLeft - container.offsetLeft - (container.clientWidth - current.offsetWidth) / 2
  }, [currentKey])
  return (
    <div
      ref={row}
      role="group"
      aria-label={label}
      className={
        scroll
          ? 'flex h-10 min-w-0 flex-1 flex-nowrap items-start gap-1.5 overflow-x-auto overflow-y-hidden [scrollbar-width:thin]'
          : 'flex flex-wrap gap-1.5'
      }
    >
      {chipOrder(list).map((category) => (
        <Link
          key={category.key}
          to={categoryPath(axis, category.key)}
          aria-label={`${category.label} 최근 7일 ${formatCount(category.count_7d)}건`}
          aria-current={category.key === currentKey ? 'page' : undefined}
          data-quiet={isQuiet(category)}
          className={CHIP}
        >
          {category.label}
          <span className="text-xs font-medium tabular-nums opacity-80">{formatCount(category.count_7d)}</span>
        </Link>
      ))}
    </div>
  )
}
