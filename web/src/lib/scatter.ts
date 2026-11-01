// "어느 쪽이 더 위험한가" 산점도 (design/v6 b-scatter · CONSENSUS 4절 · CHART-REVIEW S1~S4·A1~A6).
// 칸은 사실(악용 가능성·심각도)만 말한다. 행동 기준은 CISA 기한 하나(빨강 ◆). 그리기는 컴포넌트, 좌표·라벨 계산은 여기
import type { IsoDate } from '../types/common.ts'
import type { TrendRow } from '../types/trend.ts'
import { dueInDays, isUrgent } from './urgency.ts'

/** 칸 경계: EPSS 30%, CVSS 7.0 */
export const EPSS_SPLIT = 0.3
export const CVSS_SPLIT = 7
/** 가로축: 0.1%~30% 는 로그로 폭의 40%, 30%~100% 는 균등으로 60% (S2) */
export const EPSS_MIN = 0.001
export const EPSS_SPLIT_AT = 0.4
/** 세로축: 4.0 미만은 바닥선, 10.0 점이 잘리지 않게 위 여백 10.4 (S3) */
export const CVSS_FLOOR = 4
export const CVSS_TOP = 10.4

export type Quadrant = 'hot' | 'severe' | 'likely' | 'low'

export const isPlaced = (row: TrendRow): boolean => row.epss !== null && row.cvss !== null

export const quadrantOf = (row: TrendRow): Quadrant | null => {
  if (row.epss === null || row.cvss === null) return null
  const likely = row.epss >= EPSS_SPLIT
  if (row.cvss >= CVSS_SPLIT) return likely ? 'hot' : 'severe'
  return likely ? 'likely' : 'low'
}

export const QUADRANT_NAME: Record<Quadrant, string> = {
  hot: '악용 가능성 높음·심각',
  severe: '심각·악용 가능성 낮음',
  likely: '악용 가능성 높음·덜 심각',
  low: '둘 다 낮음',
}

const log = (value: number) => Math.log10(Math.max(EPSS_MIN, value))

/** EPSS → 가로 위치 0~1 */
export const epssFraction = (epss: number): number => {
  if (epss <= EPSS_SPLIT) return (EPSS_SPLIT_AT * (log(epss) - log(EPSS_MIN))) / (log(EPSS_SPLIT) - log(EPSS_MIN))
  return EPSS_SPLIT_AT + ((1 - EPSS_SPLIT_AT) * (Math.min(1, epss) - EPSS_SPLIT)) / (1 - EPSS_SPLIT)
}

/** CVSS → 세로 위치 0(바닥)~1(위) */
export const cvssFraction = (cvss: number): number =>
  (Math.max(CVSS_FLOOR, Math.min(CVSS_TOP, cvss)) - CVSS_FLOOR) / (CVSS_TOP - CVSS_FLOOR)

/** 툴팁·표의 EPSS: "0.1% 미만" / "0.5%" / "27%" / 없으면 "—" */
export const formatScatterEpss = (epss: number | null): string => {
  if (epss === null) return '—'
  if (epss < 0.001) return '0.1% 미만'
  if (epss < 0.01) return `${(epss * 100).toFixed(1)}%`
  return `${Math.round(epss * 100)}%`
}

/** "D-2" / "3일 지남" / KEV 가 아니면 "" */
export const dueText = (row: TrendRow, today: IsoDate): string => {
  const due = dueInDays(row, today)
  if (due === null) return ''
  return due < 0 ? `${-due}일 지남` : `D-${due}`
}

export interface PlotFrame {
  /** 점이 놓이는 가로 범위(px) */
  left: number
  right: number
  /** 세로 범위(px): top 이 CVSS_TOP, bottom 이 CVSS_FLOOR */
  top: number
  bottom: number
}

export interface ScatterPoint {
  row: TrendRow
  x: number
  y: number
  /** 같은 자리(2px 안)에 놓인 점 수 */
  same: number
  /** 반경 10px 안 점 수(자기 포함) */
  near: number
  /** 점 크기(라벨·겹침 계산용) */
  radius: number
}

