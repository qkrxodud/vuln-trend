import { Route, Routes, useLocation, useParams } from 'react-router'
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

// 라우트는 기획서 8장: 메인 / 상세 /cve/:id. 제품·생태계 페이지는 2단계
const App = () => (
  <Routes>
    <Route path="/" element={<MainRoute />} />
    <Route path="/cve/:cveId" element={<MainRoute />} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes>
)

export default App
