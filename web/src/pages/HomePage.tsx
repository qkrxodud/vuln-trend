import { LoadableView } from '../components/state/LoadableView.tsx'
import { useMeta, useTrendIndex } from '../hooks/use-data.ts'

// 메인 페이지 — 5.3~5.7 에서 헤더·KPI·차트·목록·상세 패널을 채운다.
// 지금은 데이터 레이어(5.2)가 붙었는지 확인하는 최소 표시만 둔다
export const HomePage = () => {
  const meta = useMeta()
  const trendIndex = useTrendIndex()
  return (
    <main className="mx-auto max-w-6xl space-y-4 p-6">
      <h1 className="text-2xl font-semibold">VulnTrend</h1>
      <LoadableView loadable={meta} label="갱신 정보">
        {(data) => <p className="text-sm text-gray-600">마지막 갱신 {data.updated_at}</p>}
      </LoadableView>
      <LoadableView loadable={trendIndex} label="트렌드 목록">
        {(rows) => <p className="text-sm text-gray-600">트렌드 {rows.length}건</p>}
      </LoadableView>
    </main>
  )
}
