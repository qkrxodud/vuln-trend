import { useMeta } from '../../hooks/use-data.ts'
import { useTrendFilter } from '../../hooks/use-trend-filter.ts'
import type { TrendFilter } from '../../lib/trend-filter.ts'
import type { KpiCounts } from '../../types/meta.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { KpiCard } from './KpiCard.tsx'

interface KpiDefinition {
  key: keyof KpiCounts
  label: string
  description: string
  tone?: 'kev'
  /** 이 카드를 누르면 걸리는 목록 필터 */
  filter?: Exclude<TrendFilter, 'all'>
}

// 기획서 8장 KPI 표. 1~3 은 24시간 롤링, 4 는 전체 스냅샷. 4번은 2단계에서 "PoC 공개"로 교체 (D12)
const KPI_DEFINITIONS: KpiDefinition[] = [
  { key: 'new', label: '24시간 신규', description: '최근 24시간 공개된 CVE' },
  { key: 'kev_new', label: 'KEV 신규', description: '24시간 내 CISA 악용 목록 등재', tone: 'kev', filter: 'kev' },
  { key: 'critical', label: 'Critical', description: '24시간 신규 중 CVSS 9.0 이상', filter: 'critical' },
  { key: 'high_risk', label: '고위험 (EPSS 0.5+)', description: '전체 CVE 중 30일 내 악용 확률 50% 이상' },
]

export const KpiCards = () => {
  const meta = useMeta()
  const { filter, setFilter } = useTrendFilter()

  const toggleFor = (definition: KpiDefinition) => {
    const cardFilter = definition.filter
    if (cardFilter === undefined) return undefined
    const pressed = filter === cardFilter
    return { pressed, onToggle: () => setFilter(pressed ? 'all' : cardFilter) }
  }

  return (
    <section aria-label="오늘 현황">
      <LoadableView loadable={meta} label="오늘 현황">
        {(data) => (
          <div className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,220px),1fr))] gap-4">
            {KPI_DEFINITIONS.map((definition) => (
              <KpiCard
                key={definition.key}
                label={definition.label}
                description={definition.description}
                today={data.today[definition.key]}
                yesterday={data.yesterday[definition.key]}
                tone={definition.tone}
                toggle={toggleFor(definition)}
              />
            ))}
          </div>
        )}
      </LoadableView>
    </section>
  )
}
