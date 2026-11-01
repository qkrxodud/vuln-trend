import { useListFilter } from '../../hooks/use-list-filter.ts'
import { QUADRANT_NAME } from '../../lib/quadrants.ts'
import { productCounts, signalOptions, SIGNALS, TOP_PRODUCTS } from '../../lib/list-model.ts'
import type { TrendRow } from '../../types/trend.ts'
import { Diamond } from '../signal/UrgentBadge.tsx'

const PRESSED = 'aria-pressed:bg-ink aria-pressed:font-semibold aria-pressed:text-white'
const COUNT = 'text-xs text-ink-3 tabular-nums group-aria-pressed:text-white'

// 신호 버튼 (design/v6 c-list .vtc-seg): 전체 / CISA 기한 임박 / 랜섬웨어 / 새 KEV 7일 / PoC 공개. 0건 신호는 숨김
export const SignalFilter = ({ rows, today }: { rows: TrendRow[]; today: string }) => {
  const { filter, setSignal } = useListFilter()
  return (
    <div
      role="group"
      aria-label="신호로 거르기"
      className="flex flex-wrap gap-0.5 rounded-[18px] border border-axis bg-surface p-[3px]"
    >
      <span aria-hidden="true" className="self-center pr-1 pl-2 text-xs whitespace-nowrap text-ink-2">
        신호
      </span>
      {signalOptions(rows, filter.signal, today).map((option) => (
        <button
          key={option.key}
          type="button"
          aria-pressed={filter.signal === option.key}
          onClick={() => setSignal(option.key)}
          className={`group flex items-center gap-1.5 rounded-full px-3 py-1.5 text-[13px] whitespace-nowrap text-ink-2 ${PRESSED}`}
        >
          {option.key === 'urgent' ? <Diamond /> : null}
          {option.label} <span className={COUNT}>{option.count}</span>
        </button>
      ))}
    </div>
  )
}

const CHIP = `group flex max-w-60 items-center gap-1.5 rounded-full border border-axis bg-surface px-3 py-1.5 text-[13px] font-semibold text-ink aria-pressed:border-ink ${PRESSED}`

// 제품 칩: 모든 제품 + 건수 많은 5개 + "그 외 n개 제품" 선택 상자. 건수는 지금 고른 신호 안에서 센다
export const ProductFilter = ({ rows, today }: { rows: TrendRow[]; today: string }) => {
  const { filter, setProduct } = useListFilter()
  const inSignal = productCounts(rows, filter.signal, today)
  const countOf = (name: string) => inSignal.find((product) => product.name === name)?.count ?? 0
  const all = productCounts(rows, 'all', today)
  const top = all.slice(0, TOP_PRODUCTS)
  const rest = all.slice(TOP_PRODUCTS)
  const restSelected = rest.some((product) => product.name === filter.product)
  const signalTotal = inSignal.reduce((sum, product) => sum + product.count, 0)

  return (
    <div
      role="group"
      aria-label="제품으로 거르기"
      className="flex flex-wrap items-center gap-1.5 border-b border-border pb-2.5"
    >
      <button type="button" aria-pressed={filter.product === null} onClick={() => setProduct(null)} className={CHIP}>
        <span className="truncate">모든 제품</span> <span className={COUNT}>{signalTotal}</span>
      </button>
      {top.map((product) => (
        <button
          key={product.name}
          type="button"
          title={product.name}
          aria-pressed={filter.product === product.name}
          onClick={() => setProduct(product.name)}
          className={CHIP}
        >
          <span className="min-w-0 truncate">{product.name}</span>{' '}
          <span className={COUNT}>{countOf(product.name)}</span>
        </button>
      ))}
      {rest.length === 0 ? null : (
        <select
          aria-label="그 외 제품 고르기"
          value={restSelected ? (filter.product ?? '') : ''}
          onChange={(event) => setProduct(event.target.value === '' ? null : event.target.value)}
          className={`max-w-[260px] rounded-full border px-2.5 py-1.5 text-[13px] ${
            restSelected ? 'border-ink bg-ink text-white' : 'border-axis bg-surface text-ink'
          }`}
        >
          <option value="">그 외 {rest.length}개 제품…</option>
          {rest.map((product) => (
            <option key={product.name} value={product.name}>
              {product.name} ({countOf(product.name)})
            </option>
          ))}
        </select>
      )}
    </div>
  )
}

// 지금 보는 범위 한 줄. 필터를 걸었으면 몇 건 중 몇 건인지와 필터 해제 버튼
export const ListSummary = ({ total, shown }: { total: number; shown: number }) => {
  const { filter, clear } = useListFilter()
  const signalLabel =
    filter.signal === 'all' ? null : (SIGNALS.find((option) => option.key === filter.signal)?.label ?? null)
  const quadrantLabel = filter.quadrant === null ? null : QUADRANT_NAME[filter.quadrant]
  const parts = [signalLabel, filter.product, quadrantLabel].filter((part) => part !== null)
  return (
    <div
      role="status"
      aria-label="목록 범위"
      className="flex min-h-7 flex-wrap items-center gap-2 text-[13px] text-ink-2"
    >
      {total === 0 ? null : parts.length === 0 ? (
        <span>
          주목 목록 <b className="text-ink">{total}건</b> · 기한 임박을 먼저, 나머지는 점수 높은 순
        </span>
      ) : (
        <>
          <span>
            <b className="text-ink">{parts.join(' · ')}</b> — {total}건 중 <b className="text-ink">{shown}건</b>
          </span>
          <button
            type="button"
            onClick={clear}
            className="rounded-full border border-axis bg-surface px-2.5 py-0.5 text-xs font-semibold text-ink"
          >
            필터 해제 ✕
          </button>
        </>
      )}
    </div>
  )
}
