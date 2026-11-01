import { describe, expect, it } from 'vitest'
import indexHtml from '../../index.html?raw'
import { cveDetail } from '../test/fixtures.ts'
import type { CveDetail } from '../types/cve.ts'
import {
  cvePageHtml,
  cvePageMeta,
  minePageHtml,
  newPageHtml,
  OG_IMAGE_FILE,
  SITE_DESCRIPTION,
  SITE_META_END,
  SITE_META_START,
  SITE_TITLE,
  siteMetaBlock,
  siteMetaTags,
} from './site-meta.ts'

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
    expect(metaContent(html, 'og:image:secure_url')).toBe(`https://example.github.io/vuln-trend/${OG_IMAGE_FILE}`)
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

describe('사이트 설명의 갱신 주기', () => {
  it('2시간마다 확인한다고 적고, 하루 한 번이라고 적지 않는다', () => {
    expect(SITE_DESCRIPTION).toContain('2시간마다 확인해')
    expect(SITE_DESCRIPTION).not.toContain('매일')
  })
})

describe('공유 미리보기 그림', () => {
  it('링크드인이 새로 받도록 그림 파일 이름에 판 번호를 붙인다', () => {
    expect(OG_IMAGE_FILE).toBe('og-image-v2.png')
  })
})

describe('CVE 상세 정적 페이지 (링크 공유 미리보기, 사용자 요청 2026-11-01)', () => {
  const detail: CveDetail = {
    ...cveDetail,
    id: 'CVE-2026-97229',
    title: '정규식 검증 미흡으로 원격 코드가 실행됩니다',
    primary_product: 'GitLab',
    score: 63,
    is_kev: true,
    kev_due_date: '2026-11-01',
    summary: {
      what: '정규식 검증 미흡으로 인증되지 않은 공격자가 코드를 실행할 수 있습니다. 19.2.8 미만이 영향받습니다.',
      who: 'GitLab 사용자',
      action: '업데이트하세요.',
      model: 'm',
      generated_at: '2026-11-01T09:00:00+09:00',
    },
  }
  const SHELL = `<!doctype html><html lang="ko"><head><title>${SITE_TITLE}</title>
    ${siteMetaBlock('https://example.github.io/vuln-trend/')}
    <script type="module" src="/vuln-trend/assets/index.js"></script></head><body><div id="root"></div></body></html>`

  it('제목은 CVE 번호와 한국어 요약 첫 문장, 설명은 제품·CISA 기한·점수 한 줄이다', () => {
    expect(cvePageMeta(detail)).toEqual({
      title: 'CVE-2026-97229 — 정규식 검증 미흡으로 인증되지 않은 공격자가 코드를 실행할 수 있습니다.',
      description: 'GitLab · CISA 조치 기한 2026-11-01 · 트렌드 점수 63',
      path: 'cve/CVE-2026-97229',
    })
  })

  it('요약이 없으면 제목을 쓰고, 제품·KEV 가 없으면 그렇다고 적는다', () => {
    const plain = {
      ...detail,
      summary: null,
      summary_what: null,
      primary_product: null,
      is_kev: false,
      kev_due_date: null,
    }

    expect(cvePageMeta(plain)).toMatchObject({
      title: 'CVE-2026-97229 — 정규식 검증 미흡으로 원격 코드가 실행됩니다',
      description: '제품 미상 · CISA KEV 아님 · 트렌드 점수 63',
    })
  })

  it('긴 제목은 90자 안으로 줄인다', () => {
    const long = cvePageMeta({ ...detail, summary: null, summary_what: null, title: '가'.repeat(200) })

    expect(long.title.length).toBeLessThanOrEqual(90)
    expect(long.title.endsWith('…')).toBe(true)
  })

  it('앱 셸의 탭 제목과 미리보기 묶음만 그 CVE 것으로 바꾸고, 스크립트는 그대로 둔다', () => {
    const html = cvePageHtml(SHELL, detail, 'https://example.github.io/vuln-trend/')
    const head = new DOMParser().parseFromString(html, 'text/html')
    const meta = (key: string) =>
      head.querySelector(`meta[property="${key}"], meta[name="${key}"]`)?.getAttribute('content')

    expect(head.title).toBe(
      'CVE-2026-97229 — 정규식 검증 미흡으로 인증되지 않은 공격자가 코드를 실행할 수 있습니다. | VulnTrend',
    )
    expect(meta('og:title')).toBe(cvePageMeta(detail).title)
    expect(meta('og:description')).toBe('GitLab · CISA 조치 기한 2026-11-01 · 트렌드 점수 63')
    expect(meta('og:url')).toBe('https://example.github.io/vuln-trend/cve/CVE-2026-97229')
    expect(head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.github.io/vuln-trend/cve/CVE-2026-97229',
    )
    expect(meta('og:image')).toBe(`https://example.github.io/vuln-trend/${OG_IMAGE_FILE}`)
    expect(meta('og:image:secure_url')).toBe(`https://example.github.io/vuln-trend/${OG_IMAGE_FILE}`)
    expect(meta('og:type')).toBe('article')
    expect(meta('twitter:card')).toBe('summary_large_image')
    expect(head.querySelectorAll('meta[property="og:title"]')).toHaveLength(1)
    expect(head.querySelector('script[type="module"]')?.getAttribute('src')).toBe('/vuln-trend/assets/index.js')
  })

  it('따옴표·꺾쇠·& 가 든 글도 속성과 제목이 깨지지 않게 적는다', () => {
    const tricky = { ...detail, summary: null, summary_what: null, title: 'A "quoted" <script>&amp</script>' }
    const head = new DOMParser().parseFromString(
      cvePageHtml(SHELL, tricky, 'https://example.github.io/vuln-trend/'),
      'text/html',
    )

    expect(head.title).toBe('CVE-2026-97229 — A "quoted" <script>&amp</script> | VulnTrend')
    expect(head.querySelector('meta[property="og:title"]')?.getAttribute('content')).toBe(
      'CVE-2026-97229 — A "quoted" <script>&amp</script>',
    )
    expect(head.querySelectorAll('script')).toHaveLength(1)
  })

  it('사이트 주소를 모르면 대표 주소·그림 없이 작은 카드로 둔다', () => {
    const shell = SHELL.replace(siteMetaBlock('https://example.github.io/vuln-trend/'), siteMetaBlock(undefined))
    const head = new DOMParser().parseFromString(cvePageHtml(shell, detail, undefined), 'text/html')

    expect(head.querySelector('meta[property="og:url"]')).toBeNull()
    expect(head.querySelector('link[rel="canonical"]')).toBeNull()
    expect(head.querySelector('meta[name="twitter:card"]')?.getAttribute('content')).toBe('summary')
  })

  it('메인 묶음은 표시로 감싸 빌드 뒤에도 찾아 바꿀 수 있다', () => {
    const block = siteMetaBlock(undefined)

    expect(block.startsWith(SITE_META_START)).toBe(true)
    expect(block.endsWith(SITE_META_END)).toBe(true)
  })
})

