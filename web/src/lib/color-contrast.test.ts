import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'
import { contrastRatio } from './color-contrast.ts'
import { SEVERITY_BADGE } from './severity.ts'

// 본문 크기 글자의 WCAG AA 기준
const AA_TEXT = 4.5
// 글자가 아닌 표시(아이콘·마크)의 WCAG 1.4.11 기준
const AA_GRAPHIC = 3

const token = (name: string): string => {
  const match = css.match(new RegExp(`--color-${name}:\\s*(#[0-9a-fA-F]{6})`))
  if (match === null) throw new Error(`token not found: ${name}`)
  return match[1]
}

describe('대비 계산', () => {
  it('검정 글자와 흰 바탕은 21:1, 같은 색끼리는 1:1 이다', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBeCloseTo(21, 5)
    expect(contrastRatio('#ffffff', '#000000')).toBeCloseTo(21, 5)
    expect(contrastRatio('#77777a', '#77777a')).toBeCloseTo(1, 5)
  })
})

describe('글자색 대비 (기획서 8장 색상·접근성, v6 토큰)', () => {
  // v6 토큰 (design/v6/parts/tokens.css, 사용자 결정 2026-11-01)
  const pairs: [string, string, string][] = [
    ['본문 글자 / 페이지 바탕', 'ink', 'page'],
    ['보조 글자 / 페이지 바탕', 'ink-2', 'page'],
    ['보조 글자 / 들어간 면', 'ink-2', 'surface-sunk'],
    ['축·캡션 글자 / 카드 면', 'ink-3', 'surface'],
    ['축·캡션 글자 / 페이지 바탕', 'ink-3', 'page'],
    ['축·캡션 글자 / 들어간 면', 'ink-3', 'surface-sunk'],
    ['기한 임박 글자 / 기한 임박 배지 바탕', 'urgent-ink', 'urgent-wash'],
    ['기한 임박 글자 / 카드 면', 'urgent', 'surface'],
    ['KEV 배지 글자 / 카드 면', 'kev-badge', 'surface'],
    ['경고 글자 / 경고 바탕', 'warn-ink', 'warn-bg'],
  ]
  const color = (name: string) => (name === 'white' ? '#ffffff' : token(name))

  it.each(pairs)('%s — 본문 기준 4.5:1 이상이다', (_name, foreground, background) => {
    expect(contrastRatio(color(foreground), color(background))).toBeGreaterThanOrEqual(AA_TEXT)
  })

  it.each(Object.values(SEVERITY_BADGE))('$label 심각도 배지 글자는 배지 바탕 위에서 4.5:1 이상이다', (badge) => {
    expect(contrastRatio(badge.color, badge.background)).toBeGreaterThanOrEqual(AA_TEXT)
  })
})

describe('표시 마크 대비 (글자가 아닌 그림, v6 토큰)', () => {
  const marks: [string, string, string][] = [
    ['수집 완료 ✓ / 페이지 바탕', 'ok', 'page'],
    ['기한 임박 ◆ / 카드 면', 'urgent', 'surface'],
    ['랜섬웨어 ● / 카드 면', 'ransom', 'surface'],
  ]

  it.each(marks)('%s — 그림 기준 3:1 이상이다', (_name, foreground, background) => {
    expect(contrastRatio(token(foreground), token(background))).toBeGreaterThanOrEqual(AA_GRAPHIC)
  })
})
