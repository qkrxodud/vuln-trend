import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // 색 대비 테스트가 index.css 의 토큰 값을 ?raw 로 읽는다. 기본값은 CSS 를 빈 문자열로 바꾼다
    css: { include: [/src\/index\.css/] },
    include: ['src/**/*.test.{ts,tsx}'],
    coverage: {
      provider: 'v8',
      include: ['src/api/**', 'src/hooks/**', 'src/components/**', 'src/lib/**'],
      thresholds: { lines: 90, branches: 90, functions: 90, statements: 90 },
    },
  },
})
