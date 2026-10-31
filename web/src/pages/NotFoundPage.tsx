import { Link } from 'react-router'
import { SiteFooter } from '../components/footer/SiteFooter.tsx'

export const NotFoundPage = () => (
  <div className="mx-auto flex max-w-4xl flex-col gap-7 px-4 pt-5 pb-10 sm:px-7 sm:pt-7">
    <main>
      <p>페이지를 찾을 수 없습니다.</p>
      <Link to="/" className="underline">
        메인으로
      </Link>
    </main>
    <SiteFooter />
  </div>
)
