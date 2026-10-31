import { render } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { toDailyBars } from '../../lib/charts.ts'
import { DayTick } from './DailyNewChart.tsx'

const daily = Array.from({ length: 14 }, (_, i) => ({
  date: `2026-10-${String(19 + i).padStart(2, '0')}`,
  new: 100,
  critical: 5,
}))
const bars = toDailyBars([...daily.slice(1), { date: '2026-11-01', new: 5, critical: 0 }], '2026-11-01T09:00:00+09:00')

// 좁은 화면에서는 x축이 라벨을 건너뛰어, 눈금 순번이 막대 순번과 달라진다
const renderTick = (value: string, tickIndex: number) =>
  render(
    <svg>
      <DayTick x={0} y={0} index={tickIndex} payload={{ value }} bars={bars} />
    </svg>,
  )

describe('x축 날짜 라벨', () => {
  it('라벨을 건너뛴 좁은 화면에서도 오늘 날짜 아래에 "진행 중"을 단다', () => {
    const { container } = renderTick('11/1', 4)

    expect(container.querySelector('text')).toHaveTextContent('11/1진행 중')
    expect(container.querySelector('text')).toHaveAttribute('font-weight', '700')
  })

  it('지난 날짜에는 "진행 중"을 달지 않는다', () => {
    const { container } = renderTick('10/31', 13)

    expect(container.querySelector('text')).toHaveTextContent('10/31')
    expect(container.querySelector('text')).not.toHaveTextContent('진행 중')
  })
})
