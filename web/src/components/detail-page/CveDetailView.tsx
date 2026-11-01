import { Link } from 'react-router'
import { useCveDetail } from '../../hooks/use-data.ts'
import { DetailBody } from '../detail/DetailBody.tsx'
import { useDetailContext } from '../../hooks/use-detail-context.ts'
import { NoDetail } from '../detail/NoDetail.tsx'
import { LoadableView } from '../state/LoadableView.tsx'

// /cve/:cveId 상세 페이지 본문 (WBS 5.8). 좁은 화면에서 목록을 누르면 여기로 온다 (사용자 결정 Q3).
// 내용은 옆 패널과 같은 v6 상세이고, CVE-ID 를 h1 로 둔다
export const CveDetailView = ({ cveId }: { cveId: string }) => {
  const detail = useCveDetail(cveId)
  const { today, rank } = useDetailContext(cveId)
  return (
    <div className="flex min-h-screen flex-col gap-5">
      <Link to="/" className="self-start text-sm text-ink-2 hover:text-ink">
        ← 메인으로
      </Link>
      <LoadableView loadable={detail} label="취약점 상세">
        {(data) => (
          <article className="flex flex-col gap-4 rounded-xl border border-border bg-surface p-5 sm:p-7">
            <title>{`${cveId} — VulnTrend`}</title>
            <h1 className="m-0 font-mono text-2xl font-bold text-ink">{cveId}</h1>
            {data === null || today === null ? (
              <NoDetail cveId={cveId} />
            ) : (
              <DetailBody detail={data} today={today} rank={rank} level="page" />
            )}
          </article>
        )}
      </LoadableView>
    </div>
  )
}
