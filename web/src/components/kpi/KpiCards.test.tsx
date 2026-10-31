import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { TrendFilterProvider } from '../../hooks/TrendFilterProvider.tsx'
import { useTrendFilter } from '../../hooks/use-trend-filter.ts'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { KpiCards } from './KpiCards.tsx'

const META_URL = '/vuln-trend/data/meta.json'

const ListFilterProbe = () => {
  const { filter } = useTrendFilter()
  return <output aria-label="목록 필터">{filter}</output>
}

const renderKpis = (server: FakeFetch) =>
  render(
    <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
      <TrendFilterProvider>
        <KpiCards />
        <ListFilterProbe />
      </TrendFilterProvider>
    </DataProvider>,
  )

const cardNamed = async (label: string): Promise<HTMLElement> => {
  const title = await screen.findByText(label)
  const card = title.closest('[data-kpi]')
  if (card === null) throw new Error(`KPI card not found: ${label}`)
  return card as HTMLElement
}

// 픽스처: 오늘 142·3·11·318, 어제 133·1·7·313
describe('오늘 현황 KPI 4개', () => {
  it('24시간 신규·KEV 신규·Critical·고위험을 오늘 값과 어제 대비 증감으로 보인다', async () => {
    renderKpis(new FakeFetch().serve(META_URL, metaWithVersion('v1')))

    const expected = [
      ['24시간 신규', '142', '+9'],
      ['KEV 신규', '3', '+2'],
      ['Critical', '11', '+4'],
      ['고위험 (EPSS 0.5+)', '318', '+5'],
    ]
    for (const [label, today, delta] of expected) {
      const card = await cardNamed(label)
      expect(within(card).getByText(today)).toBeInTheDocument()
      expect(within(card).getByText(delta)).toBeInTheDocument()
    }
  })

  it('증감은 화면 읽기 프로그램에 어제 값과 함께 말로 알린다', async () => {
    renderKpis(new FakeFetch().serve(META_URL, metaWithVersion('v1')))

    const card = await cardNamed('24시간 신규')

    expect(within(card).getByText('어제 133건보다 9건 늘었습니다')).toBeInTheDocument()
  })

  it('KEV 신규 카드를 누르면 목록이 KEV 만 보이고, 다시 누르면 전체로 돌아간다', async () => {
    renderKpis(new FakeFetch().serve(META_URL, metaWithVersion('v1')))
    const user = userEvent.setup()
    const kevCard = within(await cardNamed('KEV 신규')).getByRole('button')

    await user.click(kevCard)
    expect(kevCard).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByLabelText('목록 필터')).toHaveTextContent('kev')

    await user.click(kevCard)
    expect(kevCard).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByLabelText('목록 필터')).toHaveTextContent('all')
  })

  it('Critical 카드를 누르면 목록이 Critical 만 보인다', async () => {
    renderKpis(new FakeFetch().serve(META_URL, metaWithVersion('v1')))
    const user = userEvent.setup()

    await user.click(within(await cardNamed('Critical')).getByRole('button'))

    expect(screen.getByLabelText('목록 필터')).toHaveTextContent('critical')
  })

  it('필터가 없는 24시간 신규·고위험 카드는 누를 수 없고 어제 값을 함께 적는다', async () => {
    renderKpis(new FakeFetch().serve(META_URL, metaWithVersion('v1')))

    const newCard = await cardNamed('24시간 신규')
    const riskCard = await cardNamed('고위험 (EPSS 0.5+)')

    expect(within(newCard).queryByRole('button')).not.toBeInTheDocument()
    expect(within(riskCard).queryByRole('button')).not.toBeInTheDocument()
    expect(within(newCard).getByText('어제 133')).toBeInTheDocument()
  })

  it('갱신 정보를 받기 전에는 불러오는 중으로 보인다', () => {
    renderKpis(new FakeFetch().serve(META_URL, metaWithVersion('v1')))

    expect(screen.getByText(/오늘 현황 불러오는 중/)).toHaveAttribute('role', 'status')
  })
})
