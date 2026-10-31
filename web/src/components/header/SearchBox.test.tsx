import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, useLocation } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import type { SearchEntry } from '../../types/search.ts'
import { SearchBox } from './SearchBox.tsx'

const SEARCH_URL = '/vuln-trend/data/search-index.json?v=v1'
const LABEL = 'CVE-ID 또는 제품명 검색'

const index: SearchEntry[] = [
  { id: 'CVE-2026-42301', title: 'Skia 힙 오버플로', product: 'Chrome' },
  { id: 'CVE-2026-41203', title: 'V8 타입 혼동', product: 'Chrome' },
  { id: 'CVE-2026-40117', title: 'SSL-VPN 메모리 쓰기 오류', product: 'FortiOS' },
]

const LocationProbe = () => <output aria-label="현재 주소">{useLocation().pathname}</output>

const renderSearch = (server = new FakeFetch().serve(SEARCH_URL, index)) => {
  server.serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <SearchBox />
        <LocationProbe />
      </DataProvider>
    </MemoryRouter>,
  )
  return { server, user: userEvent.setup(), input: screen.getByRole('combobox', { name: LABEL }) }
}

const optionNames = () => screen.getAllByRole('option').map((option) => option.getAttribute('data-cve-id'))

describe('검색창', () => {
  describe('검색 색인 (1년치, 수 MB, P19)', () => {
    it('검색창을 쓰기 전에는 받지 않는다', () => {
      const { server } = renderSearch()

      expect(server.requestsTo(SEARCH_URL)).toHaveLength(0)
    })

    it('검색창에 들어가면 그때 받는다', async () => {
      const { server, user, input } = renderSearch()

      await user.click(input)

      await waitFor(() => expect(server.requestsTo(SEARCH_URL).length).toBeGreaterThan(0))
    })
  })

  describe('찾기', () => {
    it('CVE 번호 일부로 찾는다', async () => {
      const { user, input } = renderSearch()

      await user.type(input, '41203')

      expect(await screen.findByRole('option', { name: /CVE-2026-41203/ })).toBeInTheDocument()
      expect(optionNames()).toEqual(['CVE-2026-41203'])
    })

    it('제품명으로 찾는다', async () => {
      const { user, input } = renderSearch()

      await user.type(input, 'chrome')

      await screen.findByRole('option', { name: /CVE-2026-42301/ })
      expect(optionNames()).toEqual(['CVE-2026-42301', 'CVE-2026-41203'])
    })

    it('맞는 결과가 없으면 그렇다고 알린다', async () => {
      const { user, input } = renderSearch()

      await user.type(input, 'wordpress')

      expect(await screen.findByText('"wordpress"에 맞는 취약점이 없습니다.')).toBeInTheDocument()
    })

    it('색인에 없는 CVE 번호를 정확히 적으면 그 번호의 상세로 가는 길을 준다', async () => {
      const { user, input } = renderSearch()

      await user.type(input, 'cve-2024-3094')
      await user.click(await screen.findByRole('option', { name: /CVE-2024-3094 바로 가기/ }))

      expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2024-3094')
    })
  })

  describe('결과 고르기', () => {
    it('결과를 누르면 그 취약점의 상세 페이지로 가고 검색창을 비운다', async () => {
      const { user, input } = renderSearch()
      await user.type(input, 'fortios')

      await user.click(await screen.findByRole('option', { name: /CVE-2026-40117/ }))

      expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-40117')
      expect(input).toHaveValue('')
      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    })

    it('키보드 ↓ 로 결과를 옮겨 다니고 Enter 로 고른다', async () => {
      const { user, input } = renderSearch()
      await user.type(input, 'chrome')
      await screen.findByRole('option', { name: /CVE-2026-42301/ })

      await user.keyboard('{ArrowDown}{ArrowDown}')
      expect(screen.getByRole('option', { name: /CVE-2026-41203/ })).toHaveAttribute('aria-selected', 'true')
      await user.keyboard('{Enter}')

      expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/cve/CVE-2026-41203')
    })

    it('↑ 는 위 결과로 옮기고, 맨 위에서는 더 올라가지 않는다', async () => {
      const { user, input } = renderSearch()
      await user.type(input, 'chrome')
      await screen.findByRole('option', { name: /CVE-2026-42301/ })

      await user.keyboard('{ArrowDown}{ArrowDown}{ArrowUp}{ArrowUp}')

      expect(screen.getByRole('option', { name: /CVE-2026-42301/ })).toHaveAttribute('aria-selected', 'true')
    })

    it('Esc 를 누르면 결과 목록을 닫는다', async () => {
      const { user, input } = renderSearch()
      await user.type(input, 'chrome')
      await screen.findByRole('listbox')

      await user.keyboard('{Escape}')

      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
      expect(input).toHaveAttribute('aria-expanded', 'false')
    })

    it('검색창 밖으로 나가면 결과 목록을 닫는다', async () => {
      const { user, input } = renderSearch()
      await user.type(input, 'chrome')
      await screen.findByRole('listbox')

      fireEvent.blur(input)

      expect(screen.queryByRole('listbox')).not.toBeInTheDocument()
    })
  })

  it('결과가 많으면 앞쪽만 보이고 나머지 건수를 알려 검색어를 좁히게 한다', async () => {
    const many: SearchEntry[] = Array.from({ length: 13 }, (_, number) => ({
      id: `CVE-2026-5${String(number).padStart(4, '0')}`,
      title: 'Chrome 취약점',
      product: 'Chrome',
    }))
    const { user, input } = renderSearch(new FakeFetch().serve(SEARCH_URL, many))

    await user.type(input, 'chrome')

    await screen.findByRole('listbox')
    expect(screen.getAllByRole('option')).toHaveLength(10)
    expect(screen.getByText('외 3건 — 검색어를 더 구체적으로 적어 보세요')).toBeInTheDocument()
  })

  it('검색 색인을 못 받으면 알리고, 다시 시도로 다시 받아 찾는다', async () => {
    const { user, input } = renderSearch(new FakeFetch().serve(SEARCH_URL, 'down', 503).serve(SEARCH_URL, index))
    await user.type(input, 'fortios')

    await user.click(await screen.findByRole('button', { name: '다시 시도' }))

    expect(await screen.findByRole('option', { name: /CVE-2026-40117/ })).toBeInTheDocument()
  })

  it('지우기 버튼으로 검색어를 한 번에 비운다', async () => {
    const { user, input } = renderSearch()
    await user.type(input, 'chrome')

    await user.click(screen.getByRole('button', { name: '검색 지우기' }))

    expect(input).toHaveValue('')
  })
})
