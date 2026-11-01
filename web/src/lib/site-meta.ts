// 공유 미리보기 (기획서 8장, P23). 메인은 사이트 태그, CVE 상세는 빌드 뒤 정적 페이지로 그 CVE 의 태그를 단다
// vite.config.ts 가 빌드 때 쓴다. 그래서 DOM·import.meta 를 쓰지 않는다
import type { CveDetail } from '../types/cve.ts'

export const SITE_NAME = 'VulnTrend'
export const SITE_TITLE = 'VulnTrend — 지금 위험한 보안 취약점'
export const SITE_DESCRIPTION =
  'NVD·CISA KEV·EPSS를 2시간마다 확인해 실제 악용과 악용 확률로 점수를 매긴 보안 취약점 트렌드. 한국어 요약과 조치 기한을 함께 보여 줍니다.'
/** 링크드인 등은 그림 주소가 바뀌어야 다시 받는다. 그림을 바꾸면 판 번호를 올린다 (v2: v6 남색, 2026-11-01) */
export const OG_IMAGE_FILE = 'og-image-v2.png'
const OG_IMAGE_ALT = 'VulnTrend — 지금 위험한 보안 취약점 트렌드'

const escapeAttribute = (value: string): string =>
  value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;')
const escapeText = (value: string): string => value.replaceAll('&', '&amp;').replaceAll('<', '&lt;')

const property = (key: string, value: string) => `<meta property="${key}" content="${escapeAttribute(value)}" />`
const named = (key: string, value: string) => `<meta name="${key}" content="${escapeAttribute(value)}" />`

const baseOf = (siteUrl: string): string => (siteUrl.endsWith('/') ? siteUrl : `${siteUrl}/`)

interface PageTags {
  title: string
  description: string
  type: 'website' | 'article'
  /** 사이트 기준 경로. 메인은 '' */
  path: string
  canonical: boolean
}

// og:image 는 절대 주소여야 한다. 사이트 주소(VITE_SITE_URL)를 모르면 그림 없이 작은 카드로 둔다
const pageTags = (page: PageTags, siteUrl: string | undefined): string => {
  const common = [
    named('description', page.description),
    property('og:site_name', SITE_NAME),
    property('og:type', page.type),
    property('og:locale', 'ko_KR'),
    property('og:title', page.title),
    property('og:description', page.description),
  ]
  if (siteUrl === undefined || siteUrl === '') return [...common, named('twitter:card', 'summary')].join('\n    ')

  const base = baseOf(siteUrl)
  const url = `${base}${page.path}`
  const image = `${base}${OG_IMAGE_FILE}`
  return [
    ...(page.canonical ? [`<link rel="canonical" href="${escapeAttribute(url)}" />`] : []),
    ...common,
    property('og:url', url),
    property('og:image', image),
    property('og:image:secure_url', image),
    property('og:image:width', '1200'),
    property('og:image:height', '630'),
    property('og:image:alt', OG_IMAGE_ALT),
    named('twitter:card', 'summary_large_image'),
  ].join('\n    ')
}

export const siteMetaTags = (siteUrl: string | undefined): string =>
  pageTags({ title: SITE_TITLE, description: SITE_DESCRIPTION, type: 'website', path: '', canonical: false }, siteUrl)

/** 앱 셸에서 사이트 미리보기 태그 묶음을 찾아 바꾸기 위한 표시 */
export const SITE_META_START = '<!-- site-meta -->'
export const SITE_META_END = '<!-- /site-meta -->'

/** 메인 index.html 에 넣는 미리보기 묶음(표시 포함) */
export const siteMetaBlock = (siteUrl: string | undefined): string =>
  `${SITE_META_START}\n    ${siteMetaTags(siteUrl)}\n    ${SITE_META_END}`

// ── CVE 상세 정적 페이지 (공유 미리보기, 사용자 요청 2026-11-01) ─────────────────────────
// 빌드 뒤 dist/cve/<ID>/index.html 을 만든다. 404.html(SPA) 로는 링크드인·슬랙이 미리보기를 만들지 않아서다

