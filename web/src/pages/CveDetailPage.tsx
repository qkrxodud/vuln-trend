import { useParams } from 'react-router'
import { CveDetailView } from '../components/detail-page/CveDetailView.tsx'
import { SiteFooter } from '../components/footer/SiteFooter.tsx'
import { SiteHeader } from '../components/header/SiteHeader.tsx'

// 상세 페이지 /cve/:cveId. 주소로 바로 들어오거나 "상세 페이지 열기"로 온다 (D8)
export const CveDetailPage = () => {
  const { cveId } = useParams()
  return (
    <div className="mx-auto flex max-w-4xl flex-col gap-7 px-4 pt-5 pb-10 sm:px-7 sm:pt-7">
      <SiteHeader />
      <main>{cveId === undefined ? null : <CveDetailView cveId={cveId} />}</main>
      <SiteFooter />
    </div>
  )
}
