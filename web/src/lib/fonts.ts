// 글꼴 자체 호스팅: 외부 글꼴 서버 없이 public/fonts/ 의 woff2 를 사이트가 직접 보낸다.
// 글꼴이 데이터보다 늦게 와 글자가 다시 줄바꿈되며 밀리던 것(CLS)을 막으려고 첫 화면부터 미리 받는다.
// vite.config.ts 가 빌드 때 쓴다. 그래서 DOM·import.meta 를 쓰지 않는다.
//
// 만드는 법 (fonttools: pip install fonttools brotli)
// - vulntrend-sans.woff2: Pretendard 1.3.9 가변 글꼴을 굵기 400~800 으로 자르고(varLib.instancer)
//   KS X 1001(한자 제외)·라틴·문장 부호만 남긴 서브셋(pyftsubset). Pretendard 는 OFL 예약 글꼴 이름이라
//   수정본은 그 이름을 쓰지 않는다 → 글꼴 이름을 VulnTrend Sans 로 바꿨다 (라이선스: public/fonts/OFL-Pretendard.txt)
// - jetbrains-mono.woff2: JetBrains Mono 가변 글꼴(라틴)을 굵기 400~600 으로 자른 서브셋 (public/fonts/OFL-JetBrainsMono.txt)
export interface SiteFont {
  family: string
  file: string
}

/** index.css 의 @font-face 와 같은 순서·같은 파일이다 (fonts.test.ts 가 맞춰 본다) */
export const SITE_FONTS: SiteFont[] = [
  { family: 'VulnTrend Sans', file: 'vulntrend-sans.woff2' },
  { family: 'JetBrains Mono', file: 'jetbrains-mono.woff2' },
]

export interface FontPreloadTag {
  tag: 'link'
  attrs: Record<string, string | boolean>
  // <meta charset> 가 맨 앞에 오도록 <head> 끝에 둔다. 미리 받기는 위치와 상관없이 문서를 읽기 시작할 때 요청된다
  injectTo: 'head'
}

// 글꼴 미리 받기는 crossorigin 이 있어야 @font-face 요청과 같은 것으로 쳐서 두 번 받지 않는다
export const fontPreloadTags = (base: string): FontPreloadTag[] =>
  SITE_FONTS.map((font) => ({
    tag: 'link',
    attrs: { rel: 'preload', as: 'font', type: 'font/woff2', crossorigin: true, href: `${base}fonts/${font.file}` },
    injectTo: 'head',
  }))
