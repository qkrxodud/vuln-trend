import { describe, expect, it } from 'vitest'
import { describeDelta, formatCount, formatDelta } from './kpi.ts'

describe('KPI 숫자 표기', () => {
  it('천 단위마다 쉼표를 찍는다', () => {
    expect(formatCount(1234)).toBe('1,234')
  })
})

describe('어제 대비 증감', () => {
  it('늘었으면 "+" 를 붙인다', () => {
    expect(formatDelta(142, 133)).toBe('+9')
  })

  it('줄었으면 "−" 로 적는다', () => {
    expect(formatDelta(1, 3)).toBe('−2')
  })

  it('같으면 0 으로 적는다', () => {
    expect(formatDelta(5, 5)).toBe('0')
  })

  it('화면 읽기 프로그램에는 어제 값과 함께 말로 알린다', () => {
    expect(describeDelta(142, 133)).toBe('어제 133건보다 9건 늘었습니다')
    expect(describeDelta(1, 3)).toBe('어제 3건보다 2건 줄었습니다')
    expect(describeDelta(5, 5)).toBe('어제 5건과 같습니다')
  })
})
