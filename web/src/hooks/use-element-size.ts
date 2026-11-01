import { useLayoutEffect, useRef, useState } from 'react'

export interface ElementSize {
  width: number
  height: number
}

// 요소 크기를 따라간다. ResizeObserver 가 없는 환경(시험의 jsdom)에서는 처음 값을 그대로 쓴다
export const useElementSize = <T extends HTMLElement>(fallback: ElementSize) => {
  const ref = useRef<T>(null)
  const [size, setSize] = useState<ElementSize>(fallback)
  // 그리기 전에 한 번 재서 처음 값(fallback)으로 그렸다가 바뀌며 밀리는 일을 막는다
  useLayoutEffect(() => {
    const element = ref.current
    if (element === null || typeof ResizeObserver === 'undefined') return
    const { width, height } = element.getBoundingClientRect()
    if (width > 0) setSize({ width, height })
    const observer = new ResizeObserver(([entry]) => {
      const { width, height } = entry.contentRect
      setSize((current) => (current.width === width && current.height === height ? current : { width, height }))
    })
    observer.observe(element)
    return () => observer.disconnect()
  }, [])
  return { ref, size }
}
