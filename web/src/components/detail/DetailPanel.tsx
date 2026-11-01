import { Link } from 'react-router'
import { useCveDetail } from '../../hooks/use-data.ts'
import { useDetailContext } from '../../hooks/use-detail-context.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { DetailBody } from './DetailBody.tsx'
import { NoDetail } from './NoDetail.tsx'

const PANEL_LABEL = '선택한 취약점 상세'

// 데스크톱(≥1024px) 목록 옆 sticky 패널 (design/v6 c-list .vtc-detail). 고른 CVE 의 cve/*.json 을 그때 받는다 (WBS 5.7, D8)
export const DetailPanel = ({ cveId }: { cveId: string }) => {
  const detail = useCveDetail(cveId)
  const { today, rank } = useDetailContext(cveId)
  return (
    // 높이를 화면에 맞춰 고정하고 본문만 안에서 스크롤한다. 상세가 늦게 와도 아래 링크가 제자리에 있다 (밀림 0)
    <aside
      aria-label={PANEL_LABEL}
      className="flex h-[calc(100vh-32px)] min-w-0 flex-col gap-4 rounded-xl border border-border bg-surface p-[22px]"
    >
      <div className="-mx-1 flex min-h-0 flex-1 flex-col gap-5 overflow-auto px-1">
        <LoadableView loadable={detail} label="취약점 상세">
          {(data) =>
            data === null || today === null ? (
              <>
                <span className="font-mono text-lg font-bold text-ink">{cveId}</span>
                <NoDetail cveId={cveId} />
              </>
            ) : (
              <DetailBody detail={data} today={today} rank={rank} level="panel" />
            )
          }
        </LoadableView>
      </div>
      <Link
        to={`/cve/${cveId}`}
        className="flex shrink-0 items-center justify-center gap-1.5 rounded-[10px] bg-surface-sunk p-[11px] text-sm font-semibold text-ink hover:bg-border"
      >
        상세 페이지 열기 →
      </Link>
      <p aria-live="polite" className="sr-only">
        {detail.status === 'ready' ? `${cveId} 상세 표시 중` : ''}
      </p>
    </aside>
  )
}
