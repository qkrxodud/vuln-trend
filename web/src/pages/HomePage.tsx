import { SiteFooter } from '../components/footer/SiteFooter.tsx'
import { SiteHeader } from '../components/header/SiteHeader.tsx'
import { KpiCards } from '../components/kpi/KpiCards.tsx'
import { MineNewsLine } from '../components/mine/MineNewsLine.tsx'
import { ProductBars } from '../components/products/ProductBars.tsx'
import { QuadrantCard } from '../components/quadrants/QuadrantCard.tsx'
import { TrendCard } from '../components/trend-card/TrendCard.tsx'
import { NewCvesEntry } from '../components/trend/NewCvesEntry.tsx'
import { TrendSection } from '../components/trend/TrendSection.tsx'
import { MainReady } from '../components/state/MainReady.tsx'
import { ListFilterProvider } from '../hooks/ListFilterProvider.tsx'

// 메인 페이지 (design/v6): 헤더 → 오늘 요약(KPI) → 4칸 건수표 | 추이 → 제품 막대 → 목록 + 상세 패널 → 푸터 고지
export const HomePage = ({ selectedId }: { selectedId: string | null }) => (
  <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-4 pt-5 pb-10 sm:px-7 sm:pt-6">
    <SiteHeader />
    <ListFilterProvider>
      <main className="flex flex-col gap-4">
        <MainReady>
          <KpiCards selectedId={selectedId} />
          <MineNewsLine />
          {/* 차트 (design/v6 CONSENSUS 8절): 1행 4칸 건수표(3fr) | 추이(2fr), 2행 제품 막대(목록과 같은 820px 상한).
            1000px 이하는 4칸 건수표 → 추이 → 막대 한 줄 */}
          <div className="grid gap-4 min-[1001px]:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <QuadrantCard />
            <TrendCard />
          </div>
          <ProductBars />
          <TrendSection selectedId={selectedId} />
          <NewCvesEntry />
        </MainReady>
      </main>
    </ListFilterProvider>
    <SiteFooter />
  </div>
)
