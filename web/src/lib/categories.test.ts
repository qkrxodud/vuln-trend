import { describe, expect, it } from 'vitest'
import { categoriesFixture } from '../test/categories.ts'
import { trendRow } from '../test/rows.ts'
import {
  axisCategories,
  categoryPath,
  chipOrder,
  countByCategory,
  findCategory,
  isQuiet,
  matchesCategory,
  UNCLASSIFIED,
} from './categories.ts'

const categories = categoriesFixture({
  rce: { count_7d: 12 },
  xss: { count_7d: 30 },
  info: { count_7d: 12 },
  network: { count_7d: 2 },
})

describe('분류 찾기', () => {
  it('공격 유형은 /type, 제품군은 /family 로 나누어 찾는다', () => {
    expect(axisCategories(categories, 'type').map((category) => category.key)).toHaveLength(10)
    expect(axisCategories(categories, 'family')[0].label).toBe('운영체제')
    expect(findCategory(categories, 'type', 'rce')?.label).toBe('원격 코드 실행')
    expect(findCategory(categories, 'family', 'rce')).toBeNull()
    expect(categoryPath('family', 'network')).toBe('/family/network')
  })
})

describe('분류 칩 순서', () => {
  it('최근 7일 건수가 많은 순으로 두고, 같으면 배치가 준 순서를 지킨다', () => {
    const keys = chipOrder(axisCategories(categories, 'type')).map((category) => category.key)

    expect(keys.slice(0, 3)).toEqual(['xss', 'rce', 'info'])
    expect(keys.at(-1)).toBe('other')
  })

  it('최근 7일 0건인 분류는 숨기지 않고 흐리게 둔다', () => {
    expect(isQuiet(findCategory(categories, 'type', 'dos')!)).toBe(true)
    expect(isQuiet(findCategory(categories, 'family', 'network')!)).toBe(false)
  })
})

describe('행의 분류', () => {
  const rce = trendRow({ id: 'a', attack_types: ['rce', 'memory'], product_family: 'network' })
  const unknown = trendRow({ id: 'b', attack_types: [], product_family: null })
  const beforeContract = trendRow({ id: 'c' })

  it('공격 유형은 여러 개 중 하나라도 맞으면, 제품군은 하나가 맞으면 그 분류다', () => {
    expect(matchesCategory(rce, 'type', 'memory')).toBe(true)
    expect(matchesCategory(rce, 'family', 'network')).toBe(true)
    expect(matchesCategory(rce, 'family', 'os')).toBe(false)
    expect(matchesCategory(rce, 'type', null)).toBe(true)
  })

  it('분류가 비었거나 아직 내보내기 전인 행은 "분류 전"으로 거른다', () => {
    expect(matchesCategory(unknown, 'type', UNCLASSIFIED)).toBe(true)
    expect(matchesCategory(unknown, 'family', UNCLASSIFIED)).toBe(true)
    expect(matchesCategory(beforeContract, 'type', UNCLASSIFIED)).toBe(true)
    expect(matchesCategory(rce, 'type', UNCLASSIFIED)).toBe(false)
  })

  it('분류마다 행 수를 세고, 분류 전도 함께 센다', () => {
    const counts = countByCategory([rce, unknown, beforeContract], 'type')

    expect(counts.get('rce')).toBe(1)
    expect(counts.get('memory')).toBe(1)
    expect(counts.get(UNCLASSIFIED)).toBe(2)
  })
})
