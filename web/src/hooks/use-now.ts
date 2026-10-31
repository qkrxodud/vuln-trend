import { useEffect, useState } from 'react'

const MINUTE_MS = 60_000

/** 지금 시각. "N분 전" 표기용으로 1분마다만 바뀐다 (초 카운터 없음, D5) */
export const useNow = (): Date => {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(new Date()), MINUTE_MS)
    return () => window.clearInterval(timer)
  }, [])
  return now
}
