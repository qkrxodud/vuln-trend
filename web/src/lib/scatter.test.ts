import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import type { TrendRow } from '../types/trend.ts'
import {
  clusterMarks,
  cvssFraction,
  dueText,
  epssFraction,
  formatScatterEpss,
  isPlaced,
  labelPlan,
  layoutPoints,
  placeLabel,
  quadrantOf,
  textWidth,
} from './scatter.ts'

const TODAY = '2026-11-01'
const FRAME = { left: 0, right: 100, top: 0, bottom: 64 }
const at = (epss: number, cvss: number, overrides: Partial<TrendRow> = {}) => trendRow({ epss, cvss, ...overrides })
const kev = (due: string, overrides: Partial<TrendRow> = {}) =>
  at(0.01, 9.8, { is_kev: true, kev_due_date: due, ...overrides })

describe('좌표', () => {
  it('EPSS 0.1%~30% 는 로그로 왼쪽 40%, 30%~100% 는 균등으로 오른쪽 60% 에 놓는다', () => {
    expect(epssFraction(0.001)).toBe(0)
    expect(epssFraction(0.0001)).toBe(0)
    expect(epssFraction(0.01)).toBeCloseTo(0.1615, 3)
    expect(epssFraction(0.3)).toBeCloseTo(0.4)
    expect(epssFraction(0.65)).toBeCloseTo(0.7)
    expect(epssFraction(1)).toBe(1)
  })

  it('CVSS 는 4.0 미만을 바닥선에, 위는 10.4 까지 여백을 둔다', () => {
    expect(cvssFraction(2)).toBe(0)
    expect(cvssFraction(4)).toBe(0)
    expect(cvssFraction(7)).toBeCloseTo(0.46875)
    expect(cvssFraction(10)).toBeCloseTo(0.9375)
  })

  it('EPSS·CVSS 가 모두 있어야 그림에 놓는다', () => {
    expect(isPlaced(at(0.5, 9))).toBe(true)
    expect(isPlaced(trendRow({ epss: null }))).toBe(false)
    expect(isPlaced(trendRow({ cvss: null }))).toBe(false)
  })

  it('칸은 EPSS 30%·CVSS 7.0 을 경계로 사실만 나눈다', () => {
    expect(quadrantOf(at(0.3, 7))).toBe('hot')
    expect(quadrantOf(at(0.01, 9.8))).toBe('severe')
    expect(quadrantOf(at(0.5, 5))).toBe('likely')
    expect(quadrantOf(at(0.01, 5))).toBe('low')
    expect(quadrantOf(trendRow({ epss: null }))).toBeNull()
  })
})

describe('표기', () => {
  it('EPSS 는 0.1% 미만·소수 1자리·정수 %로, 없으면 대시로 적는다', () => {
    expect(formatScatterEpss(null)).toBe('—')
    expect(formatScatterEpss(0.0005)).toBe('0.1% 미만')
    expect(formatScatterEpss(0.005)).toBe('0.5%')
    expect(formatScatterEpss(0.2731)).toBe('27%')
  })

  it('CISA 기한은 남은 날 D-n, 지났으면 n일 지남, KEV 가 아니면 빈칸이다', () => {
    expect(dueText(kev('2026-11-03'), TODAY)).toBe('D-2')
    expect(dueText(kev('2026-10-29'), TODAY)).toBe('3일 지남')
    expect(dueText(trendRow(), TODAY)).toBe('')
  })
})