const SAME_PX = 2
const JITTER_PX = 3
const NEAR_PX = 10
const CLUSTER_PX = 12
const CLUSTER_MIN = 5

const radiusOf = (row: TrendRow, today: IsoDate): number => {
  if (isUrgent(row, today)) return 6
  if (row.kev_ransomware) return 8
  return 4.5
}

/** 좌표 있는 점을 점수순으로 놓고, 같은 자리는 세로로 0, +3, −3, +6 … 비켜 그린다(결정적, CONSENSUS 4절) */
export const layoutPoints = (rows: TrendRow[], frame: PlotFrame, today: IsoDate): ScatterPoint[] => {
  const placed: ScatterPoint[] = rows
    .filter(isPlaced)
    .sort((a, b) => b.score - a.score)
    .map((row) => ({
      row,
      x: frame.left + epssFraction(row.epss ?? 0) * (frame.right - frame.left),
      y: frame.top + (1 - cvssFraction(row.cvss ?? 0)) * (frame.bottom - frame.top),
      same: 1,
      near: 1,
      radius: radiusOf(row, today),
    }))
  const buckets = new Map<string, ScatterPoint[]>()
  placed.forEach((point) => {
    const key = `${Math.round(point.x / SAME_PX)}:${Math.round(point.y / SAME_PX)}`
    buckets.set(key, [...(buckets.get(key) ?? []), point])
  })
  const moved = new Map<ScatterPoint, ScatterPoint>()
  buckets.forEach((group) =>
    group.forEach((point, index) =>
      moved.set(point, {
        ...point,
        same: group.length,
        y: point.y + (index % 2 === 1 ? 1 : -1) * Math.ceil(index / 2) * JITTER_PX,
      }),
    ),
  )
  const ordered = placed.map((point) => moved.get(point) ?? point)
  return ordered.map((point) => ({
    ...point,
    near: ordered.filter((other) => (other.x - point.x) ** 2 + (other.y - point.y) ** 2 <= NEAR_PX ** 2).length,
  }))
}

export interface ClusterMark {
  x: number
  y: number
  count: number
}

/** 판단 구간(EPSS 30% 이상)에서 반경 12px 안 5건 이상 무리 → "n건" (S2) */
export const clusterMarks = (points: ScatterPoint[]): ClusterMark[] => {
  const used = new Set<ScatterPoint>()
  const marks: ClusterMark[] = []
  ;[...points]
    .sort((a, b) => b.near - a.near)
    .forEach((point) => {
      if (point.near < CLUSTER_MIN || used.has(point) || (point.row.epss ?? 0) < EPSS_SPLIT) return
      const members = points.filter(
        (other) => used.has(other) === false && (other.x - point.x) ** 2 + (other.y - point.y) ** 2 <= CLUSTER_PX ** 2,
      )
      if (members.length < CLUSTER_MIN) return
      members.forEach((member) => used.add(member))
      marks.push({
        x: members.reduce((sum, member) => sum + member.x, 0) / members.length,
        y: Math.min(...members.map((member) => member.y)),
        count: members.length,
      })
    })
  return marks
}

export interface LabelPlan {
  /** 라벨을 달 점, 다는 순서대로 */
  queue: ScatterPoint[]
  /** 기한 임박·랜섬웨어 전체 수와 이름을 단 수 */
  priorityTotal: number
  priorityShown: number
}

