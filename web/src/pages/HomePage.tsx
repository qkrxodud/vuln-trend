import { ChartsSection } from '../components/charts/ChartsSection.tsx'
import { SiteHeader } from '../components/header/SiteHeader.tsx'
import { KpiCards } from '../components/kpi/KpiCards.tsx'
import { TrendSection } from '../components/trend/TrendSection.tsx'

// 메인 페이지: 헤더 → KPI → 차트 → 목록. 5.7 에서 목록 옆 상세 패널을 붙인다
export const HomePage = () => (
  <div className="mx-auto flex max-w-[1280px] flex-col gap-7 px-7 pt-7 pb-10">
    <SiteHeader />
    <main className="flex flex-col gap-7">
      <KpiCards />
      <ChartsSection />
      <TrendSection />
    </main>
  </div>
)
