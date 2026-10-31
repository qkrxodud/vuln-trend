import type { Severity } from '../types/common.ts'

// 심각도 글자 배지 색. 디자인 원본 값, 기획서 8장 색상 규칙(심각도 5색 고정)
export const SEVERITY_BADGE: Record<Severity, { label: string; background: string; color: string }> = {
  CRITICAL: { label: 'Critical', background: '#f5e6e1', color: '#8a3a2a' },
  HIGH: { label: 'High', background: '#f8ecdf', color: '#8a5a2a' },
  MEDIUM: { label: 'Medium', background: '#f6f0dc', color: '#7a6420' },
  LOW: { label: 'Low', background: '#eeeae2', color: '#5a5a5d' },
  UNRATED: { label: '미평가', background: '#f2f0eb', color: '#69696c' },
}