/** 라벨 예산: 기한 임박(남은 날 적은 순) → 랜섬웨어, 상한 넓은 화면 8·좁은 화면 4. 상한을 넘지 않으면 점수 상위 5(3) 추가 (CONSENSUS 8절) */
export const labelPlan = (
  points: ScatterPoint[],
  today: IsoDate,
  narrow: boolean,
  selectedId: string | null,
): LabelPlan => {
  const urgent = points
    .filter((point) => isUrgent(point.row, today))
    .sort((a, b) => (dueInDays(a.row, today) ?? 0) - (dueInDays(b.row, today) ?? 0) || b.row.score - a.row.score)
  const ransom = points
    .filter((point) => point.row.kev_ransomware && isUrgent(point.row, today) === false)
    .sort((a, b) => b.row.score - a.row.score)
  const priority = [...urgent, ...ransom]
  const cap = narrow ? 4 : 8
  const shown = priority.slice(0, cap)
  const rest = points.filter((point) => priority.includes(point) === false)
  const queue = [...shown, ...(priority.length > cap ? [] : rest.slice(0, narrow ? 3 : 5))]
  const selected = points.find((point) => point.row.id === selectedId)
  if (selected !== undefined && queue.includes(selected) === false) queue.push(selected)
  return { queue, priorityTotal: priority.length, priorityShown: shown.length }
}

const WIDE = /[\u1100-\u11ff\u3000-\u9fff\uac00-\ud7af\uff00-\uffef]/

/** 라벨 글자 폭 추정(px). 한글·전각 0.9em, 그 밖 0.52em(Pretendard), 고정폭(CVE 번호, JetBrains Mono) 0.6em */
export const textWidth = (text: string, fontSize: number, mono = false): number =>
  [...text].reduce((sum, char) => sum + (mono ? 0.6 : WIDE.test(char) ? 0.9 : 0.52) * fontSize, 0)

export interface Box {
  x: number
  y: number
  w: number
  h: number
}

export interface LabelSpot {
  x: number
  y: number
  anchor: 'start' | 'end' | 'middle'
  /** 점에서 떨어진 자리면 지시선을 긋는다 */
  lead: boolean
  box: Box
}

const LABEL_HEIGHT = 15

export const overlaps = (a: Box, b: Box): boolean =>
  a.x < b.x + b.w && b.x < a.x + a.w && a.y < b.y + b.h && b.y < a.y + a.h

/** 점 바로 옆(오른·왼·위·아래)부터 점점 멀리, 다른 상자와 겹치지 않고 그림 안에 드는 첫 자리. 없으면 `null` */
export const placeLabel = (
  point: { x: number; y: number; radius: number },
  width: number,
  obstacles: Box[],
  area: Box,
): LabelSpot | null => {
  const gap = point.radius + 6
  const near: Omit<LabelSpot, 'box'>[] = [
    { x: point.x + gap, y: point.y + 4, anchor: 'start', lead: false },
    { x: point.x - gap, y: point.y + 4, anchor: 'end', lead: false },
    { x: point.x, y: point.y - gap - 2, anchor: 'middle', lead: false },
    { x: point.x, y: point.y + gap + 11, anchor: 'middle', lead: false },
  ]
  const far: Omit<LabelSpot, 'box'>[] = [18, 36, 54, 72].flatMap((dy) =>
    [0, 24].flatMap((dx) =>
      [-1, 1].flatMap((side) => [
        { x: point.x + gap + dx, y: point.y + 4 + side * dy, anchor: 'start' as const, lead: true },
        { x: point.x - gap - dx, y: point.y + 4 + side * dy, anchor: 'end' as const, lead: true },
      ]),
    ),
  )
  const boxOf = (spot: Omit<LabelSpot, 'box'>): Box => ({
    x: spot.anchor === 'start' ? spot.x : spot.anchor === 'end' ? spot.x - width : spot.x - width / 2,
    y: spot.y - 12,
    w: width,
    h: LABEL_HEIGHT,
  })
  const fits = (box: Box) =>
    box.x >= area.x + 2 &&
    box.x + box.w <= area.x + area.w - 2 &&
    box.y >= area.y &&
    box.y + box.h <= area.y + area.h &&
    obstacles.some((obstacle) => overlaps(box, obstacle)) === false
  const spot = [...near, ...far].find((candidate) => fits(boxOf(candidate)))
  return spot === undefined ? null : { ...spot, box: boxOf(spot) }
}
