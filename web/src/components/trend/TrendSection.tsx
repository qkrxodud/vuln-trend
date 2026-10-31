import { useNavigate } from 'react-router'
import { useTrendIndex } from '../../hooks/use-data.ts'
import { PANEL_ROUTE_STATE } from '../../lib/routes.ts'
import { DetailPanel } from '../detail/DetailPanel.tsx'
import { LoadableView } from '../state/LoadableView.tsx'
import { TrendList } from './TrendList.tsx'

interface TrendSectionProps {
  /** 주소의 /cve/:cveId. 없으면 목록 맨 위 취약점을 패널에 보인다 */
  selectedId: string | null
}

// 목록 + 상세 패널(데스크톱 ≥1024px). 고르면 주소를 /cve/ID 로 바꾼다 (D8)
export const TrendSection = ({ selectedId }: TrendSectionProps) => {
  const trendIndex = useTrendIndex()
  const navigate = useNavigate()
  const select = (cveId: string) => navigate(`/cve/${cveId}`, { replace: true, state: PANEL_ROUTE_STATE })

  return (
    <LoadableView loadable={trendIndex} label="트렌드 목록">
      {(rows) => {
        const shownId = selectedId ?? rows[0]?.id ?? null
        return (
          <div className="grid items-start gap-5 lg:grid-cols-2">
            <TrendList rows={rows} selectedId={shownId} onSelect={select} />
            {shownId === null ? null : (
              <div className="hidden lg:block lg:sticky lg:top-4">
                <DetailPanel cveId={shownId} />
              </div>
            )}
          </div>
        )
      }}
    </LoadableView>
  )
}
