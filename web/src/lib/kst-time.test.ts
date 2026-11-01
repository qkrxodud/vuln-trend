import { describe, expect, it } from 'vitest'
import { formatKstDate, formatKstDateTime, formatKstTime, formatTimeAgo, isStale } from './kst-time.ts'

const UPDATED = '2026-10-30T09:00:00+09:00'
const hoursAfter = (hours: number): Date => new Date(Date.parse(UPDATED) + hours * 3_600_000)

describe('갱신 뒤 지난 시간', () => {
  it('1분이 안 됐으면 "방금 전"', () => {
    expect(formatTimeAgo(UPDATED, hoursAfter(0.01))).toBe('방금 전')
  })

  it('1시간이 안 됐으면 분으로 적는다', () => {
    expect(formatTimeAgo(UPDATED, hoursAfter(0.5))).toBe('30분 전')
  })

  it('이틀이 안 됐으면 시간으로 적는다', () => {
    expect(formatTimeAgo(UPDATED, hoursAfter(3))).toBe('3시간 전')
    expect(formatTimeAgo(UPDATED, hoursAfter(47.9))).toBe('47시간 전')
  })

  it('이틀이 넘으면 날로 적는다', () => {
    expect(formatTimeAgo(UPDATED, hoursAfter(72))).toBe('3일 전')
  })

  it('방문자 시계가 갱신 시각보다 앞서 있어도 "방금 전"으로 적는다', () => {
    expect(formatTimeAgo(UPDATED, hoursAfter(-2))).toBe('방금 전')
  })
})

describe('데이터가 오래됐는지 판단 (배치 실패를 방문자가 아는 유일한 수단)', () => {
  it('갱신 뒤 36시간까지는 오래되지 않았다', () => {
    expect(isStale(UPDATED, hoursAfter(36))).toBe(false)
  })

  it('갱신 뒤 36시간을 넘으면 오래됐다', () => {
    expect(isStale(UPDATED, hoursAfter(36.01))).toBe(true)
  })
})

describe('오늘 막대가 어느 날인지 (한국 달력일)', () => {
  it('UTC 로는 전날이어도 한국 날짜와 시각으로 적는다', () => {
    expect(formatKstDate('2026-10-31T15:30:53Z')).toBe('2026-11-01')
    expect(formatKstTime('2026-10-31T15:30:53Z')).toBe('00:30')
  })

  it('한국 시각으로 받은 갱신 시각도 그대로 읽는다', () => {
    expect(formatKstDate('2026-11-02T09:00:00+09:00')).toBe('2026-11-02')
    expect(formatKstTime('2026-11-02T09:00:00+09:00')).toBe('09:00')
  })
})

describe('헤더의 마지막 갱신 시각', () => {
  it('한국 날짜와 시각에 KST 를 붙여 적는다', () => {
    expect(formatKstDateTime('2026-11-01T09:49:00Z')).toBe('2026-11-01 18:49 KST')
  })
})
