import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import type { TrendRow } from '../types/trend.ts'
import {
  defaultSelectedId,
  formatEpssPercent,
  formatEpssRank,
  isNewKev,
  listTiers,
  matchesSignal,
  productCounts,
  scoreRanks,
  signalOptions,
} from './list-model.ts'

const TODAY = '2026-11-01'
const kev = (overrides: Partial<TrendRow> = {}) =>
  trendRow({ is_kev: true, kev_due_date: '2026-12-01', kev_added_at: '2026-09-01', ...overrides })

describe('신호', () => {
  it('새 KEV 는 등재 7일 안(오늘 포함)이다. 등재일을 모르면 새 KEV 로 치지 않는다', () => {
    expect(isNewKev(kev({ kev_added_at: '2026-11-01' }), TODAY)).toBe(true)
    expect(isNewKev(kev({ kev_added_at: '2026-10-25' }), TODAY)).toBe(true)
    expect(isNewKev(kev({ kev_added_at: '2026-10-24' }), TODAY)).toBe(false)
    expect(isNewKev(kev({ kev_added_at: null }), TODAY)).toBe(false)
    expect(isNewKev(trendRow({ kev_added_at: '2026-11-01' }), TODAY)).toBe(false)
  })

  it('신호마다 맞는 취약점을 고른다', () => {
    expect(matchesSignal(kev({ kev_due_date: '2026-11-02' }), 'urgent', TODAY)).toBe(true)
    expect(matchesSignal(kev(), 'urgent', TODAY)).toBe(false)
    expect(matchesSignal(kev({ kev_ransomware: true }), 'ransomware', TODAY)).toBe(true)
    expect(matchesSignal(trendRow({ poc_count: 2 }), 'poc', TODAY)).toBe(true)
    expect(matchesSignal(trendRow(), 'poc', TODAY)).toBe(false)
    expect(matchesSignal(trendRow(), 'all', TODAY)).toBe(true)
  })

  it('신호 버튼은 건수를 함께 주고, 0건인 신호는 숨긴다', () => {
    const rows = [kev({ kev_due_date: '2026-11-02' }), kev({ kev_ransomware: true }), trendRow()]

    expect(signalOptions(rows, 'all', TODAY)).toEqual([
      { key: 'all', label: '전체', count: 3 },
      { key: 'urgent', label: 'CISA 기한 임박', count: 1 },
      { key: 'ransomware', label: '랜섬웨어', count: 1 },
    ])
  })

  it('지금 고른 신호는 0건이어도 남겨 둔다', () => {
    expect(signalOptions([trendRow()], 'poc', TODAY).map((option) => option.key)).toEqual(['all', 'poc'])
  })
})

describe('제품', () => {
  it('제품별 건수를 많은 순(같으면 이름순)으로 세고, 제품 정보가 없으면 "제품 미상"으로 묶는다', () => {
    const rows = [
      trendRow({ primary_product: 'PHP' }),
      trendRow({ primary_product: 'GitLab' }),
      trendRow({ primary_product: 'PHP' }),
      trendRow({ primary_product: null }),
      trendRow({ primary_product: 'Apache' }),
    ]

    expect(productCounts(rows, 'all', TODAY)).toEqual([
      { name: 'PHP', count: 2 },
      { name: 'Apache', count: 1 },
      { name: 'GitLab', count: 1 },
      { name: '제품 미상', count: 1 },
    ])
  })

  it('건수가 같으면 기한 임박이 많은 순, 그다음 최고 점수 순, 그다음 이름 순이다 — 제품 막대와 같은 순서', () => {
    const rows = [
      trendRow({ primary_product: 'JFrog', score: 40 }),
      trendRow({ primary_product: 'Windows', score: 80 }),
      kev({ primary_product: 'Plex', score: 30, kev_due_date: '2026-11-02' }),
      trendRow({ primary_product: 'Apache', score: 40 }),
    ]

    expect(productCounts(rows, 'all', TODAY).map((product) => product.name)).toEqual([
      'Plex',
      'Windows',
      'Apache',
      'JFrog',
    ])
  })

  it('신호를 고르면 제품 건수도 그 신호 안에서 센다', () => {
    const rows = [kev({ primary_product: 'PHP', kev_ransomware: true }), trendRow({ primary_product: 'PHP' })]

    expect(productCounts(rows, 'ransomware', TODAY)).toEqual([{ name: 'PHP', count: 1 }])
  })
})

