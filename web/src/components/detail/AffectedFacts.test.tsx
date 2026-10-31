import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { cveDetail } from '../../test/fixtures.ts'
import type { AffectedProduct } from '../../types/cve.ts'
import { AffectedFacts } from './AffectedFacts.tsx'

const product = (n: number): AffectedProduct => ({
  vendor: 'vendor',
  product: `product_${n}`,
  display_name: null,
  slug: null,
  ecosystem: null,
  vulnerable_range: `<${n}.0`,
  fixed_version: null,
})

const renderFacts = (affected: AffectedProduct[]) =>
  render(<AffectedFacts detail={{ ...cveDetail, is_kev: true, kev_due_date: '2026-11-10', affected }} />)

const affectedList = () => screen.getByRole('list', { name: '영향 제품' })

describe('영향 버전 · 수정 버전 · KEV 패치 기한', () => {
  it('수정 버전과 KEV 패치 기한을 먼저 보이고, 영향 제품은 그 아래 한 줄 전체에 둔다', () => {
    renderFacts([product(1)])

    expect(screen.getAllByRole('term').map((term) => term.textContent)).toEqual([
      '수정 버전',
      'KEV 패치 기한',
      '영향 버전',
    ])
  })

  it('영향 제품이 5개 이하면 모두 보이고 더 보기 버튼이 없다', () => {
    renderFacts([1, 2, 3, 4, 5].map(product))

    expect(within(affectedList()).getAllByRole('listitem')).toHaveLength(5)
    expect(screen.queryByRole('button')).toBeNull()
  })

  it('영향 제품이 많으면 처음 5개만 보이고 나머지 개수를 더 보기로 알린다', () => {
    renderFacts(Array.from({ length: 25 }, (_, i) => product(i + 1)))

    expect(within(affectedList()).getAllByRole('listitem')).toHaveLength(5)
    expect(screen.getByRole('button', { name: '20개 더 보기' })).toHaveAttribute('aria-expanded', 'false')
  })

  it('더 보기를 누르면 모두 펼치고, 접기를 누르면 다시 5개로 돌아간다', () => {
    renderFacts(Array.from({ length: 25 }, (_, i) => product(i + 1)))

    fireEvent.click(screen.getByRole('button', { name: '20개 더 보기' }))
    expect(within(affectedList()).getAllByRole('listitem')).toHaveLength(25)
    expect(screen.getByRole('button', { name: '접기' })).toHaveAttribute('aria-expanded', 'true')

    fireEvent.click(screen.getByRole('button', { name: '접기' }))
    expect(within(affectedList()).getAllByRole('listitem')).toHaveLength(5)
  })

  it('범위가 여럿이면 "||" 사이에서 줄이 바뀌도록 조각마다 따로 둔다', () => {
    renderFacts([{ ...product(1), display_name: 'Node.js', vulnerable_range: '<22.23.4 || >=23.0.0, <24.22.1' }])

    const item = within(affectedList()).getByRole('listitem')
    expect(item).toHaveTextContent('Node.js <22.23.4 || >=23.0.0, <24.22.1')
    expect(within(item).getByText('>=23.0.0, <24.22.1')).toHaveClass('inline-block')
  })

  it('영향 제품을 모르면 "정보 없음"으로 적는다', () => {
    renderFacts([])

    expect(screen.getByText('정보 없음')).toBeInTheDocument()
  })
})
