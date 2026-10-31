// JSON 계약 공통 타입 — 기획서 5장 스키마·7장 JSON 설계 기준

/** `YYYY-MM-DD` (KST 달력일). 예: `2026-11-13` */
export type IsoDate = string

/** ISO-8601, 오프셋 포함. 예: `2026-10-30T09:00:00+09:00` */
export type IsoDateTime = string

/** CVSS 심각도. CVSS 점수가 없는 건(Awaiting Analysis)은 `UNRATED` */
export type Severity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'UNRATED'

/** 점수를 매긴 CVSS 버전. 점수가 없으면 `null` */
export type CvssVersion = '2.0' | '3.0' | '3.1' | '4.0'
