import { useMeta, useTrendIndex } from '../../hooks/use-data.ts'
import { useListFilter } from '../../hooks/use-list-filter.ts'
import { productBars, SEVERITY_BUCKETS, type ProductBar, type SeverityBucket } from '../../lib/product-bars.ts'
import { referenceDate, URGENT_WITHIN_DAYS } from '../../lib/urgency.ts'
import type { TrendRow } from '../../types/trend.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { Diamond } from '../signal/UrgentBadge.tsx'

const COLOR: Record<SeverityBucket, string> = {
  CRITICAL: 'var(--color-sev-crit)',
  HIGH: 'var(--color-sev-high)',
  MEDIUM: 'var(--color-sev-med)',
  LOW: 'var(--color-sev-low)',
  NONE: 'var(--color-neutral-wash)',
}
const REST_SWATCH = { background: 'var(--color-neutral-wash)', boxShadow: 'inset 0 0 0 1px var(--color-axis)' }
const LABEL = Object.fromEntries(SEVERITY_BUCKETS.map((bucket) => [bucket.key, bucket.label])) as Record<
  SeverityBucket,
  string
>

// 이름 줄 / 막대 줄 2단 — 긴 제품명이 막대를 밀어내지 않게 (design/v6 b-products .row)
const ROW =
  'grid w-full grid-cols-[minmax(0,1fr)_auto] items-center gap-x-2 gap-y-1.5 rounded-md px-2 py-[7px] text-left'

const Bar = ({ bar, used, scale }: { bar: ProductBar; used: SeverityBucket[]; scale: number }) => (
  <span
    aria-hidden="true"
    className="flex h-3.5 min-w-1 shrink-0 gap-0.5"
    style={{ width: `${(bar.count / scale) * 100}%` }}
  >
    {used
      .filter((key) => (bar.bySeverity[key] ?? 0) > 0)
      .map((key) => (
        <i
          key={key}
          className="block h-full min-w-0.5 last:rounded-r"
          style={{ flex: `${bar.bySeverity[key]} 0 0`, background: COLOR[key] }}
        />
      ))}
  </span>
)

const ProductRow = ({ bar, used, scale }: { bar: ProductBar; used: SeverityBucket[]; scale: number }) => {
  const { filter, setProduct } = useListFilter()
  const pressed = filter.product === bar.name
  const severities = used
    .filter((key) => (bar.bySeverity[key] ?? 0) > 0)
    .map((key) => `${LABEL[key]} ${bar.bySeverity[key]}`)
    .join(', ')
  const urgent = bar.urgent > 0 ? `, CISA 기한 ${URGENT_WITHIN_DAYS}일 안 ${bar.urgent}건` : ''
  const action = pressed ? ', 목록 필터 적용 중 — 누르면 해제' : ', 누르면 목록을 이 제품으로 좁힘'
  const critical = bar.bySeverity.CRITICAL ?? 0
  return (
    <li>
      <button
        type="button"
        title={bar.name}
        aria-pressed={pressed}
        aria-label={`${bar.name}: ${bar.count}건(${severities})${urgent}${action}`}
        onClick={() => setProduct(pressed ? null : bar.name)}
        className={`${ROW} cursor-pointer transition-colors hover:bg-surface-sunk focus-visible:outline-2 focus-visible:outline-ink aria-pressed:bg-surface-sunk aria-pressed:shadow-[inset_3px_0_0_var(--color-ink)]`}
      >
        <span className="flex min-w-0 items-center gap-2">
          <b className={`min-w-0 truncate text-sm ${pressed ? 'font-bold' : 'font-semibold'}`}>{bar.name}</b>
          {bar.urgent > 0 ? (
            <span className="inline-flex shrink-0 items-center gap-[5px] rounded-full bg-urgent-wash py-px pr-2 pl-1.5 text-xs font-semibold whitespace-nowrap text-urgent-ink">
              <Diamond />
              D-{URGENT_WITHIN_DAYS} 안 {bar.urgent}
            </span>
          ) : null}
        </span>
        <span aria-hidden="true" className="text-right text-xs whitespace-nowrap text-ink-3">
          {pressed ? '해제 ✕' : '›'}
        </span>
        <span className="col-span-full flex min-w-0 items-center gap-2 pr-[110px]">
          <Bar bar={bar} used={used} scale={scale} />
          <span className="text-[13px] whitespace-nowrap text-ink-2 tabular-nums">
            <b className="text-sm text-ink">{bar.count}건</b>
            {critical > 0 ? ` · Critical ${critical}` : ''}
          </span>
        </span>
      </button>
    </li>
  )
}

