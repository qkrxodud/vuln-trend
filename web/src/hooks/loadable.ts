// 비동기 데이터 한 덩어리의 화면 상태
export type Loadable<T> =
  /** 아직 요청하지 않음 (예: 검색창을 쓰기 전의 검색 색인) */
  | { status: 'idle' }
  | { status: 'loading' }
  | { status: 'error'; error: Error; retry: () => void }
  | { status: 'ready'; data: T }
