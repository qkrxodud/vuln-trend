import { useRef, useState, type KeyboardEvent, type MouseEvent, type PointerEvent, type ReactNode } from 'react'
import { useMeta, useTrendIndex } from '../../hooks/use-data.ts'
import { useElementSize } from '../../hooks/use-element-size.ts'
import { useListFilter } from '../../hooks/use-list-filter.ts'
import { useIsDesktop } from '../../hooks/use-media-query.ts'
import { useSelectCve } from '../../hooks/use-select-cve.ts'
import { defaultSelectedId, listTiers } from '../../lib/list-model.ts'
import {
  clusterMarks,
  CVSS_SPLIT,
  cvssFraction,
  dueText,
  EPSS_MIN,
  EPSS_SPLIT,
  epssFraction,
  formatScatterEpss,
  isPlaced,
  labelPlan,
  layoutPoints,
  placeLabel,
  QUADRANT_NAME,
  quadrantOf,
  textWidth,
  type Box,
  type ScatterPoint,
} from '../../lib/scatter.ts'
import { isUrgent, productName, referenceDate, URGENT_WITHIN_DAYS } from '../../lib/urgency.ts'
import type { TrendRow } from '../../types/trend.ts'
import { LoadableView } from '../state/LoadableView.tsx'

const FS = 12
const NEAR_HIT = 24
const PLOT_FALLBACK = { width: 640, height: 380 }

const shortId = (id: string) => id.replace(/^CVE-\d{4}-/, '')
const clip = (text: string, size: number) => (text.length > size ? `${text.slice(0, size - 1)}…` : text)
const quadrantText = (row: TrendRow) => {
  const quadrant = quadrantOf(row)
  return quadrant === null ? '좌표 없음' : QUADRANT_NAME[quadrant]
}
const tagsOf = (row: TrendRow, today: string) =>
  [
    isUrgent(row, today) ? `CISA 기한 ${dueText(row, today)}` : null,
    row.kev_ransomware ? '랜섬웨어 악용' : null,
    row.poc_count > 0 ? 'PoC 공개' : null,
    row.is_kev ? 'KEV' : 'KEV 아님',
  ]
    .filter((tag) => tag !== null)
    .join(' · ')

// 점 모양 (design/v6 CONSENSUS 2절): 기한 임박 = 빨강 ◆(한 단계 크게), 랜섬웨어 = ink 채운 원 + 고리, PoC = 속 빈 고리, 그 외 = 회색 점
const Mark = ({ point, today }: { point: ScatterPoint; today: string }) => {
  const { x, y, row } = point
  const urgent = isUrgent(row, today)
  const poc = row.poc_count > 0
  const diamond = (d: number) => `M${x} ${y - d}L${x + d} ${y}L${x} ${y + d}L${x - d} ${y}Z`
  if (urgent) {
    return (
      <g data-mark="urgent">
        <path d={diamond(10.5)} fill="var(--color-surface)" />
        <path
          d={diamond(8)}
          fill="var(--color-urgent)"
          stroke={row.kev_ransomware ? 'var(--color-ransom)' : 'none'}
          strokeWidth={row.kev_ransomware ? 2 : 0}
        />
        {poc ? <circle cx={x} cy={y} r={2.5} fill="var(--color-surface)" /> : null}
      </g>
    )
  }
  const radius = row.kev_ransomware ? 5 : 4.5
  const fill = row.kev_ransomware ? 'var(--color-ransom)' : poc ? 'var(--color-surface)' : 'var(--color-neutral-mark)'
  return (
    <g data-mark={row.kev_ransomware ? 'ransom' : poc ? 'poc' : 'other'}>
      {row.kev_ransomware ? (
        <circle cx={x} cy={y} r={radius + 3} fill="none" stroke="var(--color-ransom)" strokeWidth={1.5} />
      ) : null}
      <circle cx={x} cy={y} r={radius + 1.5} fill="var(--color-surface)" />
      {poc && row.kev_ransomware === false ? (
        <circle cx={x} cy={y} r={radius - 1} fill={fill} stroke="var(--color-poc-ring)" strokeWidth={2.5} />
      ) : (
        <circle cx={x} cy={y} r={radius} fill={fill} />
      )}
      {poc && row.kev_ransomware ? <circle cx={x} cy={y} r={2.2} fill="var(--color-surface)" /> : null}
      {(row.cvss ?? 0) < 4 ? (
        <path
          d={`M${x - radius - 10} ${y - 6}L${x - radius - 2} ${y - 6}L${x - radius - 6} ${y - 1}Z`}
          fill="var(--color-ink-3)"
        />
      ) : null}
    </g>
  )
}

