import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { createDataClient } from '../../api/data-client.ts'
import { DataProvider } from '../../hooks/DataProvider.tsx'
import { TrendFilterProvider } from '../../hooks/TrendFilterProvider.tsx'
import { FakeFetch } from '../../test/fake/fake-fetch.ts'
import { metaWithVersion } from '../../test/fixtures.ts'
import { trendRow } from '../../test/rows.ts'
import { TrendSection } from './TrendSection.tsx'

describe('트렌드 목록 영역', () => {
  it('트렌드 목록을 받아 보이고, 아무것도 고르지 않았으면 맨 위 취약점을 고른 상태로 둔다', async () => {
    const row = trendRow()
    const server = new FakeFetch()
      .serve('/vuln-trend/data/meta.json', metaWithVersion('v1'))
      .serve('/vuln-trend/data/index.json?v=v1', [row])
    render(
      <MemoryRouter>
        <DataProvider client={createDataClient(server.fetch, '/vuln-trend/')}>
          <TrendFilterProvider>
            <TrendSection selectedId={null} />
          </TrendFilterProvider>
        </DataProvider>
      </MemoryRouter>,
    )

    const card = await screen.findByRole('button', { name: new RegExp(row.id) })
    expect(card).toHaveAttribute('aria-pressed', 'true')

    await userEvent.setup().click(card)

    expect(card).toHaveAttribute('aria-pressed', 'true')
  })
})