export interface CvePageMeta {
  title: string
  description: string
  /** 사이트 기준 경로 `cve/CVE-…` */
  path: string
}

const TITLE_MAX = 90

const firstSentence = (text: string): string => text.trim().split(/(?<=[.!?。])\s/)[0]

const clip = (text: string, max: number): string => (text.length > max ? `${text.slice(0, max - 1)}…` : text)

/** og:title = "CVE-… — 한국어 요약 첫 문장(없으면 제목)", og:description = 제품·CISA 기한·점수 한 줄 */
export const cvePageMeta = (detail: CveDetail): CvePageMeta => {
  const headline = firstSentence(detail.summary?.what ?? detail.summary_what ?? detail.title)
  const due = detail.is_kev && detail.kev_due_date !== null ? `CISA 조치 기한 ${detail.kev_due_date}` : 'CISA KEV 아님'
  return {
    title: clip(`${detail.id} — ${headline}`, TITLE_MAX),
    description: `${detail.primary_product ?? '제품 미상'} · ${due} · 트렌드 점수 ${detail.score}`,
    path: `cve/${detail.id}`,
  }
}

// 앱 셸 HTML 의 <title> 과 미리보기 묶음(표시 사이)을 그 페이지 것으로 바꾼다
const withPageMeta = (shell: string, page: PageTags, tabTitle: string, siteUrl: string | undefined): string => {
  const start = shell.indexOf(SITE_META_START)
  const end = shell.indexOf(SITE_META_END)
  if (start < 0 || end < 0) throw new Error('site-meta markers not found in app shell')
  const withMeta = `${shell.slice(0, start)}${SITE_META_START}\n    ${pageTags(page, siteUrl)}\n    ${shell.slice(end)}`
  return withMeta.replace(/<title>[\s\S]*?<\/title>/, () => `<title>${escapeText(tabTitle)}</title>`)
}

/** 앱 셸 HTML 의 <title> 과 미리보기 묶음을 그 CVE 것으로 바꾼다 */
export const cvePageHtml = (shell: string, detail: CveDetail, siteUrl: string | undefined): string => {
  const page = cvePageMeta(detail)
  return withPageMeta(shell, { ...page, type: 'article', canonical: true }, `${page.title} | ${SITE_NAME}`, siteUrl)
}

const NEW_TITLE = '최근 7일 새 CVE'
const NEW_DESCRIPTION =
  '최근 7일 동안 새로 공개된 CVE 전부를 심각도·KEV·조치 정보·제품·공개일로 거르고 최신순·점수순으로 봅니다.'

/** /new(최근 7일 새 CVE) 정적 페이지. 404.html(SPA)이 아닌 200 으로 미리보기를 주려고 만든다 */
export const newPageHtml = (shell: string, siteUrl: string | undefined): string =>
  withPageMeta(
    shell,
    {
      title: `${NEW_TITLE} — ${SITE_NAME}`,
      description: NEW_DESCRIPTION,
      type: 'website',
      path: 'new',
      canonical: true,
    },
    `${NEW_TITLE} | ${SITE_NAME}`,
    siteUrl,
  )

const MINE_TITLE = '내 제품'
const MINE_DESCRIPTION =
  '쓰는 제품을 고르면 그 제품의 취약점만 모아 점수순·최신순으로 봅니다. 고른 제품은 이 브라우저에만 저장됩니다.'

/** /mine(내 제품) 정적 페이지. 고른 제품은 브라우저에만 있으므로 미리보기는 화면 소개만 담는다 */
export const minePageHtml = (shell: string, siteUrl: string | undefined): string =>
  withPageMeta(
    shell,
    {
      title: `${MINE_TITLE} — ${SITE_NAME}`,
      description: MINE_DESCRIPTION,
      type: 'website',
      path: 'mine',
      canonical: true,
    },
    `${MINE_TITLE} | ${SITE_NAME}`,
    siteUrl,
  )
