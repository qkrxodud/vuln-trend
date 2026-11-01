import { useNavigate } from 'react-router'
import { PANEL_ROUTE_STATE } from '../lib/routes.ts'
import { useIsDesktop } from './use-media-query.ts'

// 취약점 고르기 (D8·5.10). 데스크톱은 주소만 /cve/ID 로 바꾸고 옆 패널에 보인다.
// 좁은 화면은 상세 페이지로 가고, 뒤로 가기로 돌아오게 기록을 남긴다. 목록·KPI 히어로가 같이 쓴다
export const useSelectCve = (): ((cveId: string) => void) => {
  const isDesktop = useIsDesktop()
  const navigate = useNavigate()
  return (cveId: string) => {
    if (isDesktop) {
      navigate(`/cve/${cveId}`, { replace: true, state: PANEL_ROUTE_STATE })
      return
    }
    navigate(`/cve/${cveId}`)
  }
}
