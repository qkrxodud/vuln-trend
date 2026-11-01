import { describe, expect, it } from 'vitest'
import css from '../index.css?raw'
import { fontPreloadTags, SITE_FONTS } from './fonts.ts'

const fontFaces = (text: string): string[] => text.match(/@font-face\s*\{[^}]*\}/g) ?? []

describe('사이트 글꼴 (외부 글꼴 서버 없이 사이트에서 직접 보냄)', () => {
  it('본문 글꼴과 숫자·CVE 번호용 고정폭 글꼴 두 개만 쓴다', () => {
    expect(SITE_FONTS.map((font) => font.family)).toEqual(['VulnTrend Sans', 'JetBrains Mono'])
  })

  it('첫 화면이 글꼴을 기다리지 않도록 두 글꼴을 사이트 경로 아래 fonts/ 에서 미리 받는다', () => {
    const tags = fontPreloadTags('/vuln-trend/')

    expect(tags.map((tag) => tag.attrs.href)).toEqual([
      '/vuln-trend/fonts/vulntrend-sans.woff2',
      '/vuln-trend/fonts/jetbrains-mono.woff2',
    ])
    tags.forEach((tag) => {
      expect(tag).toMatchObject({ tag: 'link', injectTo: 'head' })
      expect(tag.attrs).toMatchObject({ rel: 'preload', as: 'font', type: 'font/woff2', crossorigin: true })
    })
  })

  it('도메인을 연결해 사이트 경로가 / 이면 /fonts/ 에서 받는다', () => {
    expect(fontPreloadTags('/').map((tag) => tag.attrs.href)).toEqual([
      '/fonts/vulntrend-sans.woff2',
      '/fonts/jetbrains-mono.woff2',
    ])
  })

  it('화면 글꼴 정의는 미리 받는 파일과 같은 파일을 쓰고, 외부 주소를 부르지 않는다', () => {
    const urls = fontFaces(css).map((face) => face.match(/url\(['"]?([^'")]+)/)?.[1])

    expect(urls).toEqual(SITE_FONTS.map((font) => `/fonts/${font.file}`))
    expect(css).not.toMatch(/https?:\/\//)
    expect(css).not.toMatch(/@import\s+(url\()?['"]?https?:/)
  })

  it('글꼴이 늦게 와도 글자가 보이고, 쓰는 굵기(400~800)를 한 파일로 덮는다', () => {
    const [sans, mono] = fontFaces(css)

    expect(sans).toContain("font-family: 'VulnTrend Sans'")
    expect(sans).toContain('font-weight: 400 800')
    expect(mono).toContain("font-family: 'JetBrains Mono'")
    expect(mono).toContain('font-weight: 400 600')
    fontFaces(css).forEach((face) => expect(face).toContain('font-display: swap'))
  })

  it('화면 글꼴 이름이 자체 호스팅 글꼴을 먼저 찾는다', () => {
    expect(css).toMatch(/--font-sans: 'VulnTrend Sans',/)
    expect(css).toMatch(/--font-mono: 'JetBrains Mono',/)
  })
})
