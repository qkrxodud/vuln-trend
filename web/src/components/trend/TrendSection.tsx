import { useNavigate } from 'react-router'
import { useTrendIndex } from '../../hooks/use-data.ts'
import { useIsDesktop } from '../../hooks/use-media-query.ts'
import { PANEL_ROUTE_STATE } from '../../lib/routes.ts'
import { DetailPanel } from '../detail/DetailPanel.tsx'
import { LoadableView } from '../state/LoadableView.tsx'
import { TrendList } from './TrendList.tsx'

interface TrendSectionProps {
  /** 주소의 /cve/:cveId. 없으면 목록 맨 위 취약점을 패널에 보인다 */
  selectedId: string | null
}

// 데스크톱(≥1024px): 목록 + 상세 패널, 고르면 주소만 /cve/ID 로 바꾼다 (D8)
// 좁은 화면: 1열·패널 없음, 고르면 상세 페이지로 간다. 뒤로 가기로 목록에 돌아오게 기록을 남긴다 (5.10)
export const TrendSection = ({ selectedId }: TrendSectionProps) => {
  const trendIndex = useTrendIndex()
  const isDesktop = useIsDesktop()
  const navigate = useNavigate()

  const select = (cveId: string) => {
    if (isDesktop) {
      navigate(`/cve/${cveId}`, { replace: true, state: PANEL_ROUTE_STATE })
      return
    }
    navigate(`/cve/${cveId}`)
  }

  return (
    <LoadableView loadable={trendIndex} label="트렌드 목록">
      {(rows) => {
        const shownId = selectedId ?? rows[0]?.id ?? null
        return (
          <div className="grid items-start gap-5 lg:grid-cols-2">
            <TrendList rows={rows} selectedId={isDesktop ? shownId : null} onSelect={select} />
            {isDesktop && shownId !== null ? (
              <div className="sticky top-4">
                <DetailPanel cveId={shownId} />
              </div>
            ) : null}
          </div>
        )
      }}
    </LoadableView>
  )
}
