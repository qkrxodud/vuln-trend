import type { Severity } from '../types/common.ts'

// 심각도 글자 배지 색. v6 남색 램프 (design/v6/parts/tokens.css). 바탕은 들어간 면 하나, 글자 진하기로 등급을 나눈다
export const SEVERITY_BADGE: Record<Severity, { label: string; background: string; color: string }> = {
  CRITICAL: { label: 'Critical', background: '#f1f1f4', color: '#2b2a6e' },
  HIGH: { label: 'High', background: '#f1f1f4', color: '#4f4c9e' },
  MEDIUM: { label: 'Medium', background: '#f1f1f4', color: '#4a4660' },
  LOW: { label: 'Low', background: '#f1f1f4', color: '#4a4660' },
  UNRATED: { label: '미평가', background: '#f1f1f4', color: '#6b6880' },
}

/** 줄·상세에 쓰는 심각도 글자. 계약 밖 값(null, 모르는 등급)이 와도 화면 전체가 멈추지 않게 "미평가"로 적는다 */
export const severityLabel = (severity: Severity): string =>
  (SEVERITY_BADGE as Partial<Record<string, { label: string }>>)[String(severity)]?.label ??
  SEVERITY_BADGE.UNRATED.label
