import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'
import { mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { cvePageHtml, siteMetaBlock } from './src/lib/site-meta.ts'
import type { CveDetail } from './src/types/cve.ts'

// GitHub Pages 프로젝트 사이트 기본 경로. 도메인을 연결하면 VITE_BASE=/ 로 빌드한다 (기획서 8장 프론트 기술, P8)
const DEFAULT_BASE = '/vuln-trend/'

// GitHub Pages 는 SPA 라우팅을 모른다. 없는 경로는 404.html 이 받으므로,
// 그 경로를 ?p= 로 실어 index.html 로 돌려보내고 src/spa-redirect.ts 가 원래 주소로 되돌린다.
const spaFallback404 = (): Plugin => {
  let base = DEFAULT_BASE
  return {
    name: 'vuln-trend-spa-404',
    apply: 'build',
    configResolved(config) {
      base = config.base
    },
    generateBundle() {
      this.emitFile({ type: 'asset', fileName: '404.html', source: render404(base) })
    },
  }
}

const render404 = (base: string): string => `<!doctype html>
<html lang="ko">
  <head>
    <meta charset="UTF-8" />
    <title>VulnTrend</title>
    <script>
      // 404 로 들어온 경로를 ?p= 에 담아 앱 첫 화면으로 보낸다
      ;(function () {
        var base = ${JSON.stringify(base)}
        var l = window.location
        var rest = l.pathname.indexOf(base) === 0 ? l.pathname.slice(base.length) : ''
        var target = base + (rest ? '?p=' + encodeURIComponent('/' + rest + l.search) : '') + l.hash
        l.replace(target)
      })()
    </script>
  </head>
  <body></body>
</html>
`

// 사이트 공유 미리보기 태그를 <title> 아래에 넣는다. VITE_SITE_URL 은 배포 워크플로가 Pages 주소로 준다 (5.13)
// 묶음을 표시로 감싸 두어 아래 CVE 정적 페이지가 찾아 바꾼다
const siteMeta = (siteUrl: string | undefined): Plugin => ({
  name: 'vuln-trend-site-meta',
  transformIndexHtml(html) {
    return html.replace('</title>', `</title>\n    ${siteMetaBlock(siteUrl)}`)
  },
})

// CVE 상세 정적 페이지: 빌드 뒤 dist/data/cve/*.json 마다 dist/cve/<ID>/index.html 을 쓴다.
// /cve/<ID> 가 404.html 대신 200 + 그 CVE 의 미리보기 태그로 응답한다(링크드인·슬랙 미리보기). 앱은 같은 셸이라 그대로 상세를 그린다
const cveStaticPages = (siteUrl: string | undefined): Plugin => {
  let outDir = 'dist'
  return {
    name: 'vuln-trend-cve-pages',
    apply: 'build',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir)
    },
    closeBundle() {
      const shell = readFileSync(join(outDir, 'index.html'), 'utf8')
      const detailDir = join(outDir, 'data', 'cve')
      const files = readdirSync(detailDir).filter((file) => file.endsWith('.json'))
      files.forEach((file) => {
        const detail = JSON.parse(readFileSync(join(detailDir, file), 'utf8')) as CveDetail
        const pageDir = join(outDir, 'cve', detail.id)
        mkdirSync(pageDir, { recursive: true })
        writeFileSync(join(pageDir, 'index.html'), cvePageHtml(shell, detail, siteUrl))
      })
      this.info(`CVE static pages: ${files.length}`)
    },
  }
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    base: env.VITE_BASE || DEFAULT_BASE,
    plugins: [react(), tailwindcss(), spaFallback404(), siteMeta(env.VITE_SITE_URL), cveStaticPages(env.VITE_SITE_URL)],
  }
})
