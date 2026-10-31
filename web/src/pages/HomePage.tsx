import { SiteHeader } from '../components/header/SiteHeader.tsx'
import { KpiCards } from '../components/kpi/KpiCards.tsx'
import { LoadableView } from '../components/state/LoadableView.tsx'
import { useTrendIndex } from '../hooks/use-data.ts'

// 메인 페이지 — 5.5~5.7 에서 차트·목록·상세 패널을 채운다
export const HomePage = () => {
  const trendIndex = useTrendIndex()
  return (
    <div className="mx-auto flex max-w-[1280px] flex-col gap-7 px-7 pt-7 pb-10">
      <SiteHeader />
      <main className="flex flex-col gap-7">
        <KpiCards />
        <LoadableView loadable={trendIndex} label="트렌드 목록">
          {(rows) => <p className="text-sm text-ink-muted">트렌드 {rows.length}건</p>}
        </LoadableView>
      </main>
    </div>
  )
}