describe('최근 7일 새 CVE 정적 페이지 (/new 링크 공유)', () => {
  it('앱 셸의 탭 제목과 미리보기 묶음을 그 화면 것으로 바꾼다', () => {
    const shell = `<html><head><title>${SITE_TITLE}</title>
    ${siteMetaBlock('https://example.github.io/vuln-trend/')}
    <script type="module" src="/vuln-trend/assets/index.js"></script></head><body></body></html>`
    const head = new DOMParser().parseFromString(
      newPageHtml(shell, 'https://example.github.io/vuln-trend/'),
      'text/html',
    )
    const meta = (key: string) =>
      head.querySelector(`meta[property="${key}"], meta[name="${key}"]`)?.getAttribute('content')

    expect(head.title).toBe('최근 7일 새 CVE | VulnTrend')
    expect(meta('og:title')).toBe('최근 7일 새 CVE — VulnTrend')
    expect(meta('og:description')).toBe(
      '최근 7일 동안 새로 공개된 CVE 전부를 심각도·KEV·조치 정보·제품·공개일로 거르고 최신순·점수순으로 봅니다.',
    )
    expect(meta('og:url')).toBe('https://example.github.io/vuln-trend/new')
    expect(head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.github.io/vuln-trend/new',
    )
    expect(meta('og:image')).toBe(`https://example.github.io/vuln-trend/${OG_IMAGE_FILE}`)
  })
})

describe('내 제품 정적 페이지 (/mine 링크 공유)', () => {
  it('앱 셸의 탭 제목과 미리보기 묶음을 그 화면 것으로 바꾸되, 고른 제품은 담지 않는다', () => {
    const shell = `<html><head><title>${SITE_TITLE}</title>
    ${siteMetaBlock('https://example.github.io/vuln-trend/')}
    <script type="module" src="/vuln-trend/assets/index.js"></script></head><body></body></html>`
    const head = new DOMParser().parseFromString(
      minePageHtml(shell, 'https://example.github.io/vuln-trend/'),
      'text/html',
    )
    const meta = (key: string) =>
      head.querySelector(`meta[property="${key}"], meta[name="${key}"]`)?.getAttribute('content')

    expect(head.title).toBe('내 제품 | VulnTrend')
    expect(meta('og:title')).toBe('내 제품 — VulnTrend')
    expect(meta('og:description')).toBe(
      '쓰는 제품을 고르면 그 제품의 취약점만 모아 점수순·최신순으로 봅니다. 고른 제품은 이 브라우저에만 저장됩니다.',
    )
    expect(meta('og:url')).toBe('https://example.github.io/vuln-trend/mine')
    expect(head.querySelector('link[rel="canonical"]')?.getAttribute('href')).toBe(
      'https://example.github.io/vuln-trend/mine',
    )
    expect(meta('og:image')).toBe(`https://example.github.io/vuln-trend/${OG_IMAGE_FILE}`)
  })
})
