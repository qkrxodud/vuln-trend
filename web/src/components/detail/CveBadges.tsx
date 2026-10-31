import { SEVERITY_BADGE } from '../../lib/severity.ts'
import type { TrendRow } from '../../types/trend.ts'
import { Badge } from '../trend/Badge.tsx'

// 상세(패널·페이지) 머리의 배지 묶음: KEV · 랜섬웨어 · 심각도 · 제품
export const CveBadges = ({ row }: { row: TrendRow }) => {
  const severity = SEVERITY_BADGE[row.severity]
  return (
    <>
      {row.is_kev ? <Badge tone="kev">KEV</Badge> : null}
      {row.kev_ransomware ? <Badge tone="ransomware">랜섬웨어</Badge> : null}
      <Badge style={{ background: severity.background, color: severity.color }}>{severity.label}</Badge>
      {row.primary_product === null ? null : <Badge tone="neutral">{row.primary_product}</Badge>}
    </>
  )
}
