import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { SiteHeader } from './SiteHeader.tsx'

const renderHeaderAt = (now: string) => {
  vi.setSystemTime(new Date(now))
  const server = new FakeFetch().serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <SiteHeader />
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

    expect(await screen.findByText('2026-10-30 09:00 KST')).toBeInTheDocument()
    expect(screen.getByText('(3시간 전)')).toBeInTheDocument()
  })

  it('갱신이 36시간 안이면 오래된 데이터 안내를 띄우지 않는다', async () => {
    renderHeaderAt('2026-10-31T20:00:00+09:00')

    await screen.findByText('2026-10-30 09:00 KST')
    expect(screen.queryByText(/데이터가 오래됐습니다/)).not.toBeInTheDocument()
  })

  it('갱신이 36시간을 넘으면 오래된 데이터 안내를 띄워 원문 확인을 권한다', async () => {
    renderHeaderAt('2026-10-31T21:01:00+09:00')

    expect(await screen.findByText(/데이터가 오래됐습니다/)).toBeInTheDocument()
    expect(screen.getByText(/NVD·CISA KEV 원문을 확인하세요/)).toBeInTheDocument()
  })

  it('갱신이 36시간 안이면 상태를 수집 완료로 적는다', async () => {
    renderHeaderAt('2026-10-30T12:00:00+09:00')
    expect(await screen.findByText('수집 완료')).toBeInTheDocument()
  })

  it('갱신이 36시간을 넘으면 수집 완료 대신 갱신 지연으로 적는다', async () => {
    renderHeaderAt('2026-11-01T22:00:00+09:00')
    expect(await screen.findByText('갱신 지연')).toBeInTheDocument()
    expect(screen.queryByText('수집 완료')).toBeNull()
  })

  it('갱신 주기와 출처를 적고, 머리글에는 목록 필터를 두지 않는다', async () => {
    renderHeaderAt('2026-10-30T12:00:00+09:00')

    expect(await screen.findByText(/하루 1회 갱신 · 출처 NVD · CISA KEV · EPSS/)).toBeInTheDocument()
    expect(screen.queryByRole('group', { name: '취약점 필터' })).toBeNull()
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
