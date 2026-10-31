import { describe, expect, it } from 'vitest'
import indexHtml from '../../index.html?raw'
import { OG_IMAGE_FILE, SITE_DESCRIPTION, SITE_TITLE, siteMetaTags } from './site-meta.ts'

// 머리말 문자열을 DOM 으로 읽어 meta 값을 꺼낸다
const metaContent = (html: string, key: string): string | null => {
  const head = new DOMParser().parseFromString(`<head>${html}</head>`, 'text/html')
  const meta = head.querySelector(`meta[property="${key}"], meta[name="${key}"]`)
  return meta === null ? null : meta.getAttribute('content')
}

describe('사이트 공유 미리보기 (OG 태그, P23)', () => {
  it('어느 링크로 공유해도 사이트 이름·제목·설명을 한국어로 보인다', () => {
    const html = siteMetaTags(undefined)

    expect(metaContent(html, 'og:site_name')).toBe('VulnTrend')
    expect(metaContent(html, 'og:title')).toBe(SITE_TITLE)
    expect(metaContent(html, 'og:description')).toBe(SITE_DESCRIPTION)
    expect(metaContent(html, 'description')).toBe(SITE_DESCRIPTION)
    expect(metaContent(html, 'og:type')).toBe('website')
    expect(metaContent(html, 'og:locale')).toBe('ko_KR')
  })

  it('사이트 주소를 알면 대표 주소와 미리보기 그림을 절대 주소로 단다', () => {
    const html = siteMetaTags('https://example.github.io/vuln-trend/')

    expect(metaContent(html, 'og:url')).toBe('https://example.github.io/vuln-trend/')
    expect(metaContent(html, 'og:image')).toBe(`https://example.github.io/vuln-trend/${OG_IMAGE_FILE}`)
    expect(metaContent(html, 'og:image:width')).toBe('1200')
    expect(metaContent(html, 'og:image:height')).toBe('630')
    expect(metaContent(html, 'og:image:alt')).not.toBeNull()
    expect(metaContent(html, 'twitter:card')).toBe('summary_large_image')
  })

  it('사이트 주소 끝에 / 가 없어도 그림 주소가 어긋나지 않는다', () => {
    const html = siteMetaTags('https://example.github.io/vuln-trend')

    expect(metaContent(html, 'og:url')).toBe('https://example.github.io/vuln-trend/')
    expect(metaContent(html, 'og:image')).toBe(`https://example.github.io/vuln-trend/${OG_IMAGE_FILE}`)
  })

  it('사이트 주소를 모르면 깨진 상대 주소 대신 그림·대표 주소를 아예 빼고 작은 카드로 보인다', () => {
    for (const siteUrl of [undefined, '']) {
      const html = siteMetaTags(siteUrl)

      expect(metaContent(html, 'og:url')).toBeNull()
      expect(metaContent(html, 'og:image')).toBeNull()
      expect(metaContent(html, 'twitter:card')).toBe('summary')
    }
  })

  it('문구에 따옴표나 & 가 있어도 속성이 깨지지 않게 적는다', () => {
    const html = siteMetaTags('https://example.github.io/a?b=1&c="2"/')

    expect(metaContent(html, 'og:url')).toBe('https://example.github.io/a?b=1&c="2"/')
  })
})

describe('브라우저 탭 제목', () => {
  it('메인 탭 제목은 공유 미리보기 제목과 같다', () => {
    const title = new DOMParser().parseFromString(indexHtml, 'text/html').title

    expect(title).toBe(SITE_TITLE)
  })
})
