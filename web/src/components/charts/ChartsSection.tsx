import { useStats } from '../../hooks/use-data.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { CHARTS_LABEL, CHARTS_SLOT_CLASS } from './charts-slot.ts'
import { DailyNewChart } from './DailyNewChart.tsx'
import { SeverityDonut } from './SeverityDonut.tsx'

// 차트 줄: 14일 막대(달력일) + 7일 심각도 도넛(미평가 포함). stats.json 하나로 그린다
// 1024px 이상은 두 칸, 미만은 세로로 쌓는다. 구간마다 높이가 하나로 정해져야 지연 로드 자리(charts-slot)가 맞는다
export const ChartsSection = () => {
  const stats = useStats()
  return (
    <section aria-label={CHARTS_LABEL} className={CHARTS_SLOT_CLASS}>
      <LoadableView loadable={stats} label={CHARTS_LABEL}>
        {(data) => (
          <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_auto]">
            <DailyNewChart daily={data.daily} />
            <SeverityDonut distribution={data.severity_7d} />
          </div>
        )}
      </LoadableView>
    </section>
  )
}
