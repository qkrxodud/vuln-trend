import { render, screen, within } from '@testing-library/react'
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

  describe('머리', () => {
    it('CVE-ID 와 배지, 제목을 보인다', async () => {
      renderView({ ...detail, kev_ransomware: true })
      await loaded()

      expect(screen.getByText('KEV')).toBeInTheDocument()
      expect(screen.getByText('랜섬웨어')).toBeInTheDocument()
      expect(screen.getByText(detail.title)).toBeInTheDocument()
    })

    it('트렌드 점수·EPSS·CVSS 세 점수를 크게 보인다', async () => {
      renderView(detail)
      await loaded()
      const scores = screen.getByRole('list', { name: '점수' })

      expect(
        within(scores)
          .getAllByRole('listitem')
          .map((item) => item.textContent),
      ).toEqual(['트렌드 점수98', 'EPSS0.91상위 1%', 'CVSS 3.18.8High'])
    })

    it('아직 EPSS·CVSS 가 없는 신규 건은 대시와 미평가로 적는다', async () => {
      renderView({ ...detail, epss: null, epss_percentile: null, cvss: null, cvss_version: null, severity: 'UNRATED' })
      await loaded()
      const scores = screen.getByRole('list', { name: '점수' })

      expect(
        within(scores)
          .getAllByRole('listitem')
          .map((item) => item.textContent),
      ).toEqual(['트렌드 점수98', 'EPSS—', 'CVSS미평가'])
    })

    it('영향 제품·버전과 수정 버전, KEV 패치 기한을 보인다', async () => {
      renderView(detail)
      await loaded()

      expect(screen.getByRole('list', { name: '영향 제품' })).toHaveTextContent('Chrome < 130.0.6723.58')
      expect(screen.getByText('미정')).toBeInTheDocument()
      expect(screen.getByText('11-13')).toBeInTheDocument()
    })
  })

  describe('본문', () => {
    it('한국어 요약 → 왜 지금? → 점수 구성 → 원문 설명 → 참고 링크 순서로 놓는다', async () => {
      renderView(detail)
      await loaded()

      expect(screen.getAllByRole('heading', { level: 2 }).map((heading) => heading.textContent)).toEqual([
        '한국어 요약',
        '왜 지금?',
        '점수 구성',
        '원문 설명',
        '참고 링크',
      ])
    })

    it('한국어 요약은 AI 생성 면책과 원문 링크를 함께 보인다', async () => {
      renderView(detail)
      await loaded()
      const summary = screen.getByRole('region', { name: '한국어 요약' })

      expect(within(summary).getByText('Chrome 130 미만 사용자')).toBeInTheDocument()
      expect(within(summary).getByText(/AI 가 생성한 요약입니다/)).toBeInTheDocument()
      expect(within(summary).getByRole('link', { name: 'NVD 원문 보기' })).toHaveAttribute(
        'href',
        'https://nvd.nist.gov/vuln/detail/CVE-2026-41203',
      )
    })

    it('한국어 요약이 없으면 순위와 상관없이 원문 설명을 보라고 안내한다', async () => {
      renderView({ ...detail, summary: null })
      await loaded()
      const summary = screen.getByRole('region', { name: '한국어 요약' })

      expect(within(summary).getByText('한국어 요약이 아직 없습니다. 원문 설명을 확인하세요.')).toBeInTheDocument()
      expect(within(summary).queryByText(/상위 20건/)).toBeNull()
    })

    it('원문 설명과 참고 링크(벤더·NVD)를 보인다', async () => {
      renderView(detail)
      await loaded()
      const references = screen.getByRole('region', { name: '참고 링크' })

      expect(screen.getByText(detail.description)).toBeInTheDocument()
      expect(within(references).getByRole('link', { name: /Chrome 보안 공지/ })).toHaveAttribute(
        'href',
        'https://vendor.example/advisory',
      )
      expect(within(references).getByRole('link', { name: /NVD 원문 CVE-2026-41203/ })).toBeInTheDocument()
    })
  })

  it('브라우저 탭 제목에 CVE-ID 를 넣는다', async () => {
    renderView(detail)
    await loaded()

    expect(document.title).toBe('CVE-2026-41203 — VulnTrend')
  })

  it('상세 파일을 만들지 않은 CVE 는 이유와 함께 NVD 원문으로 안내한다', async () => {
    renderView(null)
    await loaded()

    expect(screen.getByText(/상세 페이지는 트렌드 상위·최근 7일 목록에 오른 취약점만 만듭니다/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /NVD 원문 CVE-2026-41203/ })).toHaveAttribute(
      'href',
      'https://nvd.nist.gov/vuln/detail/CVE-2026-41203',
    )
  })

  it('메인으로 돌아가는 링크가 있다', async () => {
    renderView(detail)
    await loaded()

    expect(screen.getByRole('link', { name: '← 메인으로' })).toHaveAttribute('href', '/')
  })
})
