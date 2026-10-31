// dist 를 GitHub Pages 처럼 서빙한다: base 경로 아래 정적 파일, 없는 경로는 404.html(상태 404), 캐시 max-age=600.
// overrides 로 data 파일 내용을 바꿔 끼워 stale·캐시 시나리오를 만든다
import { readFile } from 'node:fs/promises'
import { createServer, type Server } from 'node:http'
import type { AddressInfo } from 'node:net'
import { extname, join, normalize } from 'node:path'

const TYPES: Record<string, string> = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
}

// Pages 가 주는 캐시 헤더와 같게 둔다. 그래야 meta.json no-cache·?v= 가 실제로 효과가 있는지 볼 수 있다
const PAGES_CACHE_CONTROL = 'max-age=600'

export interface PagesServer {
  origin: string
  base: string
  /** base 아래 상대 경로(예: data/meta.json) → 대신 줄 본문 */
  overrides: Map<string, string>
  close: () => Promise<void>
}

const readOrNull = async (path: string): Promise<Buffer | null> => {
  try {
    return await readFile(path)
  } catch {
    return null
  }
}

export const startPagesServer = async (distDir: string, base: string): Promise<PagesServer> => {
  const overrides = new Map<string, string>()
  const notFound = await readFile(join(distDir, '404.html'))
  const server: Server = createServer((request, response) => {
    void (async () => {
      const pathname = decodeURIComponent(new URL(request.url ?? '/', 'http://localhost').pathname)
      const relative = pathname.startsWith(base) ? pathname.slice(base.length) : null
      const file = relative === null ? null : relative === '' ? 'index.html' : normalize(relative)
      const override = file === null ? undefined : overrides.get(file)
      const body =
        override !== undefined
          ? Buffer.from(override)
          : file === null || file.startsWith('..')
            ? null
            : await readOrNull(join(distDir, file))
      if (body === null || file === null) {
        response.writeHead(404, { 'Content-Type': TYPES['.html'] })
        response.end(notFound)
        return
      }
      response.writeHead(200, {
        'Content-Type': TYPES[extname(file)] ?? 'application/octet-stream',
        'Cache-Control': PAGES_CACHE_CONTROL,
      })
      response.end(body)
    })()
  })
  await new Promise<void>((resolve) => server.listen(0, '127.0.0.1', resolve))
  const { port } = server.address() as AddressInfo
  return {
    origin: `http://127.0.0.1:${port}`,
    base,
    overrides,
    close: () => new Promise((resolve) => server.close(() => resolve())),
  }
}
