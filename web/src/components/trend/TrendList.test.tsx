import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { TrendFilterProvider } from '../../hooks/TrendFilterProvider.tsx'
import { rankedRows, trendRow } from '../../test/rows.ts'
import type { TrendRow } from '../../types/trend.ts'
import { TrendList } from './TrendList.tsx'

const renderList = (rows: TrendRow[], onSelect = vi.fn(), selectedId: string | null = null) => {
  render(
    <TrendFilterProvider>
      <TrendList rows={rows} selectedId={selectedId} onSelect={onSelect} />
    </TrendFilterProvider>,
  )
  return { user: userEvent.setup(), onSelect }
}

const visibleRowIds = (): string[] =>
  screen.getAllByRole('button', { name: /^CVE-/ }).map((row) => row.getAttribute('data-cve-id') ?? '')

const showByScore = async (user: ReturnType<typeof userEvent.setup>) =>
  user.click(screen.getByRole('button', { name: '점수순' }))

describe('지금 주목할 취약점 목록', () => {
  describe('상위 20건 + 더 보기 (D15)', () => {
    it('처음에는 점수 높은 20건만 보이고, 더 보기를 누르면 받은 목록 전부를 보인다', async () => {
      const rows = rankedRows(25)
      const { user } = renderList(rows)
      await showByScore(user)

      expect(visibleRowIds()).toEqual(rows.slice(0, 20).map((row) => row.id))

      await user.click(screen.getByRole('button', { name: '더 보기 (5건 더)' }))

      expect(visibleRowIds()).toHaveLength(25)
      expect(screen.queryByRole('button', { name: /더 보기/ })).not.toBeInTheDocument()
    })

    it('20건 이하면 더 보기 버튼이 없다', async () => {
      const { user } = renderList(rankedRows(20))
      await showByScore(user)

      expect(screen.queryByRole('button', { name: /더 보기/ })).not.toBeInTheDocument()
    })

    it('필터를 바꾸면 다시 20건부터 보인다', async () => {
      const { user } = renderList(rankedRows(30, { is_kev: true }))
      await showByScore(user)
      await user.click(screen.getByRole('button', { name: /더 보기/ }))

      await user.click(screen.getByRole('button', { name: /^KEV/ }))

      expect(visibleRowIds()).toHaveLength(20)
    })
  })

  describe('필터 (헤더·KPI 와 같은 상태, R1)', () => {
    it('KEV 를 고르면 실제 악용이 확인된 취약점만 보이고, 버튼마다 건수를 적는다', async () => {
      const kev = trendRow({ is_kev: true, score: 90 })
      const { user } = renderList([kev, trendRow({ score: 80 }), trendRow({ score: 70, severity: 'CRITICAL' })])
      await showByScore(user)

      expect(screen.getByRole('button', { name: '전체 3' })).toBeInTheDocument()
      expect(screen.getByRole('button', { name: 'Critical 1' })).toBeInTheDocument()
      await user.click(screen.getByRole('button', { name: 'KEV 1' }))

      expect(visibleRowIds()).toEqual([kev.id])
    })

    it('조건에 맞는 취약점이 없으면 필터를 바꿔 보라고 안내한다', async () => {
      const { user } = renderList([trendRow()])

      await user.click(screen.getByRole('button', { name: /^KEV/ }))

      expect(screen.getByText('조건에 맞는 취약점이 없습니다. 검색어나 필터를 바꿔 보세요.')).toBeInTheDocument()
    })
  })

  describe('보기 전환 (D7)', () => {
    it('처음에는 소프트웨어별로 묶어 보이고, 묶음은 최고 점수순이다', () => {
      renderList([
        trendRow({ primary_product: 'FortiOS', score: 91, is_kev: true }),
        trendRow({ primary_product: 'Chrome', score: 88 }),
        trendRow({ primary_product: 'FortiOS', score: 40 }),
      ])

      const headers = screen.getAllByRole('button', { expanded: true })

      expect(headers.map((header) => header.textContent)).toEqual([
        'FortiOS2건KEV 1최고 점수 91접기',
        'Chrome1건최고 점수 88접기',
      ])
      expect(screen.getByText('소프트웨어별 묶음 · 그룹은 최고 점수순 · 상위 3건 표시')).toBeInTheDocument()
    })

    it('묶음 머리를 누르면 그 제품의 취약점을 접고, 다시 누르면 펼친다', async () => {
      const chrome = trendRow({ primary_product: 'Chrome' })
      const { user } = renderList([chrome])
      const header = screen.getByRole('button', { name: /^Chrome/ })

      await user.click(header)
      expect(header).toHaveAttribute('aria-expanded', 'false')
      expect(screen.queryByRole('button', { name: /^CVE-/ })).not.toBeInTheDocument()

      await user.click(header)
      expect(visibleRowIds()).toEqual([chrome.id])
    })

    it('점수순으로 바꾸면 묶음 없이 점수 높은 순서로 보인다', async () => {
      const rows = [trendRow({ primary_product: 'A', score: 90 }), trendRow({ primary_product: 'B', score: 80 })]
      const { user } = renderList(rows)

      await showByScore(user)

      expect(screen.getByRole('button', { name: '점수순' })).toHaveAttribute('aria-pressed', 'true')
      expect(screen.queryByRole('button', { expanded: true })).not.toBeInTheDocument()
      expect(screen.getByText('트렌드 점수순 · 상위 2건 표시')).toBeInTheDocument()
    })
  })

  describe('목록 행', () => {
    it('KEV 취약점은 KEV·랜섬웨어 배지와 KEV 패치 기한을 보인다', () => {
      const row = trendRow({ is_kev: true, kev_ransomware: true, kev_due_date: '2026-11-13' })
      renderList([row])
      const card = screen.getByRole('button', { name: new RegExp(row.id) })

      expect(within(card).getByText('KEV')).toBeInTheDocument()
      expect(within(card).getByText('랜섬웨어')).toBeInTheDocument()
      expect(within(card).getByText('KEV 패치 기한 11-13')).toBeInTheDocument()
    })

    it('KEV 가 아니면 KEV 배지와 기한을 보이지 않는다', () => {
      const row = trendRow()
      renderList([row])
      const card = screen.getByRole('button', { name: new RegExp(row.id) })

      expect(within(card).queryByText('KEV')).not.toBeInTheDocument()
      expect(within(card).queryByText(/패치 기한/)).not.toBeInTheDocument()
    })

    it('심각도와 제품을 글자 배지로 보인다 (색만으로 구분하지 않음)', () => {
      const row = trendRow({ severity: 'CRITICAL', primary_product: 'FortiOS' })
      renderList([row])
      const card = screen.getByRole('button', { name: new RegExp(row.id) })

      expect(within(card).getByText('Critical')).toBeInTheDocument()
      expect(within(card).getByText('FortiOS')).toBeInTheDocument()
    })

    it('한국어 요약이 있으면 요약을 보인다', () => {
      const row = trendRow({ summary_what: 'V8 엔진 타입 혼동으로 원격 코드가 실행됩니다.' })
      renderList([row])

      expect(screen.getByText('V8 엔진 타입 혼동으로 원격 코드가 실행됩니다.')).toBeInTheDocument()
      expect(screen.queryByText('원문')).not.toBeInTheDocument()
    })

    it('요약이 없으면 "원문" 표시와 함께 영어 제목을 보인다', () => {
      renderList([trendRow({ summary_what: null, title: 'Heap overflow in Skia' })])

      expect(screen.getByText('원문')).toBeInTheDocument()
      expect(screen.getByText('Heap overflow in Skia')).toBeInTheDocument()
    })

    it('점수·EPSS(상위 N%)·CVSS 를 적는다', () => {
      const row = trendRow({ score: 98, epss: 0.91, epss_percentile: 0.997, cvss: 9.3, cvss_version: '4.0' })
      renderList([row])
      const card = screen.getByRole('button', { name: new RegExp(row.id) })

      expect(card).toHaveTextContent('점수 98')
      expect(card).toHaveTextContent('EPSS 0.91 (상위 1%)')
      expect(card).toHaveTextContent('CVSS 9.3 (v4)')
    })

    it('아직 EPSS·CVSS 가 없는 신규 건은 대시와 미평가로 적는다', () => {
      const row = trendRow({ epss: null, epss_percentile: null, cvss: null, cvss_version: null, severity: 'UNRATED' })
      renderList([row])
      const card = screen.getByRole('button', { name: new RegExp(row.id) })

      expect(card).toHaveTextContent('EPSS —')
      expect(card).toHaveTextContent('CVSS 미평가')
    })

    it('행을 누르면 그 취약점을 골랐다고 알리고, 고른 행은 눌린 상태로 보인다', async () => {
      const [first, second] = [trendRow({ score: 90 }), trendRow({ score: 80 })]
      const { user, onSelect } = renderList([first, second], vi.fn(), first.id)

      await user.click(screen.getByRole('button', { name: new RegExp(second.id) }))

      expect(onSelect).toHaveBeenCalledWith(second.id)
      expect(screen.getByRole('button', { name: new RegExp(first.id) })).toHaveAttribute('aria-pressed', 'true')
    })
  })
})
