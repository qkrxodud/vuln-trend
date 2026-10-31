import { fireEvent, render, screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import type { Stats } from '../../types/stats.ts'
import { ChartsSection } from './ChartsSection.tsx'

const stats: Stats = {
  daily: [
    { date: '2026-10-29', new: 133, critical: 7 },
    { date: '2026-10-30', new: 142, critical: 11 },
  ],
  severity_7d: { critical: 52, high: 253, medium: 383, low: 113, unrated: 70 },
}

const renderCharts = () => {
  const server = new FakeFetch()
    .serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
    .serve('/vuln-trend/data/stats.json?v=v1', stats)
  render(
    <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
      <ChartsSection />
    </DataProvider>,
  )
}

describe('추이 차트', () => {
  it('통계를 받기 전에는 불러오는 중으로 보인다', () => {
    renderCharts()

    expect(screen.getByRole('status')).toHaveTextContent('추이 차트 불러오는 중')
  })

  describe('최근 14일 일별 신규', () => {
    it('그림을 못 보는 방문자도 날짜별 신규·Critical 건수를 표로 읽을 수 있다', async () => {
      renderCharts()

      const table = await screen.findByRole('table', { name: '최근 14일 일별 신규 건수' })
      const lastRow = within(table).getAllByRole('row').at(-1)
      if (lastRow === undefined) throw new Error('No rows in daily table')

      expect(within(lastRow).getByRole('rowheader')).toHaveTextContent('10/30')
      expect(
        within(lastRow)
          .getAllByRole('cell')
          .map((cell) => cell.textContent),
      ).toEqual(['142', '11'])
    })
  })

  describe('오늘 막대 (진행 중)', () => {
    it('마지막 갱신과 같은 날의 막대는 표에서도 갱신 시각까지 센 진행 중이라고 읽힌다', async () => {
      renderCharts()

      const table = await screen.findByRole('table', { name: '최근 14일 일별 신규 건수' })
      const headers = within(table)
        .getAllByRole('rowheader')
        .map((header) => header.textContent)

      expect(headers).toEqual(['10/29', '10/30 (진행 중, 09:00까지)'])
    })
  })

  describe('최근 7일 심각도 분포', () => {
    it('가운데에 7일 신규 합계를 보인다', async () => {
      renderCharts()

      const center = await screen.findByLabelText('도넛 가운데 값')

      expect(center).toHaveTextContent('최근 7일')
      expect(center).toHaveTextContent('871')
    })

    it('범례에 다섯 범주의 건수와 비율을 적는다 (미평가 포함)', async () => {
      renderCharts()

      const legend = await screen.findByRole('list', { name: '심각도 범례' })
      const items = within(legend).getAllByRole('listitem')

      expect(items.map((item) => item.textContent)).toEqual([
        'Critical52건6%',
        'High253건29%',
        'Medium383건44%',
        'Low113건13%',
        '미평가70건8%',
      ])
    })

    it('범례 항목에 키보드로 머물면 가운데에 그 범주의 비율과 건수를 보이고, 벗어나면 합계로 돌아간다', async () => {
      renderCharts()
      const legend = await screen.findByRole('list', { name: '심각도 범례' })
      const unrated = within(legend).getByRole('button', { name: /미평가/ })

      fireEvent.focus(unrated)
      expect(screen.getByLabelText('도넛 가운데 값')).toHaveTextContent('미평가8%70건')

      fireEvent.blur(unrated)
      expect(screen.getByLabelText('도넛 가운데 값')).toHaveTextContent('최근 7일871건')
    })

    it('범례 항목에 마우스를 올려도 가운데 값이 그 범주로 바뀐다', async () => {
      renderCharts()
      const legend = await screen.findByRole('list', { name: '심각도 범례' })
      const critical = within(legend).getByRole('button', { name: /Critical/ })

      fireEvent.mouseEnter(critical)
      expect(screen.getByLabelText('도넛 가운데 값')).toHaveTextContent('Critical6%52건')

      fireEvent.mouseLeave(critical)
      expect(screen.getByLabelText('도넛 가운데 값')).toHaveTextContent('최근 7일871건')
    })
  })
})
