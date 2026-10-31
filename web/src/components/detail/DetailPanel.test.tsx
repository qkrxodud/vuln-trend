import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { cveDetail, metaWithVersion } from '../../test/fixtures.ts'
import type { CveDetail } from '../../types/cve.ts'
import { DetailPanel } from './DetailPanel.tsx'

const DETAIL_URL = '/vuln-trend/data/cve/CVE-2026-41203.json?v=v1'

const kevDetail: CveDetail = {
  ...cveDetail,
  kev_ransomware: true,
  kev_due_date: '2026-11-13',
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
  references: [{ url: 'https://chromereleases.googleblog.com/2026/10/stable.html', source: 'vendor', title: null }],
  summary: {
    what: 'V8 엔진 타입 혼동으로 원격 코드가 실행됩니다.',
    who: 'Chrome 130 미만을 쓰는 모든 사용자',
    action: 'Chrome 130.0.6723.58 이상으로 업데이트하세요.',
    model: 'mock',
    generated_at: '2026-10-30T09:00:00+09:00',
  },
}

const renderPanel = (detail: CveDetail | null) => {
  const server = new FakeFetch().serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
  if (detail !== null) server.serve(DETAIL_URL, detail)
  render(
    <MemoryRouter>
      <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
        <DetailPanel cveId="CVE-2026-41203" />
      </DataProvider>
    </MemoryRouter>,
  )
}

// 패널 틀은 바로 뜨고 내용은 상세를 받은 뒤 채워진다. 다 받았다는 안내가 나올 때까지 기다린다
const panel = async () => {
  await screen.findByText('CVE-2026-41203 상세 표시 중')
  return screen.getByRole('complementary', { name: '선택한 취약점 상세' })
}

describe('선택한 취약점 상세 패널', () => {
  it('고른 취약점의 상세를 받기 전에는 불러오는 중으로 보인다', () => {
    renderPanel(kevDetail)

    expect(screen.getByText(/취약점 상세 불러오는 중/)).toBeInTheDocument()
  })

  it('ID·배지·제목과 영향 버전·수정 버전·KEV 패치 기한을 보인다', async () => {
    renderPanel(kevDetail)
    const aside = await panel()

    expect(within(aside).getByText('CVE-2026-41203')).toBeInTheDocument()
    expect(within(aside).getByText('KEV')).toBeInTheDocument()
    expect(within(aside).getByText('랜섬웨어')).toBeInTheDocument()
    expect(within(aside).getByRole('heading', { name: cveDetail.title })).toBeInTheDocument()
    expect(within(aside).getByText('Chrome < 130.0.6723.58')).toBeInTheDocument()
    expect(within(aside).getByText('미정')).toBeInTheDocument()
    expect(within(aside).getByText('11-13')).toBeInTheDocument()
  })

  it('AI 요약이 있으면 무엇이·누가·조치를 AI 생성 안내와 함께 보인다', async () => {
    renderPanel(kevDetail)
    const aside = await panel()

    expect(within(aside).getByText('AI 생성 · 원문 확인 필요')).toBeInTheDocument()
    expect(within(aside).getByText('Chrome 130 미만을 쓰는 모든 사용자')).toBeInTheDocument()
  })

  it('요약이 없으면 영어 원문 설명을 보이고 요약 제공 범위를 알린다', async () => {
    renderPanel({ ...kevDetail, summary: null })
    const aside = await panel()

    expect(within(aside).getByText(cveDetail.description)).toBeInTheDocument()
    expect(within(aside).getByText('한국어 요약은 상위 20건에만 제공')).toBeInTheDocument()
  })

  it('점수 구성은 0 이 아닌 항과 시간 감쇠, 왜 지금? 을 보인다', async () => {
    renderPanel(kevDetail)
    const factors = within(await panel()).getByRole('list', { name: '점수 구성' })

    expect(
      within(factors)
        .getAllByRole('listitem')
        .map((item) => item.textContent),
    ).toEqual(['실제 악용 (KEV)+50', '악용 확률 (EPSS)+27.3', '심각도 (CVSS)+8.8', '시간 감쇠30일마다 절반−2.1'])
    expect(screen.getByText('CISA 악용 목록 등재')).toBeInTheDocument()
  })

  it('조치 문구와 벤더 어드바이저리·NVD 원문 링크를 보인다', async () => {
    renderPanel(kevDetail)
    const aside = await panel()

    expect(within(aside).getByText('Chrome 130.0.6723.58 이상으로 업데이트하세요.')).toBeInTheDocument()
    expect(within(aside).getByRole('link', { name: /chromereleases\.googleblog\.com/ })).toHaveAttribute(
      'href',
      'https://chromereleases.googleblog.com/2026/10/stable.html',
    )
    expect(within(aside).getByRole('link', { name: /NVD 원문/ })).toHaveAttribute(
      'href',
      'https://nvd.nist.gov/vuln/detail/CVE-2026-41203',
    )
  })

  it('요약이 없으면 조치는 벤더 어드바이저리 확인을 권한다', async () => {
    renderPanel({ ...kevDetail, summary: null })
    const aside = await panel()

    expect(
      within(aside).getByText('수정 버전으로 업데이트하세요. 자세한 조치는 벤더 어드바이저리를 확인하세요.'),
    ).toBeInTheDocument()
  })

  it('상세 페이지 열기 링크로 그 취약점의 상세 페이지에 갈 수 있다', async () => {
    renderPanel(kevDetail)

    expect(within(await panel()).getByRole('link', { name: '상세 페이지 열기 →' })).toHaveAttribute(
      'href',
      '/cve/CVE-2026-41203',
    )
  })

  it('상세 파일을 만들지 않은 취약점은 NVD 원문으로 안내한다', async () => {
    renderPanel(null)
    const aside = await panel()

    expect(within(aside).getByText('이 취약점은 상세 정보를 따로 만들지 않았습니다.')).toBeInTheDocument()
    expect(within(aside).getByRole('link', { name: /NVD 원문/ })).toBeInTheDocument()
  })

  it('패널이 열리면 화면 읽기 프로그램에 어떤 취약점을 보이는지 알린다', async () => {
    renderPanel(kevDetail)
    await panel()

    expect(screen.getByText('CVE-2026-41203 상세 표시 중')).toHaveAttribute('aria-live', 'polite')
  })
})
