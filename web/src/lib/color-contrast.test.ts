import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'
import { contrastRatio } from './color-contrast.ts'
import { SEVERITY_BADGE } from './severity.ts'

// 본문 크기 글자의 WCAG AA 기준
const AA_TEXT = 4.5

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

describe('글자색 대비 (기획서 8장 색상·접근성, 5.12)', () => {
  const pairs: [string, string, string][] = [
    ['흐린 글자 / 흰 바탕', 'ink-faint', 'white'],
    ['흐린 글자 / 종이 바탕', 'ink-faint', 'paper'],
    ['흐린 글자 / 칩', 'ink-faint', 'chip'],
    ['옅은 글자 / 흰 바탕', 'ink-muted', 'white'],
    ['옅은 글자 / 종이 바탕', 'ink-muted', 'paper'],
    ['강조 글자 / 흰 바탕', 'accent', 'white'],
    ['강조 글자 / 선택한 행 바탕', 'accent', 'accent-soft'],
    ['KEV 글자 / KEV 바탕', 'kev', 'kev-bg'],
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
