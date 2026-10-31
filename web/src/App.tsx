import { Route, Routes } from 'react-router'
import { CveDetailPage } from './pages/CveDetailPage.tsx'
import { HomePage } from './pages/HomePage.tsx'
import { NotFoundPage } from './pages/NotFoundPage.tsx'

// 라우트는 기획서 8장: 메인 / 상세 /cve/:id. 제품·생태계 페이지는 2단계
const App = () => (
  <Routes>
    <Route path="/" element={<HomePage />} />
    <Route path="/cve/:cveId" element={<CveDetailPage />} />
    <Route path="*" element={<NotFoundPage />} />
  </Routes>
)

export default App
