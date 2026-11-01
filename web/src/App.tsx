import { lazy, Suspense } from 'react'
import { Route, Routes, useLocation, useParams } from 'react-router'
import { LoadingState } from './components/state/LoadingState.tsx'
import { MineProvider } from './hooks/MineProvider.tsx'
import { isPanelState } from './lib/routes.ts'
import { CveDetailPage } from './pages/CveDetailPage.tsx'
import { HomePage } from './pages/HomePage.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'

// 메인(/)과 목록에서 고른 /cve/:cveId 는 같은 화면이다. 같은 컴포넌트로 그려 목록 상태(보기·더 보기)를 지킨다
const MainRoute = () => {
  const { cveId } = useParams()
  const location = useLocation()
  if (cveId === undefined) return <HomePage selectedId={null} />
  if (isPanelState(location.state)) return <HomePage selectedId={cveId} />
  return <CveDetailPage />
}

// 최근 7일 새 CVE(/new)는 따로 청크로 나눠 메인 첫 로드를 늘리지 않는다
const NewCvesPage = lazy(() => import('./pages/NewCvesPage.tsx'))
const MinePage = lazy(() => import('./pages/MinePage.tsx'))
const CategoryPage = lazy(() => import('./pages/CategoryPage.tsx'))

const RouteLoading = ({ label }: { label: string }) => (
  <div className="min-h-screen">
    <LoadingState label={label} />
  </div>
)

const NewCvesRoute = () => (
  <Suspense fallback={<RouteLoading label="최근 7일 새 CVE" />}>
    <NewCvesPage />
  </Suspense>
)

// 라우트는 기획서 8장: 메인 / 상세 /cve/:id. 최근 7일 /new, 내 제품 /mine, 분류 /type/:key·/family/:key
// 내 제품(localStorage) 상태는 모든 화면이 같이 쓴다
const App = () => (
  <MineProvider>
    <Routes>
      <Route path="/" element={<MainRoute />} />
      <Route path="/cve/:cveId" element={<MainRoute />} />
      <Route path="/new" element={<NewCvesRoute />} />
      <Route
        path="/mine"
        element={
          <Suspense fallback={<RouteLoading label="내 제품" />}>
            <MinePage />
          </Suspense>
        }
      />
      <Route
        path="/type/:key"
        element={
          <Suspense fallback={<RouteLoading label="분류" />}>
            <CategoryPage axis="type" />
          </Suspense>
        }
      />
      <Route
        path="/family/:key"
        element={
          <Suspense fallback={<RouteLoading label="분류" />}>
            <CategoryPage axis="family" />
          </Suspense>
        }
      />
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  </MineProvider>
)

export default App
