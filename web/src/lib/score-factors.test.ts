import { describe, expect, it } from 'vitest'
import { formatDecay, formatPoints, toScoreFactors } from './score-factors.ts'

const breakdown = { kev: 50, ransomware: 0, epss: 27.3, news: 0, poc: 0, cvss: 8.8, decay: -2.1 }

describe('점수 구성', () => {
  it('0 이 아닌 항만 순서대로 넣는다 (KEV → 랜섬웨어 → EPSS → 뉴스 → PoC → CVSS)', () => {
    expect(toScoreFactors(breakdown).map((factor) => factor.label)).toEqual([
      '실제 악용 (KEV)',
      '악용 확률 (EPSS)',
      '심각도 (CVSS)',
    ])
  })

  it('막대 길이는 그 항이 받을 수 있는 최대 점수 대비 비율이다', () => {
    expect(toScoreFactors(breakdown).map((factor) => factor.widthPercent)).toEqual([100, 91, 88])
  })

  it('항 점수는 "+" 를 붙이고, 정수가 아니면 소수 한 자리까지 적는다', () => {
    expect(formatPoints(50)).toBe('+50')
    expect(formatPoints(27.3)).toBe('+27.3')
  })

  it('시간 감쇠는 빠진 점수를 "−" 로 적고, 빠진 점수가 없으면 0 으로 적는다', () => {
    expect(formatDecay(-2.1)).toBe('−2.1')
    expect(formatDecay(0)).toBe('0')
  })
})