const LegendGlyph = ({ kind }: { kind: 'urgent' | 'ransom' | 'poc' | 'other' }) => (
  <svg width={14} height={14} aria-hidden="true">
    {kind === 'urgent' ? <path d="M7 0.5 13.5 7 7 13.5 0.5 7Z" fill="var(--color-urgent)" /> : null}
    {kind === 'ransom' ? (
      <>
        <circle cx={7} cy={7} r={6.25} fill="none" stroke="var(--color-ransom)" strokeWidth={1.5} />
        <circle cx={7} cy={7} r={3.75} fill="var(--color-ransom)" />
      </>
    ) : null}
    {kind === 'poc' ? (
      <circle cx={7} cy={7} r={4.75} fill="var(--color-surface)" stroke="var(--color-poc-ring)" strokeWidth={2.5} />
    ) : null}
    {kind === 'other' ? <circle cx={7} cy={7} r={4.5} fill="var(--color-neutral-mark)" /> : null}
  </svg>
)

interface PlacedLabel {
  point: ScatterPoint
  x: number
  y: number
  anchor: 'start' | 'end' | 'middle'
  lead: boolean
  box: Box
  /** 이름 대신 번호만 단 경우 그 번호 */
  number: number | null
}

const Tooltip = ({
  point,
  today,
  width,
  height,
}: {
  point: ScatterPoint
  today: string
  width: number
  height: number
}) => {
  const { row } = point
  const tipWidth = 230
  const gap = point.radius + 8
  const left = point.x + gap + tipWidth > width ? Math.max(0, point.x - gap - tipWidth) : point.x + gap
  const top = Math.max(-20, Math.min(point.y - 60, height - 140))
  const rows: [string, string][] = [
    ['트렌드 점수', String(row.score)],
    ['악용 가능성 EPSS', formatScatterEpss(row.epss)],
    ['심각도 CVSS', (row.cvss ?? 0).toFixed(1)],
    ['CISA 기한', dueText(row, today) === '' ? '없음' : dueText(row, today)],
  ]
  return (
    <div
      role="tooltip"
      id="scatter-tip"
      className="pointer-events-none absolute z-10 w-max max-w-[260px] rounded-md bg-ink px-2.5 py-2 text-xs leading-[1.45] text-white shadow-[0_4px_16px_rgba(22,17,58,.18)]"
      style={{ left, top }}
    >
      <div className="font-mono font-semibold">{row.id}</div>
      <div className="mt-0.5 mb-1.5 truncate text-[#c9c8d2]">
        {productName(row)} · {row.title}
      </div>
      {rows.map(([name, value]) => (
        <div key={name} className="flex justify-between gap-4">
          <span className="text-[#c9c8d2]">{name}</span>
          <b className="tabular-nums">{value}</b>
        </div>
      ))}
      <div className="mt-1.5 font-semibold">
        {quadrantText(row)} · {tagsOf(row, today)}
      </div>
      {point.near > 1 ? (
        <div className="mt-1 text-[#c9c8d2]">
          {point.same > 1 ? `같은 자리 ${point.same}건 · ` : ''}이 근처 {point.near}건 — 표로 보기에서 모두 확인
        </div>
      ) : null}
      <div className="mt-1 text-[#c9c8d2]">누르면 아래 목록에서 선택</div>
    </div>
  )
}

interface PlotProps {
  rows: TrendRow[]
  today: string
  currentId: string | null
  onSelect: (cveId: string) => void
}

