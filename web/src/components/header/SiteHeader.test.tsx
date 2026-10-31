import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { TrendFilterProvider } from '../../hooks/TrendFilterProvider.tsx'
import { useTrendFilter } from '../../hooks/use-trend-filter.ts'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { SiteHeader } from './SiteHeader.tsx'

// 목록 쪽 필터 자리. 헤더 필터와 같은 상태를 읽는지 확인한다
const ListFilterProbe = () => {
  const { filter } = useTrendFilter()
  return <output aria-label="목록 필터">{filter}</output>
}

const renderHeaderAt = (now: string) => {
  vi.setSystemTime(new Date(now))
  const server = new FakeFetch().serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <TrendFilterProvider>
          <SiteHeader />
          <ListFilterProbe />
        </TrendFilterProvider>
      </DataProvider>
    </MemoryRouter>,
  )
}

describe('사이트 머리글', () => {
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'], shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('마지막 갱신을 한국 시각과 지난 시간으로 보인다', async () => {
    renderHeaderAt('2026-10-30T12:00:00+09:00')

    expect(await screen.findByText('10-30 09:00')).toBeInTheDocument()
    expect(screen.getByText('(3시간 전)')).toBeInTheDocument()
  })

  it('갱신이 36시간 안이면 오래된 데이터 안내를 띄우지 않는다', async () => {
    renderHeaderAt('2026-10-31T20:00:00+09:00')

    await screen.findByText('10-30 09:00')
    expect(screen.queryByText(/데이터가 오래됐습니다/)).not.toBeInTheDocument()
  })

  it('갱신이 36시간을 넘으면 오래된 데이터 안내를 띄워 원문 확인을 권한다', async () => {
    renderHeaderAt('2026-10-31T21:01:00+09:00')

    expect(await screen.findByText(/데이터가 오래됐습니다/)).toBeInTheDocument()
    expect(screen.getByText(/NVD·CISA KEV 원문을 확인하세요/)).toBeInTheDocument()
  })

  it('필터를 고르면 목록 필터도 같은 값으로 바뀐다', async () => {
    renderHeaderAt('2026-10-30T12:00:00+09:00')
    const user = userEvent.setup()

    await user.click(screen.getByRole('button', { name: 'KEV' }))

    expect(screen.getByRole('button', { name: 'KEV' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByRole('button', { name: '전체' })).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByLabelText('목록 필터')).toHaveTextContent('kev')
  })

  it('검색어를 지우기 버튼으로 한 번에 비운다', async () => {
    renderHeaderAt('2026-10-30T12:00:00+09:00')
    const user = userEvent.setup()
    const search = screen.getByRole('combobox', { name: 'CVE-ID 또는 제품명 검색' })

    expect(screen.queryByRole('button', { name: '검색 지우기' })).not.toBeInTheDocument()
    await user.type(search, 'chrome')
    await user.click(screen.getByRole('button', { name: '검색 지우기' }))

    expect(search).toHaveValue('')
  })
})
