import { ChartsSection } from '../components/charts/ChartsSection.tsx'
import { SiteHeader } from '../components/header/SiteHeader.tsx'
import { KpiCards } from '../components/kpi/KpiCards.tsx'
import { TrendSection } from '../components/trend/TrendSection.tsx'

// 메인 페이지: 헤더 → KPI → 차트 → 목록 + 상세 패널
export const HomePage = ({ selectedId }: { selectedId: string | null }) => (
  <div className="mx-auto flex max-w-[1280px] flex-col gap-7 px-4 pt-5 pb-10 sm:px-7 sm:pt-7">
    <SiteHeader />
    <main className="flex flex-col gap-7">
      <KpiCards />
      <ChartsSection />
      <TrendSection selectedId={selectedId} />
    </main>
  </div>
)
