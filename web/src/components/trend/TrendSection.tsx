import { useMeta, useTrendIndex } from '../../hooks/use-data.ts'
import { useListFilter } from '../../hooks/use-list-filter.ts'
import { useIsDesktop } from '../../hooks/use-media-query.ts'
import { useSelectCve } from '../../hooks/use-select-cve.ts'
import { formatKstDateTime } from '../../lib/kst-time.ts'
import { defaultSelectedId, listTiers } from '../../lib/list-model.ts'
import { TREND_SECTION_ID } from '../../lib/routes.ts'
import { referenceDate } from '../../lib/urgency.ts'
import type { Meta } from '../../types/meta.ts'
import type { TrendRow } from '../../types/trend.ts'
import { DetailPanel } from '../detail/DetailPanel.tsx'
import { LoadableView } from '../state/LoadableView.tsx'
import { ListSummary, ProductFilter, SignalFilter } from '../watch/ListFilters.tsx'
import { WatchList } from '../watch/WatchList.tsx'

interface TrendSectionProps {
  /** 주소의 /cve/:cveId. 없으면 목록 맨 위 취약점을 패널에 보인다 */
  selectedId: string | null
}

const EMPTY = 'm-0 rounded-[10px] border border-dashed border-axis p-6 text-center text-sm leading-relaxed text-ink-2'

const WatchSection = ({ meta, rows, selectedId }: { meta: Meta; rows: TrendRow[]; selectedId: string | null }) => {
  const { filter, clear } = useListFilter()
  const isDesktop = useIsDesktop()
  const select = useSelectCve()
  const today = referenceDate(meta.updated_at)
  const tiers = listTiers(rows, filter, today)
  const shownId = selectedId ?? defaultSelectedId(rows, today)

  const body = () => {
    if (rows.length === 0) {
      return (
        <p className={EMPTY}>
          이번 수집에서 주목할 취약점이 없습니다.
          <br />
          2시간마다 다시 확인합니다.
        </p>
      )
    }
    if (tiers.total === 0) {
      return (
        <div className={EMPTY}>
          <p className="m-0">조건에 맞는 취약점이 없습니다.</p>
          <button
            type="button"
            onClick={clear}
            className="mt-2 rounded-full border border-axis bg-surface px-2.5 py-0.5 text-xs font-semibold text-ink"
          >
            필터 해제
          </button>
        </div>
      )
    }
    return (
      <WatchList
        key={`${filter.signal}|${filter.product ?? ''}`}
        tiers={tiers}
        allRows={rows}
        signal={filter.signal}
        today={today}
        selectedId={isDesktop ? shownId : null}
        onSelect={select}
      />
    )
  }

  return (
    <section id={TREND_SECTION_ID} aria-labelledby="watch-title" className="flex scroll-mt-4 flex-col gap-3.5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h2 id="watch-title" className="m-0 text-xl font-extrabold tracking-tight text-ink">
            지금 주목할 취약점
          </h2>
          <p className="mt-1 text-[13px] text-ink-2">
            트렌드 점수 기준 상위 {rows.length}건 · 기준 {formatKstDateTime(meta.updated_at)}
          </p>
        </div>
        {rows.length === 0 ? null : <SignalFilter rows={rows} today={today} />}
      </div>
      {rows.length === 0 ? null : <ProductFilter rows={rows} today={today} />}
      <ListSummary total={rows.length} shown={tiers.total} />
      <div className="grid items-start gap-5 lg:grid-cols-2">
        {body()}
        {isDesktop && shownId !== null ? (
          <div className="sticky top-4">
            <DetailPanel cveId={shownId} />
          </div>
        ) : null}
      </div>
    </section>
  )
}

// 지금 주목할 취약점 (design/v6 c-list): 신호·제품 필터 → 기한 임박 고정 묶음 → 나머지 점수순.
// 데스크톱(≥1024px): 목록 + 상세 패널, 고르면 주소만 /cve/ID 로 바꾼다 (D8).
// 좁은 화면: 1열·패널 없음, 고르면 상세 페이지로 간다 (5.10, 사용자 결정 Q3)
export const TrendSection = ({ selectedId }: TrendSectionProps) => {
  const meta = useMeta()
  const trendIndex = useTrendIndex()
  return (
    <LoadableView loadable={meta} label="트렌드 목록">
      {(metaData) => (
        <LoadableView loadable={trendIndex} label="트렌드 목록">
          {(rows) => <WatchSection meta={metaData} rows={rows} selectedId={selectedId} />}
        </LoadableView>
      )}
    </LoadableView>
  )
}
