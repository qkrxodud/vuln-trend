import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig, loadEnv, type Plugin } from 'vite'

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

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    base: env.VITE_BASE || DEFAULT_BASE,
    plugins: [react(), tailwindcss(), spaFallback404()],
  }
})
