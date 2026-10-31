import type { ReactNode } from 'react'
import { CHARTS_LABEL, CHARTS_SLOT_CLASS } from './charts-slot.ts'

// 차트 자리. 받는 중·실패 모두 차트 높이만큼 자리를 잡아 아래 목록이 밀리지 않게 한다
export const ChartsSlot = ({ children }: { children: ReactNode }) => (
  <section aria-label={CHARTS_LABEL} className={CHARTS_SLOT_CLASS}>
    {children}
  </section>
)
