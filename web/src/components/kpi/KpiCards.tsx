import { useMeta, useStats, useTrendIndex } from '../../hooks/use-data.ts'
import { referenceDate } from '../../lib/urgency.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { KevAddedCard } from './KevAddedCard.tsx'
import { TodayNewCard } from './TodayNewCard.tsx'
import { UrgentHeroCard } from './UrgentHeroCard.tsx'

// 오늘 요약 (design/v6 a-top): 히어로 "CISA 조치 기한 임박" + 오늘 새 CVE + 최근 7일 KEV 등재.
// 기준일은 meta.updated_at 의 KST 날짜. 넓은 화면 1.7:1:1, 900px 이하 2칸(히어로 한 줄), 520px 이하 1칸
export const KpiCards = ({ selectedId = null }: { selectedId?: string | null }) => {
  const meta = useMeta()
  const index = useTrendIndex()
  const stats = useStats()
  return (
    <section
      aria-label="오늘 요약"
      className="grid grid-cols-1 gap-4 min-[521px]:grid-cols-2 min-[901px]:grid-cols-[minmax(0,1.7fr)_repeat(2,minmax(0,1fr))]"
    >
      <LoadableView loadable={meta} label="오늘 요약">
        {(metaData) => {
          const today = referenceDate(metaData.updated_at)
          return (
            <>
              <LoadableView loadable={index} label="기한 임박">
                {(rows) => <UrgentHeroCard rows={rows} today={today} selectedId={selectedId} />}
              </LoadableView>
              <LoadableView loadable={stats} label="오늘 새 CVE">
                {(data) => <TodayNewCard meta={metaData} daily={data.daily} />}
              </LoadableView>
              <LoadableView loadable={index} label="KEV 등재">
                {(rows) => <KevAddedCard meta={metaData} rows={rows} today={today} />}
              </LoadableView>
            </>
          )
        }}
      </LoadableView>
    </section>
  )
}
