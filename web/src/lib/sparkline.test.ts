import { describe, expect, it } from 'vitest'
import { niceCeil, sparkline } from './sparkline.ts'

const SIZE = { width: 100, height: 40, pad: 5 }

describe('눈금 상한 올림', () => {
  it('10의 거듭제곱 절반 단위로 올린다', () => {
    expect(niceCeil(1983)).toBe(2000)
    expect(niceCeil(530)).toBe(550)
    expect(niceCeil(42)).toBe(45)
    expect(niceCeil(0)).toBe(1)
  })
})

describe('KPI 스파크라인', () => {
  it('값이 하나뿐이면 선을 그리지 않는다', () => {
    expect(sparkline([10], false, SIZE)).toBeNull()
  })

  it('왼쪽 끝에서 오른쪽 끝까지 고르게 놓고, 큰 값일수록 위에 그린다', () => {
    const line = sparkline([0, 10, 20], false, SIZE)

    expect(line?.solid).toMatch(/^M5\.0 35\.0 L50\.0 /)
    expect(line?.end.x).toBe(95)
    expect(line?.end.y).toBeLessThan(35)
  })

  it('오늘이 집계 중이면 실선은 어제에서 끝나고, 오늘은 점선과 빈 점으로 따로 그린다', () => {
    const line = sparkline([10, 10, 3], true, SIZE)

    expect(line?.end.x).toBe(50)
    expect(line?.today?.x).toBe(95)
    expect(line?.partial).toMatch(/^M50\.0 .* L95\.0 /)
  })

  it('오늘이 완결이면 점선과 빈 점이 없다', () => {
    const line = sparkline([10, 10, 3], false, SIZE)

    expect(line?.partial).toBeNull()
    expect(line?.today).toBeNull()
  })

  it('급증일은 위에서 잘라 평소의 흔들림이 납작해지지 않게 한다', () => {
    const line = sparkline([100, 110, 2000, 90, 105], false, SIZE)

    expect(line?.solid).toContain(' L50.0 5.0 ')
  })

  it('평소(완결일 중앙값) 가로선 높이를 함께 준다', () => {
    const line = sparkline([100, 100, 100], false, SIZE)

    expect(line?.usualY).toBe(line?.end.y)
  })
})
