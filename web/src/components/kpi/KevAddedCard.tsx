import { formatCount } from '../../lib/kpi.ts'
import { kevAddedCounts } from '../../lib/urgency.ts'
import type { Meta } from '../../types/meta.ts'
import type { TrendRow } from '../../types/trend.ts'
import { KpiArticle, KpiValue } from './KpiCard.parts.tsx'

const SCOPE_LABEL = { all: '전체 KEV 기준', watchlist: '주목 목록 기준' } as const

// "최근 7일 새로 KEV 등재". 배치의 전체 집계(kev_added_7d, KST 달력일 [오늘-6, 오늘])가 먼저,
// 없으면 주목 목록 행으로 센다 (v6 G1). meta.today.kev_new 는 UTC 24시간 롤링이라 같은 줄에 섞지 않는다
export const KevAddedCard = ({ meta, rows, today }: { meta: Meta; rows: TrendRow[]; today: string }) => {
  const counts = kevAddedCounts(meta, rows, today)
  return (
    <KpiArticle id="kpi-kev-added" title="최근 7일 새로 KEV 등재">
      {counts === null ? (
        <>
          <p className="py-1.5 text-base font-semibold text-ink-2">등재일 정보 없음</p>
          <p className="text-[13px] leading-[1.45] text-ink-2">
            최근 24시간 등재 <b className="font-semibold text-ink">{formatCount(meta.today.kev_new)}</b>건
          </p>
        </>
      ) : (
        <>
          <KpiValue value={formatCount(counts.recent)} />
          <p className="text-[13px] leading-[1.45] text-ink-2">
            그 전 7일 <b className="font-semibold text-ink">{formatCount(counts.previous)}</b>건
            <br />
            CISA 가 실제 악용을 확인해 새로 올린 것 · {SCOPE_LABEL[counts.scope]}
          </p>
        </>
      )}
    </KpiArticle>
  )
}
