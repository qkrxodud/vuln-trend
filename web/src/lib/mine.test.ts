import { describe, expect, it } from 'vitest'
import { trendRow } from '../test/rows.ts'
import {
  EMPTY_MINE,
  isMine,
  mineRows,
  newSince,
  olderEntries,
  parseMine,
  productCandidates,
  toggleProduct,
} from './mine.ts'

describe('저장된 내 제품 읽기', () => {
  it('저장된 제품과 마지막 방문 시각을 읽는다', () => {
    expect(parseMine(JSON.stringify({ products: ['GitLab', 'PHP'], seenAt: '2026-11-01T09:00:00+09:00' }))).toEqual({
      products: ['GitLab', 'PHP'],
      seenAt: '2026-11-01T09:00:00+09:00',
    })
  })

  it('없거나 깨졌거나 모양이 다르면 빈 상태로 시작한다', () => {
    expect(parseMine(null)).toEqual(EMPTY_MINE)
    expect(parseMine('{not json')).toEqual(EMPTY_MINE)
    expect(parseMine(JSON.stringify({ products: 'GitLab' }))).toEqual(EMPTY_MINE)
    expect(parseMine(JSON.stringify({ products: ['GitLab', 3, ''] }))).toEqual({ products: ['GitLab'], seenAt: null })
  })
})

describe('고르기', () => {
  it('누르면 더하고 다시 누르면 뺀다. 이름순으로 둔다', () => {
    const one = toggleProduct(EMPTY_MINE, 'PHP', null)
    const two = toggleProduct(one, 'GitLab', null)

    expect(two.products).toEqual(['GitLab', 'PHP'])
    expect(isMine(two, 'GitLab')).toBe(true)
    expect(toggleProduct(two, 'GitLab', null).products).toEqual(['PHP'])
  })

  it('처음 제품을 고르면 그때를 본 때로 잡아, 처음에는 새 건이 0건이다', () => {
    const picked = toggleProduct(EMPTY_MINE, 'GitLab', '2026-11-01T18:49:00+09:00')

    expect(picked.seenAt).toBe('2026-11-01T18:49:00+09:00')
  })

  it('제품을 더 고르거나 빼도 본 때는 그대로다', () => {
    const seen = { products: ['PHP'], seenAt: '2026-10-31T09:00:00+09:00' }

    expect(toggleProduct(seen, 'GitLab', '2026-11-01T18:49:00+09:00').seenAt).toBe('2026-10-31T09:00:00+09:00')
    expect(toggleProduct(seen, 'PHP', '2026-11-01T18:49:00+09:00').seenAt).toBe('2026-10-31T09:00:00+09:00')
  })

  it('갱신 시각을 아직 모르면 본 때를 비워 두고, /mine 을 열 때 잡는다', () => {
    expect(toggleProduct(EMPTY_MINE, 'GitLab', null).seenAt).toBeNull()
  })

  it('제품을 모르는(null) 건은 고를 수 없다', () => {
    expect(isMine({ products: ['제품 미상'], seenAt: null }, null)).toBe(false)
  })
})

describe('내 제품 취약점 모으기', () => {
  const index = [trendRow({ id: 'A', primary_product: 'GitLab' }), trendRow({ id: 'B', primary_product: 'PHP' })]
  const recent = [trendRow({ id: 'A', primary_product: 'GitLab' }), trendRow({ id: 'C', primary_product: 'GitLab' })]

  it('주목 목록과 최근 7일에서 내 제품 행을 모으고, 같은 번호는 한 번만 둔다', () => {
    expect(mineRows([index, recent], ['GitLab']).map((row) => row.id)).toEqual(['A', 'C'])
    expect(mineRows([index, recent], [])).toEqual([])
  })

  it('마지막 방문 뒤 공개된 것만 새것으로 세고, 처음이면 모두 새것이다', () => {
    const rows = [
      trendRow({ published_at: '2026-11-01T10:00:00+09:00' }),
      trendRow({ published_at: '2026-10-30T10:00:00+09:00' }),
      trendRow({ published_at: null }),
    ]

    expect(newSince(rows, '2026-10-31T09:00:00+09:00')).toBe(1)
    expect(newSince(rows, null)).toBe(3)
  })

  it('지난 12개월 검색 색인에서 점수 정보가 없는 내 제품 CVE 를 따로 모은다', () => {
    const search = [
      { id: 'A', title: 'a', product: 'GitLab' },
      { id: 'D', title: 'd', product: 'GitLab' },
      { id: 'E', title: 'e', product: 'PHP' },
      { id: 'F', title: 'f', product: null },
    ]

    expect(olderEntries(search, ['GitLab'], new Set(['A']))).toEqual([{ id: 'D', title: 'd', product: 'GitLab' }])
  })

  it('고를 후보는 검색 색인의 제품 이름을 건수 많은 순으로 준다', () => {
    const search = [
      { id: '1', title: '', product: 'PHP' },
      { id: '2', title: '', product: 'GitLab' },
      { id: '3', title: '', product: 'GitLab' },
      { id: '4', title: '', product: null },
    ]

    expect(productCandidates(search)).toEqual([
      { name: 'GitLab', count: 2 },
      { name: 'PHP', count: 1 },
    ])
  })
})
