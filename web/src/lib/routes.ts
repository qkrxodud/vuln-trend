// /cve/:cveId 는 두 가지로 쓰인다 (D8).
// 목록에서 고르면 이 state 를 실어 메인 화면 + 옆 패널로, 주소로 바로 들어오면 상세 페이지로 보인다
/** 목록 영역 id. KPI 히어로에서 고르면 여기로 내린다 */
export const TREND_SECTION_ID = 'trend-list'

export const PANEL_ROUTE_STATE = { view: 'panel' } as const

export const isPanelState = (state: unknown): boolean =>
  typeof state === 'object' && state !== null && (state as { view?: unknown }).view === 'panel'
