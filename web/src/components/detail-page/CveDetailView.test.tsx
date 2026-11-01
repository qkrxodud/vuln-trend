import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { cveDetail, metaWithVersion } from '../../test/fixtures.ts'
import type { CveDetail } from '../../types/cve.ts'
import { CveDetailView } from './CveDetailView.tsx'

const detail: CveDetail = {
  ...cveDetail,
  title: 'Chrome V8 타입 혼동 원격 코드 실행',
  affected: [
    {
      vendor: 'google',
      product: 'chrome',
      display_name: 'Chrome',
      slug: 'chrome',
      ecosystem: null,
      vulnerable_range: '< 130.0.6723.58',
      fixed_version: null,
    },
  ],
  references: [{ url: 'https://vendor.example/advisory', source: 'vendor', title: 'Chrome 보안 공지' }],
  summary: {
    what: 'V8 엔진 타입 혼동으로 원격 코드가 실행됩니다.',
    who: 'Chrome 130 미만 사용자',
    action: 'Chrome 130.0.6723.58 이상으로 업데이트하세요.',
    model: 'mock',
    generated_at: '2026-10-30T09:00:00+09:00',
  },
}

const renderView = (served: CveDetail | null) => {
  const server = new FakeFetch().serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
  if (served !== null) server.serve('/vuln-trend/data/cve/CVE-2026-41203.json?v=v1', served)
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <CveDetailView cveId="CVE-2026-41203" />
      </DataProvider>
    </MemoryRouter>,
  )
}

const loaded = () => screen.findByRole('heading', { level: 1, name: 'CVE-2026-41203' })

describe('CVE 상세 페이지', () => {
  it('상세를 받기 전에는 불러오는 중으로 보인다', () => {
    renderView(detail)

    expect(screen.getByText(/취약점 상세 불러오는 중/)).toBeInTheDocument()
  })

  it('CVE-ID 를 가장 큰 제목으로, 그 아래 패널과 같은 v6 상세를 보인다', async () => {
    renderView(detail)
    await loaded()

    expect(screen.getByText(detail.title)).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 2, name: '무엇이 문제인가' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: '영향 범위' })).toHaveTextContent('Chrome')
    expect(screen.getByRole('region', { name: '참고 링크' })).toHaveTextContent('Chrome 보안 공지')
  })

  it('브라우저 탭 제목에 CVE-ID 를 넣는다', async () => {
    renderView(detail)
    await loaded()

    expect(document.title).toBe('CVE-2026-41203 — VulnTrend')
  })

  it('상세 파일을 만들지 않은 CVE 는 이유와 함께 NVD 원문으로 안내한다', async () => {
    renderView(null)

    expect(await loaded()).toBeInTheDocument()
    expect(screen.getByText(/상세 정보를 따로 만들지 않았습니다/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /nvd\.nist\.gov/ })).toHaveAttribute(
      'href',
      'https://nvd.nist.gov/vuln/detail/CVE-2026-41203',
    )
  })

  it('메인으로 돌아가는 링크가 있다', async () => {
    renderView(detail)

    expect(screen.getByRole('link', { name: '← 메인으로' })).toHaveAttribute('href', '/')
  })
})
