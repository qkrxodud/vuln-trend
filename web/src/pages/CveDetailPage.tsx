import { useParams } from 'react-router'

// 상세 페이지 — 5.8 에서 채운다
export const CveDetailPage = () => {
  const { cveId } = useParams()
  return (
    <main className="mx-auto max-w-4xl p-6">
      <h1 className="font-mono text-2xl font-semibold">{cveId}</h1>
    </main>
  )
}
