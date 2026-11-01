import { dueInDays, URGENT_WITHIN_DAYS } from '../../lib/urgency.ts'
import type { TrendRow } from '../../types/trend.ts'
import { Diamond } from './UrgentBadge.tsx'

// v6 기호 세트 (design/v6/CONSENSUS.md 2절). 목록·상세가 같이 쓴다.
// 랜섬웨어 = ink 테두리 + 채운 원 ●, 새 KEV = 글자 배지, KEV = 조용한 회색 테두리(기본값이라), PoC = ink 테두리
const BADGE = 'inline-flex items-center gap-[5px] rounded-full px-2 py-0.5 text-xs leading-[1.4] whitespace-nowrap'

export const RansomBadge = () => (
  <span className={`${BADGE} bg-surface font-bold text-ink shadow-[inset_0_0_0_1.5px_var(--color-ransom)]`}>
    <span aria-hidden="true" className="inline-block size-2 rounded-full bg-ransom" />
    랜섬웨어
  </span>
)

export const NewKevBadge = ({ daysAgo }: { daysAgo: number }) => (
  <span className={`${BADGE} bg-surface-sunk font-semibold text-ink`}>
    새 KEV · {daysAgo === 0 ? '오늘' : `${daysAgo}일 전`}
  </span>
)

export const KevBadge = () => (
  <span className={`${BADGE} font-semibold text-kev-badge shadow-[inset_0_0_0_1px_var(--color-kev-badge)]`}>KEV</span>
)

export const PocBadge = ({ count }: { count: number }) => (
  <span className={`${BADGE} bg-surface font-bold text-ink shadow-[inset_0_0_0_1px_var(--color-ink)]`}>
    PoC {count}
  </span>
)

/** CISA 기한: 임박은 빨강 ◆ D-n, 아직 멀면 진한 글자 D-n, 지났으면 보조 글자 "n일 지남". KEV 가 아니면 없음 */
export const DueLabel = ({ row, today }: { row: TrendRow; today: string }) => {
  const due = dueInDays(row, today)
  if (due === null) return null
  if (due < 0) return <span className="text-xs whitespace-nowrap text-ink-2">CISA 기한 {-due}일 지남</span>
  if (due <= URGENT_WITHIN_DAYS) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-urgent-wash px-2 py-0.5 text-xs font-bold whitespace-nowrap text-urgent-ink">
        <Diamond />
        CISA 기한 D-{due}
      </span>
    )
  }
  return <span className="text-xs font-semibold whitespace-nowrap text-ink">CISA 기한 D-{due}</span>
}
