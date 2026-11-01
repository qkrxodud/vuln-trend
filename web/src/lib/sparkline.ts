import { median } from './kpi.ts'

// KPI 스파크라인 좌표 (design/v6 T2): 완결된 날까지 실선 + 채운 끝점, 집계 중인 오늘은 점선 + 빈 점, 평소(중앙값) 가는 가로선
export interface SparkPoint {
  x: number
  y: number
}

export interface Sparkline {
  /** 완결된 날의 꺾은선 path */
  solid: string
  /** 마지막 완결일 → 오늘 점선 path. 오늘이 집계 중이 아니면 `null` */
  partial: string | null
  /** 마지막 완결일 점 */
  end: SparkPoint
  /** 집계 중인 오늘 점. 없으면 `null` */
  today: SparkPoint | null
  /** 평소(완결일 중앙값) 가로선 높이 */
  usualY: number
}

export interface SparkSize {
  width: number
  height: number
  pad: number
}

export const SPARK_SIZE: SparkSize = { width: 112, height: 40, pad: 5 }

/** 눈금 상한: 10의 거듭제곱 절반 단위로 올린다. 1983 → 2000, 530 → 550. 최소 1 */
export const niceCeil = (value: number): number => {
  const step = 10 ** Math.floor(Math.log10(Math.max(value, 1))) / 2
  return Math.max(Math.ceil(value / step) * step, 1)
}

const pathOf = (points: SparkPoint[]): string =>
  points.map((point, index) => `${index === 0 ? 'M' : 'L'}${point.x.toFixed(1)} ${point.y.toFixed(1)}`).join(' ')

// 급증일은 평소(중앙값)의 2.2배에서 자른다 — 추이 차트와 같은 규칙 (design/v6 CONSENSUS 5절)
export const sparkline = (values: number[], partial: boolean, size: SparkSize = SPARK_SIZE): Sparkline | null => {
  if (values.length < 2) return null
  const { width, height, pad } = size
  const complete = partial ? values.slice(0, -1) : values
  const usual = median(complete)
  const cap = niceCeil(usual * 2.2)
  const yOf = (value: number) => height - pad - (Math.min(value, cap) / cap) * (height - pad * 2)
  const points = values.map((value, index) => ({
    x: pad + (index / (values.length - 1)) * (width - pad * 2),
    y: yOf(value),
  }))
  const solidPoints = partial ? points.slice(0, -1) : points
  const end = solidPoints[solidPoints.length - 1]
  const today = partial ? points[points.length - 1] : null
  return {
    solid: pathOf(solidPoints),
    partial: today === null ? null : pathOf([end, today]),
    end,
    today,
    usualY: yOf(usual),
  }
}
