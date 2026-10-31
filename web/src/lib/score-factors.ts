// 상세 패널의 점수 구성 (기획서 6장 점수식, 8장 "0 이 아닌 항만")
import type { ScoreBreakdown } from '../types/trend.ts'

type FactorKey = Exclude<keyof ScoreBreakdown, 'decay'>

export interface ScoreFactor {
  key: FactorKey
  label: string
  value: number
  /** 그 항의 최대 점수 대비 막대 길이 % */
  widthPercent: number
}

// 항마다 받을 수 있는 최대 점수: KEV 50, 랜섬웨어 10, EPSS×30, 뉴스 min(5)×5, PoC min(3)×5, CVSS 10
const FACTORS: { key: FactorKey; label: string; max: number }[] = [
  { key: 'kev', label: '실제 악용 (KEV)', max: 50 },
  { key: 'ransomware', label: '랜섬웨어 사용', max: 10 },
  { key: 'epss', label: '악용 확률 (EPSS)', max: 30 },
  { key: 'news', label: '보안 뉴스 언급', max: 25 },
  { key: 'poc', label: '공개 PoC', max: 15 },
  { key: 'cvss', label: '심각도 (CVSS)', max: 10 },
]

export const toScoreFactors = (breakdown: ScoreBreakdown): ScoreFactor[] =>
  FACTORS.filter(({ key }) => breakdown[key] > 0).map(({ key, label, max }) => ({
    key,
    label,
    value: breakdown[key],
    widthPercent: Math.min(100, Math.round((breakdown[key] / max) * 100)),
  }))

const trimDecimal = (value: number): string => (Number.isInteger(value) ? String(value) : value.toFixed(1))

export const formatPoints = (value: number): string => `+${trimDecimal(value)}`

/** 감쇠는 0 이하. 빠진 점수를 수학 기호 빼기로 적는다 */
export const formatDecay = (decay: number): string => (decay < 0 ? `−${trimDecimal(-decay)}` : '0')
