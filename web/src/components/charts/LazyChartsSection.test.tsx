import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { CHARTS_SLOT_CLASS } from './charts-slot.ts'
import { createLazyChartsSection, type ChartsModule } from './LazyChartsSection.tsx'

const loadedCharts: ChartsModule = { ChartsSection: () => <p>차트 그림</p> }

describe('차트 지연 로드 (5.12)', () => {
  it('차트 코드를 받는 동안 차트 높이만큼 자리를 잡고 불러오는 중이라고 알린다', () => {
    const Charts = createLazyChartsSection(() => new Promise<ChartsModule>(() => {}))

    render(<Charts />)

    expect(screen.getByRole('status')).toHaveTextContent('추이 차트 불러오는 중')
    expect(screen.getByRole('region', { name: '추이 차트' })).toHaveClass(...CHARTS_SLOT_CLASS.split(' '))
  })

  it('차트 코드를 받으면 차트를 그린다', async () => {
    const Charts = createLazyChartsSection(() => Promise.resolve(loadedCharts))

    render(<Charts />)

    expect(await screen.findByText('차트 그림')).toBeInTheDocument()
  })

  it('차트 코드를 받지 못하면 화면 전체를 깨지 않고 차트 자리에만 오류를 보이며, 다시 시도하면 다시 받는다', async () => {
    let attempts = 0
    const Charts = createLazyChartsSection(() => {
      attempts += 1
      return attempts === 1 ? Promise.reject(new Error('chunk load failed')) : Promise.resolve(loadedCharts)
    })
    const user = userEvent.setup()
    render(<Charts />)

    expect(await screen.findByRole('alert')).toHaveTextContent('추이 차트를 불러오지 못했습니다')
    expect(screen.getByRole('region', { name: '추이 차트' })).toHaveClass(...CHARTS_SLOT_CLASS.split(' '))
    await user.click(screen.getByRole('button', { name: '다시 시도' }))

    expect(await screen.findByText('차트 그림')).toBeInTheDocument()
    expect(attempts).toBe(2)
  })
})