describe('묶음', () => {
  const rows = [
    kev({ id: 'top', score: 90 }),
    kev({ id: 'd2', score: 50, kev_due_date: '2026-11-03' }),
    kev({ id: 'd0', score: 40, kev_due_date: '2026-11-01', kev_ransomware: true }),
    trendRow({ id: 'mid', score: 60, primary_product: 'GitLab' }),
  ]

  it('기본 보기는 기한 임박을 기한 빠른 순으로 맨 위에 고정하고, 나머지는 점수 높은 순이다', () => {
    const tiers = listTiers(rows, { signal: 'all', product: null, quadrant: null }, TODAY)

    expect(tiers.urgent.map((row) => row.id)).toEqual(['d0', 'd2'])
    expect(tiers.rest.map((row) => row.id)).toEqual(['top', 'mid'])
    expect(tiers.total).toBe(4)
  })

  it('신호를 고르면 고정 묶음 없이 점수순 한 묶음이다', () => {
    const tiers = listTiers(rows, { signal: 'urgent', product: null, quadrant: null }, TODAY)

    expect(tiers.urgent).toEqual([])
    expect(tiers.rest.map((row) => row.id)).toEqual(['d2', 'd0'])
  })

  it('제품을 고르면 그 제품만 남긴다', () => {
    const tiers = listTiers(rows, { signal: 'all', product: 'GitLab', quadrant: null }, TODAY)

    expect(tiers.rest.map((row) => row.id)).toEqual(['mid'])
    expect(tiers.total).toBe(1)
  })

  it('4칸 건수표의 칸을 고르면 그 칸의 취약점만 점수순으로 남긴다', () => {
    const scored = [
      trendRow({ id: 'hot-low', score: 30, epss: 0.9, cvss: 9 }),
      trendRow({ id: 'hot-high', score: 70, epss: 0.5, cvss: 7 }),
      trendRow({ id: 'severe', score: 90, epss: 0.01, cvss: 9 }),
      trendRow({ id: 'unscored', score: 95, epss: null, cvss: 9 }),
    ]
    const tiers = listTiers(scored, { signal: 'all', product: null, quadrant: 'hot' }, TODAY)

    expect([...tiers.urgent, ...tiers.rest].map((row) => row.id)).toEqual(['hot-high', 'hot-low'])
    expect(tiers.total).toBe(2)
  })

  it('순위는 필터와 상관없이 주목 목록 전체의 점수 순위다', () => {
    expect(scoreRanks(rows).get('d0')).toBe(4)
    expect(scoreRanks(rows).get('top')).toBe(1)
  })

  it('처음 고른 상태는 목록 맨 위다. 기한 임박이 있으면 그 첫 건, 없으면 점수 1위', () => {
    expect(defaultSelectedId(rows, TODAY)).toBe('d0')
    expect(defaultSelectedId([trendRow({ id: 'a', score: 10 }), trendRow({ id: 'b', score: 20 })], TODAY)).toBe('b')
    expect(defaultSelectedId([], TODAY)).toBeNull()
  })
})

describe('EPSS 표기', () => {
  it('악용 확률은 % 로, 크기에 따라 자릿수를 줄이고 아주 작으면 "<0.01%"로 적는다', () => {
    expect(formatEpssPercent(0.99999)).toBe('100%')
    expect(formatEpssPercent(0.2731)).toBe('27%')
    expect(formatEpssPercent(0.0531)).toBe('5.3%')
    expect(formatEpssPercent(0.00531)).toBe('0.53%')
    expect(formatEpssPercent(0.00004)).toBe('<0.01%')
  })

  it('순위는 상위 몇 % 인지로 적고, 1% 안이면 "상위 1% 안"이다', () => {
    expect(formatEpssRank(0.996)).toBe('상위 1% 안')
    expect(formatEpssRank(0.9721)).toBe('상위 3%')
    expect(formatEpssRank(0.45)).toBe('상위 55%')
  })
})
