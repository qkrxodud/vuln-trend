import { useCallback, useSyncExternalStore } from 'react'

// 기획서 8장 반응형: 데스크톱(≥1024px)은 목록 + sticky 상세 패널 2열, 그 아래는 1열·패널 없음
export const DESKTOP_QUERY = '(min-width: 1024px)'

export const useMediaQuery = (query: string): boolean => {
  const subscribe = useCallback(
    (onChange: () => void) => {
      const list = window.matchMedia(query)
      list.addEventListener('change', onChange)
      return () => list.removeEventListener('change', onChange)
    },
    [query],
  )
  return useSyncExternalStore(subscribe, () => window.matchMedia(query).matches)
}

/** 목록 옆 상세 패널을 쓰는 데스크톱 폭(≥1024px)인가 */
export const useIsDesktop = (): boolean => useMediaQuery(DESKTOP_QUERY)
