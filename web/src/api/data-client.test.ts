import { beforeEach, describe, expect, it } from 'vitest'
import { FakeFetch } from '../test/fake/fake-fetch.ts'
import { cveDetail, metaWithVersion, stats } from '../test/fixtures.ts'
import { createDataClient, type DataClient } from './data-client.ts'
import { DataLoadError } from './data-load-error.ts'

const SITE_BASE = '/vuln-trend/'
const META_URL = '/vuln-trend/data/meta.json'
const STATS_URL = '/vuln-trend/data/stats.json?v=v1'

describe('사이트 데이터 파일 읽기', () => {
  let server: FakeFetch
  let client: DataClient

  beforeEach(() => {
    server = new FakeFetch()
    client = createDataClient(server.fetch, SITE_BASE)
  })

  describe('갱신 정보(meta)', () => {
    it('브라우저 캐시를 믿지 않고 매번 서버에 새 갱신이 있는지 확인한다', async () => {
      server.serve(META_URL, metaWithVersion('v1'))

      const meta = await client.meta()

      expect(meta.version).toBe('v1')
      expect(server.requestsTo(META_URL)).toEqual([{ url: META_URL, cache: 'no-cache' }])
    })
  })

  describe('나머지 데이터 파일', () => {
    it('갱신 버전을 주소에 붙여 새 배치가 돌았을 때만 다시 받는다', async () => {
      server.serve(STATS_URL, stats)

      await expect(client.stats('v1')).resolves.toEqual(stats)
      expect(server.requestsTo(STATS_URL)).toHaveLength(1)
    })

    it('트렌드 목록·최근 7일 새 CVE·검색 색인도 같은 버전 규칙으로 받는다', async () => {
      server
        .serve('/vuln-trend/data/index.json?v=v1', [])
        .serve('/vuln-trend/data/recent.json?v=v1', [])
        .serve('/vuln-trend/data/search-index.json?v=v1', [])

      await client.trendIndex('v1')
      await client.recent('v1')
      await client.searchIndex('v1')

      expect(server.requests.map((request) => request.url)).toEqual([
        '/vuln-trend/data/index.json?v=v1',
        '/vuln-trend/data/recent.json?v=v1',
        '/vuln-trend/data/search-index.json?v=v1',
      ])
    })

    it('한 화면에서 같은 파일을 여러 번 찾아도 한 번만 받는다', async () => {
      server.serve(STATS_URL, stats)

      await Promise.all([client.stats('v1'), client.stats('v1')])

      expect(server.requestsTo(STATS_URL)).toHaveLength(1)
    })

    it('받지 못한 파일은 다시 찾으면 새로 받는다', async () => {
      server.serve(STATS_URL, 'down', 503).serve(STATS_URL, stats)

      await expect(client.stats('v1')).rejects.toThrow(DataLoadError)
      await expect(client.stats('v1')).resolves.toEqual(stats)
    })

    it('받지 못하면 어느 파일이 어떤 상태로 실패했는지 알린다', async () => {
      server.serve(STATS_URL, 'down', 503)

      const failure = await client.stats('v1').catch((error: unknown) => error)

      expect(failure).toBeInstanceOf(DataLoadError)
      expect(failure).toMatchObject({ url: STATS_URL, status: 503 })
    })

    it('네트워크가 끊겨도 어느 파일이 실패했는지 알린다', async () => {
      const offline = createDataClient(async () => {
        throw new TypeError('Failed to fetch')
      }, SITE_BASE)

      await expect(offline.stats('v1')).rejects.toMatchObject({ url: STATS_URL, status: null })
    })
  })

  describe('CVE 상세', () => {
    const DETAIL_URL = '/vuln-trend/data/cve/CVE-2026-41203.json?v=v1'

    it('상세 파일이 있으면 그 CVE 의 상세를 준다', async () => {
      server.serve(DETAIL_URL, cveDetail)

      await expect(client.cveDetail('CVE-2026-41203', 'v1')).resolves.toEqual(cveDetail)
    })

    it('상세 파일을 만들지 않은 CVE 는 "없음"으로 알려 NVD 링크로 안내하게 한다', async () => {
      await expect(client.cveDetail('CVE-2026-41203', 'v1')).resolves.toBeNull()
    })

    it('상세 파일이 없는 것이 아니라 서버 오류면 실패로 알린다', async () => {
      server.serve(DETAIL_URL, 'down', 500)

      await expect(client.cveDetail('CVE-2026-41203', 'v1')).rejects.toThrow(DataLoadError)
    })
  })
})