const Plot = ({ rows, today, currentId, onSelect }: PlotProps) => {
  const { ref, size } = useElementSize<HTMLDivElement>(PLOT_FALLBACK)
  const svgRef = useRef<SVGSVGElement>(null)
  const [hotId, setHotId] = useState<string | null>(null)
  const order = rows
    .filter(isPlaced)
    .sort((a, b) => b.score - a.score)
    .map((row) => row.id)
  const [roveId, setRoveId] = useState<string | null>(null)
  const rove = roveId ?? (currentId !== null && order.includes(currentId) ? currentId : (order[0] ?? null))

  // 기하 (design/v6 b-scatter draw): 좁은 화면(480 미만)·두 줄 축 설명(900 미만)
  const W = Math.max(280, size.width)
  const narrow = W < 480
  const H = Math.max(narrow ? 320 : 380, size.height)
  const twoLine = W < 900
  const m = { l: narrow ? 52 : 48, r: 14, t: 30, b: twoLine ? 60 : 44 }
  const pw = W - m.l - m.r
  const ph = H - m.t - m.b
  const x0 = m.l + 6
  const x1 = m.l + pw - 10
  const sx = (epss: number) => x0 + epssFraction(epss) * (x1 - x0)
  const sy = (cvss: number) => m.t + (1 - cvssFraction(cvss)) * ph

  const points = layoutPoints(rows, { left: x0, right: x1, top: m.t, bottom: m.t + ph }, today)
  const hot = rows.filter((row) => quadrantOf(row) === 'hot').length
  const quadrantLabels: { x: number; y: number; anchor: 'start' | 'end'; text: string; strong: boolean }[] = [
    {
      x: m.l + pw - 8,
      y: m.t - 9,
      anchor: 'end',
      text: `${narrow ? '악용 높음·심각' : '악용 가능성 높음 · 심각'} ${hot}건`,
      strong: true,
    },
    {
      x: m.l + 8,
      y: m.t - 9,
      anchor: 'start',
      text: narrow ? '심각·악용 낮음' : '심각 · 악용 가능성 낮음',
      strong: false,
    },
    {
      x: m.l + pw - 8,
      y: m.t + ph - 8,
      anchor: 'end',
      text: narrow ? '악용 높음·덜 심각' : '악용 가능성 높음 · 덜 심각',
      strong: false,
    },
    { x: m.l + 8, y: m.t + ph - 8, anchor: 'start', text: '둘 다 낮음', strong: false },
  ]
  const labelBox = (label: (typeof quadrantLabels)[number]): Box => {
    const w = textWidth(label.text, label.strong ? 13 : FS)
    return { x: (label.anchor === 'end' ? label.x - w : label.x) - 3, y: label.y - 13, w: w + 6, h: 17 }
  }
  const clusters = clusterMarks(points)
  const clusterBoxes = clusters.map((mark) => ({ x: mark.x - 14, y: mark.y - 22, w: 28, h: 15 }))

  // 라벨: 예산 안에서 기한 임박 → 랜섬웨어 → 점수 상위, 고른 점은 늘 시도. 자리가 없으면 우선 라벨만 번호로 (CONSENSUS 8절)
  const plan = labelPlan(points, today, narrow, currentId)
  const obstacles: Box[] = [
    ...points.map((p) => ({ x: p.x - p.radius - 2, y: p.y - p.radius - 2, w: 2 * p.radius + 4, h: 2 * p.radius + 4 })),
    ...quadrantLabels.map(labelBox),
    ...clusterBoxes,
  ]
  const area = { x: m.l, y: m.t - 24, w: pw, h: ph + 24 }
  const priority = plan.queue.slice(0, plan.priorityShown)
  const labels: PlacedLabel[] = []
  const numbered: string[] = []
  plan.queue.forEach((point) => {
    const { row } = point
    const prefix = isUrgent(row, today) ? `${dueText(row, today)} ` : row.kev_ransomware ? '랜섬웨어 ' : ''
    const name = `${row.id === currentId ? '✓ ' : ''}${prefix}${clip(productName(row), narrow ? 12 : 24)} `
    const spot = placeLabel(point, textWidth(name, FS) + textWidth(shortId(row.id), FS, true), obstacles, area)
    if (spot !== null) {
      labels.push({ point, ...spot, number: null })
      obstacles.push(spot.box)
      return
    }
    if (priority.includes(point) === false) return
    const number = numbered.length + 1
    const numberSpot = placeLabel(point, textWidth(String(number), FS), obstacles, area)
    if (numberSpot === null) return
    labels.push({ point, ...numberSpot, number })
    obstacles.push(numberSpot.box)
    numbered.push(`${number} ${prefix}${productName(row)} ${shortId(row.id)}`)
  })

  const notes = [
    numbered.length > 0 ? `번호 = ${numbered.join(' · ')}` : null,
    plan.priorityTotal > plan.priorityShown
      ? narrow
        ? `나머지 ${plan.priorityTotal - plan.priorityShown}건은 표로 보기`
        : `기한 임박·랜섬웨어 ${plan.priorityTotal}건 중 가장 급한 ${plan.priorityShown}건만 이름을 달았습니다 — 나머지 ${plan.priorityTotal - plan.priorityShown}건은 표로 보기`
      : null,
    rows.length > points.length ? `EPSS·CVSS 없는 ${rows.length - points.length}건은 그림에서 빠짐(표에는 있음)` : null,
    rows.some((row) => isUrgent(row, today)) || rows.length === 0
      ? null
      : `CISA 기한 ${URGENT_WITHIN_DAYS}일 안에 끝나는 건 없음`,
  ].filter((note) => note !== null)

  const hotPoint = points.find((p) => p.row.id === hotId) ?? null
  // 그리기 순서: 그 외 → PoC → 랜섬웨어 → 기한 임박 → 고른 점 (중요한 것이 위)
  const layer = (p: ScatterPoint) =>
    (p.row.id === currentId ? 8 : 0) +
    (isUrgent(p.row, today) ? 4 : 0) +
    (p.row.kev_ransomware ? 2 : 0) +
    (p.row.poc_count > 0 ? 1 : 0)
  const drawn = [...points].sort((a, b) => layer(a) - layer(b) || a.row.score - b.row.score)

  const nearest = (event: PointerEvent<SVGRectElement> | MouseEvent<SVGRectElement>) => {
    const bounds = svgRef.current?.getBoundingClientRect()
    const x = event.clientX - (bounds?.left ?? 0)
    const y = event.clientY - (bounds?.top ?? 0)
    return points.reduce<{ point: ScatterPoint | null; distance: number }>(
      (best, p) => {
        const distance = (p.x - x) ** 2 + (p.y - y) ** 2
        return distance < best.distance ? { point: p, distance } : best
      },
      { point: null, distance: NEAR_HIT ** 2 },
    ).point
  }

  const focusPoint = (id: string) => {
    const node = svgRef.current?.querySelector<SVGGElement>(`[data-id="${id}"]`)
    node?.focus()
  }

  const onKey = (event: KeyboardEvent<SVGGElement>, id: string) => {
    if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      onSelect(id)
      return
    }
    if (event.key === 'Escape') {
      setHotId(null)
      return
    }
    const index = order.indexOf(id)
    const step: Record<string, number> = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }
    const next =
      event.key === 'Home'
        ? 0
        : event.key === 'End'
          ? order.length - 1
          : step[event.key] === undefined
            ? -1
            : Math.min(order.length - 1, Math.max(0, index + step[event.key]))
    if (next < 0) return
    event.preventDefault()
    setRoveId(order[next])
    focusPoint(order[next])
  }

  const dim = (id: string) => hotId !== null && hotId !== id

  return (
    <>
      <div
        ref={ref}
        className="relative max-h-[560px] w-full min-w-0 flex-auto touch-pan-y"
        style={{ minHeight: narrow ? 320 : 380 }}
      >
        <svg
          ref={svgRef}
          role="group"
          aria-label="취약점별 악용 가능성(EPSS)과 심각도(CVSS). 화살표 키로 점수순 이동, Enter 로 선택하면 아래 목록에서 선택됩니다"
          viewBox={`0 0 ${W} ${H}`}
          className="absolute inset-0 block size-full overflow-visible"
        >
          <rect
            x={sx(EPSS_SPLIT)}
            y={m.t - 26}
            width={sx(1) - sx(EPSS_SPLIT)}
            height={sy(CVSS_SPLIT) - m.t + 26}
            fill="var(--color-surface-sunk)"
          />
          <g aria-hidden="true">
            {[4, 6, 8, 10].map((v) => (
              <g key={v}>
                <line
                  x1={m.l}
                  x2={m.l + pw}
                  y1={sy(v)}
                  y2={sy(v)}
                  stroke="var(--color-grid)"
                  shapeRendering="crispEdges"
                />
                <text x={m.l - 8} y={sy(v) + 4} textAnchor="end" fontSize={FS} fill="var(--color-ink-3)">
                  {v.toFixed(1)}
                </text>
              </g>
            ))}
            {[EPSS_MIN, 0.01, 0.1, 0.6, 1].map((v) => (
              <g key={v}>
                <line
                  x1={sx(v)}
                  x2={sx(v)}
                  y1={m.t}
                  y2={m.t + ph}
                  stroke="var(--color-grid)"
                  shapeRendering="crispEdges"
                />
                {narrow && (v === 0.1 || v === 0.6) ? null : (
                  <text
                    x={sx(v)}
                    y={m.t + ph + 16}
                    textAnchor={v === EPSS_MIN ? 'start' : v === 1 ? 'end' : 'middle'}
                    fontSize={FS}
                    fill="var(--color-ink-3)"
                  >
                    {v === EPSS_MIN ? '0.1%' : `${Math.round(v * 100)}%`}
                  </text>
                )}
              </g>
            ))}
            {[-3, 1].map((o) => (
              <line
                key={o}
                x1={sx(EPSS_SPLIT) + o - 3}
                y1={m.t + ph + 4}
                x2={sx(EPSS_SPLIT) + o + 3}
                y2={m.t + ph - 4}
                stroke="var(--color-ink-2)"
                strokeWidth={1.5}
              />
            ))}
            <line x1={m.l} x2={m.l + pw} y1={m.t + ph} y2={m.t + ph} stroke="var(--color-axis)" />
            <line x1={m.l} x2={m.l} y1={m.t} y2={m.t + ph} stroke="var(--color-axis)" />
            <line x1={sx(EPSS_SPLIT)} x2={sx(EPSS_SPLIT)} y1={m.t} y2={m.t + ph} stroke="var(--color-axis)" />
            <line x1={m.l} x2={m.l + pw} y1={sy(CVSS_SPLIT)} y2={sy(CVSS_SPLIT)} stroke="var(--color-axis)" />
            <text
              x={sx(EPSS_SPLIT)}
              y={m.t + ph + 16}
              textAnchor="middle"
              fontSize={FS}
              fontWeight={700}
              fill="var(--color-ink)"
            >
              30%
            </text>
            <text
              x={m.l - 8}
              y={sy(CVSS_SPLIT) + 4}
              textAnchor="end"
              fontSize={FS}
              fontWeight={700}
              fill="var(--color-ink)"
            >
              7.0
            </text>
            <text
              x={m.l + pw}
              y={twoLine ? H - 22 : H - 6}
              textAnchor="end"
              fontSize={FS}
              fontWeight={600}
              fill="var(--color-ink-2)"
            >
              {narrow ? '악용 가능성 EPSS →' : '악용 가능성 EPSS (30일 안 악용 확률) →'}
            </text>
            <text x={m.l} y={H - 6} textAnchor="start" fontSize={FS} fill="var(--color-ink-3)">
              {narrow
                ? '30% 왼쪽은 로그 눈금 · 4.0 미만은 바닥'
                : '30% 왼쪽은 로그, 오른쪽은 균등 눈금 · CVSS 4.0 미만은 바닥선에'}
              {points.some((p) => p.same > 1) ? (narrow ? ' · 같은 값 흩음' : ' · 같은 값은 살짝 흩어 그림') : ''}
            </text>
            <text
              fontSize={FS}
              fontWeight={600}
              fill="var(--color-ink-2)"
              textAnchor="end"
              transform={`translate(11 ${m.t}) rotate(-90)`}
            >
              심각도 CVSS →
            </text>
          </g>
          <g>
            {drawn.map((p) => {
              const selected = p.row.id === currentId
              return (
                <g
                  key={p.row.id}
                  data-id={p.row.id}
                  role="button"
                  tabIndex={p.row.id === rove ? 0 : -1}
                  aria-pressed={selected}
                  aria-describedby="scatter-tip"
                  aria-label={`${productName(p.row)} ${p.row.id}, 점수 ${p.row.score}, ${quadrantText(p.row)}, 악용 가능성 ${formatScatterEpss(p.row.epss)}, 심각도 ${(p.row.cvss ?? 0).toFixed(1)}, ${tagsOf(p.row, today)}`}
                  onFocus={() => {
                    setRoveId(p.row.id)
                    setHotId(p.row.id)
                  }}
                  onBlur={() => setHotId(null)}
                  onKeyDown={(event) => onKey(event, p.row.id)}
                  className="group outline-none"
                  style={{ opacity: dim(p.row.id) ? 0.3 : 1, transition: 'opacity 160ms ease-out' }}
                >
                  {selected ? (
                    <circle
                      cx={p.x}
                      cy={p.y}
                      r={p.radius + 5}
                      fill="none"
                      stroke="var(--color-ink)"
                      strokeWidth={2.5}
                    />
                  ) : null}
                  <circle
                    data-center
                    cx={p.x}
                    cy={p.y}
                    r={p.radius + 8}
                    fill="none"
                    className="stroke-none group-focus-visible:stroke-ink"
                    strokeWidth={2}
                    strokeDasharray="3 2"
                  />
                  <Mark point={p} today={today} />
                </g>
              )
            })}
          </g>
          <g aria-hidden="true">
            {clusters.map((mark) => (
              <text
                key={`${mark.x}:${mark.y}`}
                x={mark.x}
                y={mark.y - 10}
                textAnchor="middle"
                fontSize={FS}
                fontWeight={700}
                fill="var(--color-ink-2)"
              >
                {mark.count}건
              </text>
            ))}
            {labels.map((label) => {
              const { row } = label.point
              const selected = row.id === currentId
              const edgeX = label.anchor === 'start' ? label.box.x - 2 : label.box.x + label.box.w + 2
              const edgeY = label.box.y + label.box.h / 2
              const length = Math.hypot(edgeX - label.point.x, edgeY - label.point.y)
              return (
                <g key={row.id} data-label={row.id} style={{ opacity: dim(row.id) ? 0.3 : 1 }}>
                  {label.lead ? (
                    <line
                      x1={label.point.x + ((edgeX - label.point.x) / length) * (label.point.radius + 2)}
                      y1={label.point.y + ((edgeY - label.point.y) / length) * (label.point.radius + 2)}
                      x2={edgeX}
                      y2={edgeY}
                      stroke="var(--color-ink-3)"
                    />
                  ) : null}
                  <rect
                    x={label.box.x - 2}
                    y={label.box.y}
                    width={label.box.w + 4}
                    height={label.box.h}
                    rx={3}
                    fill="var(--color-surface)"
                    fillOpacity={0.85}
                  />
                  <text
                    x={label.x}
                    y={label.y}
                    textAnchor={label.anchor}
                    fontSize={FS}
                    fill="var(--color-ink)"
                    textDecoration={selected ? 'underline' : undefined}
                  >
                    {label.number === null ? (
                      <>
                        {selected ? <tspan fontWeight={700}>✓ </tspan> : null}
                        {isUrgent(row, today) ? <tspan fontWeight={700}>{dueText(row, today)} </tspan> : null}
                        {row.kev_ransomware && isUrgent(row, today) === false ? (
                          <tspan fontWeight={700}>랜섬웨어 </tspan>
                        ) : null}
                        <tspan fontWeight={600}>{clip(productName(row), narrow ? 12 : 24)}</tspan>
                        <tspan fill="var(--color-ink-2)" fontFamily="var(--font-mono)">
                          {' '}
                          {shortId(row.id)}
                        </tspan>
                      </>
                    ) : (
                      <tspan fontWeight={700}>{label.number}</tspan>
                    )}
                  </text>
                </g>
              )
            })}
            {quadrantLabels.map((label) => {
              const box = labelBox(label)
              return (
                <g key={label.text}>
                  <rect
                    x={box.x}
                    y={box.y + 2}
                    width={box.w}
                    height={box.h - 2}
                    rx={3}
                    fill="var(--color-surface)"
                    fillOpacity={0.9}
                  />
                  <text
                    x={label.x}
                    y={label.y}
                    textAnchor={label.anchor}
                    fontSize={label.strong ? 13 : FS}
                    fontWeight={label.strong ? 700 : 500}
                    fill={label.strong ? 'var(--color-ink)' : 'var(--color-ink-3)'}
                  >
                    {label.text}
                  </text>
                </g>
              )
            })}
          </g>
          {/* 마우스: 가장 가까운 점(24px 안)을 잡는다 — 점 100개의 히트 원이 서로 겹치지 않게 */}
          <rect
            data-hit
            x={0}
            y={0}
            width={W}
            height={H}
            fill="transparent"
            className={hotPoint === null ? 'cursor-crosshair' : 'cursor-pointer'}
            onPointerMove={(event) => setHotId(nearest(event)?.row.id ?? null)}
            onPointerLeave={() => setHotId(null)}
            onClick={(event) => {
              const point = nearest(event)
              if (point !== null) onSelect(point.row.id)
            }}
          />
        </svg>
        {points.length === 0 ? (
          <div className="absolute inset-0 grid place-items-center text-sm text-ink-3">그릴 취약점이 없습니다</div>
        ) : null}
        {hotPoint === null ? null : <Tooltip point={hotPoint} today={today} width={W} height={H} />}
      </div>
      {notes.length === 0 ? null : <p className="-mt-1 mb-0 text-xs text-ink-2">{notes.join(' · ')}</p>}
    </>
  )
}

