import { act, renderHook } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { setViewportWidth } from '../test/fake/fake-media.ts'
import { useIsDesktop } from './use-media-query.ts'

describe('데스크톱 폭 판단 (목록 옆 패널 기준 1024px)', () => {
  it('1024px 이상이면 데스크톱이다', () => {
    setViewportWidth(1024)

    expect(renderHook(() => useIsDesktop()).result.current).toBe(true)
  })

  it('1024px 미만이면 데스크톱이 아니다', () => {
    setViewportWidth(1023)

    expect(renderHook(() => useIsDesktop()).result.current).toBe(false)
  })

  it('창 크기가 바뀌면 바로 따라간다', () => {
    setViewportWidth(1280)
    const { result } = renderHook(() => useIsDesktop())

    act(() => setViewportWidth(375))

    expect(result.current).toBe(false)
  })
})