describe('점 놓기', () => {
  it('점수순으로 놓고, 같은 자리의 점은 세로로 3px씩 비켜 그린다', () => {
    const points = layoutPoints(
      [at(1, 10.4, { id: 'low', score: 10 }), at(1, 10.4, { id: 'high', score: 90 }), trendRow({ epss: null })],
      FRAME,
      TODAY,
    )

    expect(points.map((point) => [point.row.id, point.x, point.y, point.same])).toEqual([
      ['high', 100, 0, 2],
      ['low', 100, 3, 2],
    ])
  })

  it('기한 임박은 크게, 랜섬웨어는 고리만큼 더 크게 자리를 잡는다', () => {
    const [urgent, ransom, other] = layoutPoints(
      [
        kev('2026-11-02', { score: 90 }),
        at(0.5, 9, { is_kev: true, kev_ransomware: true, kev_due_date: '2026-12-01', score: 80 }),
        at(0.2, 5, { score: 70 }),
      ],
      FRAME,
      TODAY,
    )

    expect([urgent.radius, ransom.radius, other.radius]).toEqual([6, 8, 4.5])
  })

  it('판단 구간(30% 이상)에서 5건 이상 몰린 무리만 "n건"으로 센다', () => {
    const crowd = Array.from({ length: 5 }, (_, i) => at(0.99, 9.8, { score: 50 - i }))
    const left = Array.from({ length: 5 }, (_, i) => at(0.02, 9.8, { score: 40 - i }))
    const points = layoutPoints([...crowd, ...left], { left: 0, right: 400, top: 0, bottom: 300 }, TODAY)

    expect(points[0].near).toBe(5)
    expect(clusterMarks(points).map((mark) => mark.count)).toEqual([5])
  })
})

describe('라벨 예산', () => {
  const urgent = (count: number) =>
    Array.from({ length: count }, (_, i) => kev(`2026-11-0${(i % 3) + 2}`, { id: `U${i}`, score: 60 - i }))
  const others = Array.from({ length: 10 }, (_, i) => at(0.5, 8, { id: `O${i}`, score: 99 - i }))

  it('기한 임박을 남은 날 적은 순으로 먼저, 그다음 랜섬웨어, 그다음 점수 상위 5건에 이름을 단다', () => {
    const ransom = at(0.4, 9, { id: 'R', is_kev: true, kev_ransomware: true, kev_due_date: '2026-12-01', score: 20 })
    const points = layoutPoints([...urgent(2), ransom, ...others], FRAME, TODAY)
    const plan = labelPlan(points, TODAY, false, null)

    expect(plan.queue.map((point) => point.row.id)).toEqual(['U0', 'U1', 'R', 'O0', 'O1', 'O2', 'O3', 'O4'])
    expect([plan.priorityTotal, plan.priorityShown]).toEqual([3, 3])
  })

  it('기한 임박이 상한(넓은 화면 8·좁은 화면 4)을 넘으면 가장 급한 것만 달고 점수 상위는 생략한다', () => {
    const points = layoutPoints([...urgent(20), ...others], FRAME, TODAY)

    expect(labelPlan(points, TODAY, false, null)).toMatchObject({ priorityTotal: 20, priorityShown: 8 })
    expect(labelPlan(points, TODAY, false, null).queue).toHaveLength(8)
    expect(labelPlan(points, TODAY, true, null).queue).toHaveLength(4)
  })

  it('고른 점은 예산 밖이어도 이름을 단다', () => {
    const points = layoutPoints([...urgent(20), ...others], FRAME, TODAY)

    expect(labelPlan(points, TODAY, true, 'O9').queue.map((point) => point.row.id)).toContain('O9')
  })
})

describe('라벨 자리', () => {
  const area = { x: 0, y: 0, w: 200, h: 200 }
  const point = { x: 50, y: 50, radius: 4 }

  it('한글은 한 글자 0.9em, 그 밖은 0.52em, 고정폭(CVE 번호)은 0.6em 으로 폭을 잰다', () => {
    expect(textWidth('GitLab', 12)).toBeCloseTo(37.44)
    expect(textWidth('랜섬웨어', 12)).toBeCloseTo(43.2)
    expect(textWidth('97229', 12, true)).toBeCloseTo(36)
  })

  it('비어 있으면 점 바로 오른쪽에 둔다', () => {
    expect(placeLabel(point, 40, [], area)).toMatchObject({ x: 60, y: 54, anchor: 'start', lead: false })
  })

  it('오른쪽이 막혔으면 왼쪽, 가까운 자리가 모두 막혔으면 떨어진 자리에 지시선과 함께 둔다', () => {
    expect(placeLabel({ x: 100, y: 50, radius: 4 }, 40, [{ x: 108, y: 30, w: 80, h: 40 }], area)).toMatchObject({
      x: 90,
      anchor: 'end',
    })
    expect(placeLabel(point, 40, [{ x: 0, y: 20, w: 200, h: 60 }], area)).toMatchObject({ lead: true })
  })

  it('그림 안 어디에도 자리가 없으면 달지 않는다', () => {
    expect(placeLabel(point, 300, [], area)).toBeNull()
  })
})
