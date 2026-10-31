// 사이트 수준 공유 미리보기 (기획서 8장, P23). CVE 별 OG 는 7단계 SEO 와 함께 한다
// vite.config.ts 가 빌드 때 index.html 머리에 넣는다. 그래서 DOM·import.meta 를 쓰지 않는다
export const SITE_NAME = 'VulnTrend'
export const SITE_TITLE = 'VulnTrend — 지금 위험한 보안 취약점'
export const SITE_DESCRIPTION =
  'NVD·CISA KEV·EPSS를 매일 모아 실제 악용과 악용 확률로 점수를 매긴 보안 취약점 트렌드. 한국어 요약과 조치 기한을 함께 보여 줍니다.'
export const OG_IMAGE_FILE = 'og-image.png'
const OG_IMAGE_ALT = 'VulnTrend — 지금 위험한 보안 취약점 트렌드'

const escapeAttribute = (value: string): string =>
  value.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;')

const property = (key: string, value: string) => `<meta property="${key}" content="${escapeAttribute(value)}" />`
const named = (key: string, value: string) => `<meta name="${key}" content="${escapeAttribute(value)}" />`

// og:image 는 절대 주소여야 한다. 사이트 주소(VITE_SITE_URL)를 모르면 그림 없이 작은 카드로 둔다
export const siteMetaTags = (siteUrl: string | undefined): string => {
  const common = [
    named('description', SITE_DESCRIPTION),
    property('og:site_name', SITE_NAME),
    property('og:type', 'website'),
    property('og:locale', 'ko_KR'),
    property('og:title', SITE_TITLE),
    property('og:description', SITE_DESCRIPTION),
  ]
  if (siteUrl === undefined || siteUrl === '') return [...common, named('twitter:card', 'summary')].join('\n    ')

  const base = siteUrl.endsWith('/') ? siteUrl : `${siteUrl}/`
  return [
    ...common,
    property('og:url', base),
    property('og:image', `${base}${OG_IMAGE_FILE}`),
    property('og:image:width', '1200'),
    property('og:image:height', '630'),
    property('og:image:alt', OG_IMAGE_ALT),
    named('twitter:card', 'summary_large_image'),
  ].join('\n    ')
}
