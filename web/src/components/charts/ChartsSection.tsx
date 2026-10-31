import { useStats } from '../../hooks/use-data.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { DailyNewChart } from './DailyNewChart.tsx'
import { SeverityDonut } from './SeverityDonut.tsx'

// 차트 줄: 14일 막대(달력일) + 7일 심각도 도넛(미평가 포함). stats.json 하나로 그린다
export const ChartsSection = () => {
  const stats = useStats()
  return (
    <section aria-label="추이 차트">
      <LoadableView loadable={stats} label="추이 차트">
        {(data) => (
          <div className="flex flex-wrap gap-8">
            <DailyNewChart daily={data.daily} />
            <SeverityDonut distribution={data.severity_7d} />
          </div>
        )}
      </LoadableView>
    </section>
  )
}
