import { describe, expect, it } from 'vitest'
import { formatMonthDay } from './charts.ts'

describe('차트 날짜 표기', () => {
  it('날짜는 "월/일"로 적는다', () => {
    expect(formatMonthDay('2026-10-30')).toBe('10/30')
    expect(formatMonthDay('2026-11-03')).toBe('11/3')
  })
})
