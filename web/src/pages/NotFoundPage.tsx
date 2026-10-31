import { Link } from 'react-router'

export const NotFoundPage = () => (
  <main className="mx-auto max-w-4xl p-6">
    <p>페이지를 찾을 수 없습니다.</p>
    <Link to="/" className="underline">
      메인으로
    </Link>
  </main>
)