const DataTable = ({ rows, today }: { rows: TrendRow[]; today: string }) => (
  <details className="text-xs text-ink-2">
    <summary className="w-max cursor-pointer text-ink-3">표로 보기</summary>
    <div className="mt-2 max-h-80 overflow-auto">
      <table className="w-full border-collapse tabular-nums">
        <thead>
          <tr>
            {['CVE', '제품', '점수', 'EPSS', 'CVSS', 'CISA 기한', '표시', '칸'].map((name) => (
              <th
                key={name}
                scope="col"
                className="sticky top-0 border-b border-grid bg-surface px-1.5 py-1 text-left font-semibold whitespace-nowrap text-ink-3"
              >
                {name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {[...rows]
            .sort((a, b) => b.score - a.score)
            .map((row) => {
              const marks = [
                isUrgent(row, today) ? '기한 임박' : null,
                row.kev_ransomware ? '랜섬웨어' : null,
                row.poc_count > 0 ? 'PoC' : null,
              ]
                .filter((mark) => mark !== null)
                .join(' · ')
              const cells: ReactNode[] = [
                row.id,
                productName(row),
                String(row.score),
                formatScatterEpss(row.epss),
                row.cvss === null ? '—' : row.cvss.toFixed(1),
                dueText(row, today) === '' ? '—' : dueText(row, today),
                marks === '' ? '—' : marks,
                quadrantText(row),
              ]
              return (
                <tr key={row.id}>
                  {cells.map((cell, index) => (
                    <td
                      key={index}
                      title={index === 1 ? productName(row) : undefined}
                      className={`border-b border-grid px-1.5 py-1 whitespace-nowrap ${index === 0 ? 'font-mono' : ''} ${index === 1 ? 'max-w-[180px] truncate' : ''}`}
                    >
                      {cell}
                    </td>
                  ))}
                </tr>
              )
            })}
        </tbody>
      </table>
    </div>
  </details>
)

const ScatterBody = ({ rows, today, selectedId }: { rows: TrendRow[]; today: string; selectedId: string | null }) => {
  const isDesktop = useIsDesktop()
  const select = useSelectCve()
  const { filter, clear } = useListFilter()
  const currentId = isDesktop ? (selectedId ?? defaultSelectedId(rows, today)) : null
  const urgentCount = rows.filter((row) => isUrgent(row, today)).length
  const ransomCount = rows.filter((row) => row.kev_ransomware).length
  const pocCount = rows.filter((row) => row.poc_count > 0).length
  const kevCount = rows.filter((row) => row.is_kev).length
  const selectedRow = rows.find((row) => row.id === currentId) ?? null

  // 점을 고르면 목록·상세가 같은 CVE 를 고른다. 목록 필터에 가려져 있으면 필터를 풀어 보이게 한다 (design/v6 c-list select)
  const choose = (cveId: string) => {
    const { urgent, rest } = listTiers(rows, filter, today)
    if ([...urgent, ...rest].some((row) => row.id === cveId) === false) clear()
    select(cveId)
  }

  const legend: { kind: 'urgent' | 'ransom' | 'poc' | 'other'; label: string; count: number | null }[] = [
    { kind: 'urgent' as const, label: `CISA 기한 ${URGENT_WITHIN_DAYS}일 안`, count: urgentCount },
    { kind: 'ransom' as const, label: '랜섬웨어 악용', count: ransomCount },
    { kind: 'poc' as const, label: 'PoC 공개', count: pocCount },
    { kind: 'other' as const, label: '그 외', count: null },
  ].filter((item) => item.count === null || item.count > 0)

  return (
    <>
      <div className="flex flex-wrap items-start justify-between gap-x-4 gap-y-2">
        <div>
          <h2 id="scatter-title" className="m-0 text-lg leading-[1.3] font-bold text-ink">
            어느 쪽이 더 위험한가
          </h2>
          <p className="mt-1 mb-0 text-[13px] text-ink-3">
            {rows.length === 0
              ? '표시할 취약점이 없습니다'
              : urgentCount > 0
                ? `빨강 ◆(CISA 기한 ${URGENT_WITHIN_DAYS}일 안)은 칸과 상관없이 먼저 · 그다음 오른쪽 위 칸(악용 가능성 30% 이상 · 심각도 7.0 이상) · 주목 ${rows.length}건 중 KEV ${kevCount}건`
                : `CISA 기한 ${URGENT_WITHIN_DAYS}일 안에 끝나는 건 없음 · 오른쪽 위 칸(악용 가능성 30% 이상 · 심각도 7.0 이상)부터 · 주목 ${rows.length}건 중 KEV ${kevCount}건`}
          </p>
        </div>
        <ul
          aria-label="점 모양 설명"
          className="m-0 flex list-none flex-wrap gap-x-3.5 gap-y-1.5 p-0 text-xs text-ink-2"
        >
          {legend.map((item) => (
            <li key={item.kind} className="flex items-center gap-1.5 whitespace-nowrap">
              <LegendGlyph kind={item.kind} />
              {item.label}
              {item.count === null ? null : <b className="text-ink tabular-nums"> {item.count}건</b>}
            </li>
          ))}
        </ul>
      </div>
      <Plot rows={rows} today={today} currentId={currentId} onSelect={choose} />
      {selectedRow === null ? null : (
        <p className="-mt-1 mb-0 text-xs font-semibold text-ink">
          ✓ 선택: {productName(selectedRow)} {selectedRow.id} — 아래 목록·상세에 표시 중
        </p>
      )}
      <DataTable rows={rows} today={today} />
    </>
  )
}

// "어느 쪽이 더 위험한가" 산점도 (design/v6 b-scatter, CONSENSUS 4·8·9절).
// 가로 EPSS 구간 스케일(로그 40% + 균등 60%), 세로 CVSS. 칸 이름은 사실 + 건수, 행동 기준은 빨강 ◆ 하나.
// 점을 누르거나 Enter 로 고르면 목록·상세가 같은 CVE 를 고른다 (useSelectCve)
export const ScatterCard = ({ selectedId }: { selectedId: string | null }) => {
  const meta = useMeta()
  const index = useTrendIndex()
  return (
    <section
      aria-labelledby="scatter-title"
      className="flex min-w-0 flex-col gap-3 rounded-[10px] border border-border bg-surface p-5"
    >
      <LoadableView loadable={meta} label="산점도">
        {(metaData) => (
          <LoadableView loadable={index} label="산점도">
            {(rows) => <ScatterBody rows={rows} today={referenceDate(metaData.updated_at)} selectedId={selectedId} />}
          </LoadableView>
        )}
      </LoadableView>
    </section>
  )
}
