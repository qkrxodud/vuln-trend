import { lazy, Suspense, useState, type ComponentType, type ReactNode } from 'react'
import { ErrorState } from '../state/ErrorState.tsx'
import { LoadingState } from '../state/LoadingState.tsx'
import { CHARTS_LABEL, CHARTS_SLOT_CLASS } from './charts-slot.ts'

export type ChartsModule = { ChartsSection: () => ReactNode }

// 차트 자리. 받는 중·실패 모두 차트 높이만큼 자리를 잡아 아래 목록이 밀리지 않게 한다
const ChartsSlot = ({ children }: { children: ReactNode }) => (
  <section aria-label={CHARTS_LABEL} className={CHARTS_SLOT_CLASS}>
    {children}
  </section>
)

// Recharts 는 첫 화면 JS 의 절반이 넘는다(378KB). 차트만 따로 받아 KPI·목록을 먼저 그린다 (5.12)
// 청크를 받지 못해도(배포 직후 옛 청크 삭제 등) 화면 전체를 깨지 않고 차트 자리에만 다시 시도를 둔다
export const createLazyChartsSection = (load: () => Promise<ChartsModule>) => {
  const toLazy = () =>
    lazy(() =>
      load().then(
        (module): { default: ComponentType<{ onRetry: () => void }> } => ({ default: module.ChartsSection }),
        () => ({
          default: ({ onRetry }: { onRetry: () => void }) => (
            <ChartsSlot>
              <ErrorState label={CHARTS_LABEL} onRetry={onRetry} />
            </ChartsSlot>
          ),
        }),
      ),
    )

  return function LazyCharts() {
    const [Charts, setCharts] = useState(toLazy)
    return (
      <Suspense
        fallback={
          <ChartsSlot>
            <LoadingState label={CHARTS_LABEL} />
          </ChartsSlot>
        }
      >
        <Charts onRetry={() => setCharts(() => toLazy())} />
      </Suspense>
    )
  }
}

export const LazyChartsSection = createLazyChartsSection(() => import('./ChartsSection.tsx'))