const Bars = ({ rows, today }: { rows: TrendRow[]; today: string }) => {
  const { filter, setProduct } = useListFilter()
  const bars = productBars(rows, today)
  const share = bars.total === 0 ? 0 : Math.round((bars.topCount / bars.total) * 100)
  const answer = rows.length === 0 ? '' : bars.concentrated ? ' — 몇 제품에 몰려 있습니다' : ' — 고르게 퍼져 있습니다'
  return (
    <>
      <div>
        <h2 id="products-title" className="m-0 text-lg leading-[1.3] font-bold text-ink">
          어디에 몰려 있나{answer}
        </h2>
        <p className="mt-1 mb-0 text-[13px] text-ink-3">
          {rows.length === 0
            ? '주목 목록이 비어 있습니다'
            : `많은 ${bars.ranked.length}개 제품이 주목 ${bars.total}건 중 ${bars.topCount}건(${share}%)${
                bars.rest === null ? '' : ` · 나머지 ${bars.rest.products}개 제품이 ${bars.rest.count}건`
              } · 제품을 누르면 아래 목록을 좁힙니다`}
        </p>
      </div>
      <ul aria-label="막대 색 설명" className="m-0 flex list-none flex-wrap gap-x-3 gap-y-1.5 p-0 text-xs text-ink-2">
        {bars.usedSeverities.map((key) => (
          <li key={key} className="flex items-center gap-1.5 whitespace-nowrap">
            <span aria-hidden="true" className="size-2.5 shrink-0 rounded-sm" style={{ background: COLOR[key] }} />
            {LABEL[key]}
          </li>
        ))}
        {bars.rest === null ? null : (
          <li className="flex items-center gap-1.5 whitespace-nowrap">
            <span aria-hidden="true" className="size-2.5 shrink-0 rounded-sm" style={REST_SWATCH} />그 외 제품 합계
          </li>
        )}
        {bars.ranked.some((bar) => bar.urgent > 0) ? (
          <li className="flex items-center gap-1.5 whitespace-nowrap">
            <Diamond />
            CISA 기한 {URGENT_WITHIN_DAYS}일 안 건수
          </li>
        ) : null}
      </ul>
      <ol aria-label="제품별 건수" className="m-0 flex list-none flex-col gap-0.5 p-0">
        {bars.ranked.length === 0 ? <li className="px-2 py-3 text-sm text-ink-3">표시할 제품이 없습니다</li> : null}
        {bars.ranked.map((bar) => (
          <ProductRow key={bar.name} bar={bar} used={bars.usedSeverities} scale={bars.scale} />
        ))}
        {bars.rest === null ? null : (
          // 그 외 제품 합계: 같은 척도의 회색 막대, 누를 수 없음 (목록의 "그 외 제품" 선택 상자에서 고른다)
          <li className="mt-1 border-t border-grid pt-1">
            <div
              className={ROW}
              aria-label={`그 외 ${bars.rest.products}개 제품: 합계 ${bars.rest.count}건, 제품마다 ${bars.rest.maxPerProduct}건 이하 — 아래 목록의 제품 선택에서 고를 수 있음`}
            >
              <span className="flex min-w-0 items-center gap-2">
                <b className="min-w-0 truncate text-sm font-semibold text-ink-2">그 외 {bars.rest.products}개 제품</b>
                <span className="text-xs whitespace-nowrap text-ink-3">제품마다 {bars.rest.maxPerProduct}건 이하</span>
              </span>
              <span />
              <span className="col-span-full flex min-w-0 items-center gap-2 pr-[110px]">
                <span
                  aria-hidden="true"
                  className="flex h-3.5 min-w-1 shrink-0 rounded-r"
                  style={{ width: `${(bars.rest.count / bars.scale) * 100}%`, ...REST_SWATCH }}
                />
                <b className="text-sm whitespace-nowrap text-ink tabular-nums">{bars.rest.count}건</b>
              </span>
            </div>
          </li>
        )}
      </ol>
      <div className="mt-auto flex flex-wrap items-center justify-between gap-2 text-xs text-ink-3">
        <span aria-live="polite">
          {filter.product === null ? '전체 제품 표시 중' : `목록을 ${filter.product} 로 좁힌 상태`}
        </span>
        {filter.product === null ? null : (
          <button
            type="button"
            onClick={() => setProduct(null)}
            className="rounded-full bg-surface-sunk px-2.5 py-1 text-xs text-ink focus-visible:outline-2 focus-visible:outline-ink"
          >
            필터 해제 ✕
          </button>
        )}
      </div>
    </>
  )
}

// "어디에 몰려 있나" 제품 막대 (design/v6 b-products · CHART-REVIEW P1). 막대를 누르면 목록 제품 필터를 건다(공유 상태).
// 폭은 목록과 같은 820px 상한 — 6건짜리 막대가 넓게 늘어나 차이를 과장하지 않게 (CONSENSUS 8절)
export const ProductBars = () => {
  const meta = useMeta()
  const index = useTrendIndex()
  return (
    <section
      aria-labelledby="products-title"
      className="flex w-full max-w-[820px] min-w-0 flex-col gap-3 rounded-[10px] border border-border bg-surface p-5"
    >
      <LoadableView loadable={meta} label="제품 막대">
        {(metaData) => (
          <LoadableView loadable={index} label="제품 막대">
            {(rows) => <Bars rows={rows} today={referenceDate(metaData.updated_at)} />}
          </LoadableView>
        )}
      </LoadableView>
    </section>
  )
}
