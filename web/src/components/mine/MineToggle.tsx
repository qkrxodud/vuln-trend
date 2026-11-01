import { useMineOptional } from '../../hooks/use-mine.ts'

// "☆ 내 제품" 별표: 이 제품을 내 제품에 더하거나 뺀다(이 브라우저에만 저장). 제품을 모르거나 공급자가 없으면 그리지 않는다
export const MineToggle = ({ product }: { product: string | null }) => {
  const mine = useMineOptional()
  if (mine === null || product === null) return null
  const pressed = mine.mine.products.includes(product)
  return (
    <button
      type="button"
      aria-pressed={pressed}
      aria-label={pressed ? `${product} 내 제품에서 빼기` : `${product} 내 제품에 더하기`}
      onClick={() => mine.toggle(product)}
      className="inline-flex shrink-0 items-center gap-1 rounded-full border border-axis bg-surface px-2 py-0.5 text-xs font-semibold whitespace-nowrap text-ink-2 hover:border-ink-3 aria-pressed:border-ink aria-pressed:text-ink"
    >
      <span aria-hidden="true">{pressed ? '★' : '☆'}</span> 내 제품
    </button>
  )
}
