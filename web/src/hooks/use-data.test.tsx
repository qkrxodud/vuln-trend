import { act, renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../api/data-client.ts'
import { FakeFetch } from '../test/fake/fake-fetch.ts'
import { cveDetail, metaWithVersion, stats } from '../test/fixtures.ts'
import { DataProvider } from './DataProvider.tsx'
import { useCveDetail, useMeta, useSearchIndex, useStats, useTrendIndex } from './use-data.ts'

const SITE_BASE = '/vuln-trend/'
const META_URL = '/vuln-trend/data/meta.json'
const STATS_URL = '/vuln-trend/data/stats.json?v=v1'
const SEARCH_URL = '/vuln-trend/data/search-index.json?v=v1'

const providerFor = (server: FakeFetch) => {
  const client = createDataClient(server.fetch, SITE_BASE)
  return ({ children }: { children: ReactNode }) => <DataProvider client={client}>{children}</DataProvider>
}

describe('화면이 쓰는 데이터 상태', () => {
  it('갱신 정보를 받기 전에는 불러오는 중으로 보인다', () => {
    const server = new FakeFetch().serve(META_URL, metaWithVersion('v1'))

    const { result } = renderHook(() => useStats(), { wrapper: providerFor(server) })

    expect(result.current.status).toBe('loading')
  })

  it('갱신 정보를 받으면 그 버전으로 통계를 읽어 보여 준다', async () => {
    const server = new FakeFetch().serve(META_URL, metaWithVersion('v1')).serve(STATS_URL, stats)

    const { result } = renderHook(() => useStats(), { wrapper: providerFor(server) })

    await waitFor(() => expect(result.current).toEqual({ status: 'ready', data: stats }))
  })

  it('갱신 정보를 못 받으면 다른 데이터도 실패로 보이고, 다시 시도하면 처음부터 읽는다', async () => {
    const server = new FakeFetch()
      .serve(META_URL, 'down', 503)
      .serve(META_URL, metaWithVersion('v1'))
      .serve(STATS_URL, stats)
    const { result } = renderHook(() => ({ meta: useMeta(), stats: useStats() }), { wrapper: providerFor(server) })
    await waitFor(() => expect(result.current.stats.status).toBe('error'))

    act(() => {
      if (result.current.stats.status === 'error') result.current.stats.retry()
    })

    await waitFor(() => expect(result.current.stats).toEqual({ status: 'ready', data: stats }))
    expect(result.current.meta.status).toBe('ready')
  })

  it('통계를 못 받으면 실패로 보이고, 다시 시도하면 새로 받아 보여 준다', async () => {
    const server = new FakeFetch()
      .serve(META_URL, metaWithVersion('v1'))
      .serve(STATS_URL, 'down', 503)
      .serve(STATS_URL, stats)
    const { result } = renderHook(() => useStats(), { wrapper: providerFor(server) })
    await waitFor(() => expect(result.current.status).toBe('error'))

    act(() => {
      if (result.current.status === 'error') result.current.retry()
    })

    await waitFor(() => expect(result.current).toEqual({ status: 'ready', data: stats }))
  })

  it('트렌드 목록도 갱신 버전으로 읽는다', async () => {
    const server = new FakeFetch()
      .serve(META_URL, metaWithVersion('v1'))
      .serve('/vuln-trend/data/index.json?v=v1', [cveDetail])

    const { result } = renderHook(() => useTrendIndex(), { wrapper: providerFor(server) })

    await waitFor(() => expect(result.current).toEqual({ status: 'ready', data: [cveDetail] }))
  })

  it('데이터 공급자 밖에서 쓰면 개발 중에 바로 알 수 있게 실패한다', () => {
    expect(() => renderHook(() => useStats())).toThrow('Data hooks must be used inside <DataProvider>')
  })

  describe('검색 색인 (1년치, 수 MB)', () => {
    it('검색창을 쓰기 전에는 받지 않는다', async () => {
      const server = new FakeFetch().serve(META_URL, metaWithVersion('v1')).serve(SEARCH_URL, [])

      const { result } = renderHook(() => ({ meta: useMeta(), search: useSearchIndex(false) }), {
        wrapper: providerFor(server),
      })
      await waitFor(() => expect(result.current.meta.status).toBe('ready'))

      expect(result.current.search.status).toBe('idle')
      expect(server.requestsTo(SEARCH_URL)).toHaveLength(0)
    })

    it('검색창을 쓰기 시작하면 받는다', async () => {
      const server = new FakeFetch().serve(META_URL, metaWithVersion('v1')).serve(SEARCH_URL, [])

      const { result } = renderHook(() => useSearchIndex(true), { wrapper: providerFor(server) })

      await waitFor(() => expect(result.current).toEqual({ status: 'ready', data: [] }))
    })
  })

  describe('CVE 상세', () => {
    it('고른 CVE 가 없으면 아무것도 받지 않는다', () => {
      const server = new FakeFetch().serve(META_URL, metaWithVersion('v1'))

      const { result } = renderHook(() => useCveDetail(null), { wrapper: providerFor(server) })

      expect(result.current.status).toBe('idle')
    })

    it('고른 CVE 의 상세를 받아 보여 준다', async () => {
      const server = new FakeFetch()
        .serve(META_URL, metaWithVersion('v1'))
        .serve('/vuln-trend/data/cve/CVE-2026-41203.json?v=v1', cveDetail)

      const { result } = renderHook(() => useCveDetail('CVE-2026-41203'), { wrapper: providerFor(server) })

      await waitFor(() => expect(result.current).toEqual({ status: 'ready', data: cveDetail }))
    })

    it('다른 CVE 를 고르면 새 상세를 받기 전까지 먼저 고른 CVE 의 상세를 보이지 않는다', async () => {
      const server = new FakeFetch()
        .serve(META_URL, metaWithVersion('v1'))
        .serve('/vuln-trend/data/cve/CVE-2026-41203.json?v=v1', cveDetail)
      const { result, rerender } = renderHook(({ cveId }) => useCveDetail(cveId), {
        wrapper: providerFor(server),
        initialProps: { cveId: 'CVE-2026-41203' },
      })
      await waitFor(() => expect(result.current.status).toBe('ready'))

      rerender({ cveId: 'CVE-2026-99999' })

      expect(result.current.status).toBe('loading')
      await waitFor(() => expect(result.current).toEqual({ status: 'ready', data: null }))
    })

    it('상세 파일이 없는 CVE 는 "상세 없음"으로 준비된다', async () => {
      const server = new FakeFetch().serve(META_URL, metaWithVersion('v1'))

      const { result } = renderHook(() => useCveDetail('CVE-2026-99999'), { wrapper: providerFor(server) })

      await waitFor(() => expect(result.current).toEqual({ status: 'ready', data: null }))
    })
  })
})
