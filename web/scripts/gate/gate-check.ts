// Gate G(WBS 9장) 화면 쪽 점검을 한 번에 돌린다: npm run gate:check
// 기본은 web/dist(빌드 결과, data 는 public/data 복사본). 다른 빌드를 보려면 --dist <폴더>
import { readdir, readFile } from 'node:fs/promises'
import { join, resolve } from 'node:path'
import type { CveDetail } from '../../src/types/cve.ts'
import { formatGateTable, gateExitCode, type GateResult } from './report.ts'
import { runBrowserChecks } from './run-browser.ts'
import { checkSearchIndexSize, checkSummaries, type FileSizes, measureSizes } from './static-checks.ts'

const distArg = process.argv.indexOf('--dist')
const distDir = resolve(distArg === -1 ? 'dist' : process.argv[distArg + 1])
const dataDir = join(distDir, 'data')

const sizesOrNull = async (path: string): Promise<FileSizes | null> => {
  try {
    return measureSizes(await readFile(path))
  } catch {
    return null
  }
}

const readDetails = async (): Promise<CveDetail[]> => {
  const files = (await readdir(join(dataDir, 'cve'))).filter((file) => file.endsWith('.json'))
  return Promise.all(
    files.map(async (file) => JSON.parse(await readFile(join(dataDir, 'cve', file), 'utf8')) as CveDetail),
  )
}

const browserResults = async (): Promise<GateResult[]> => {
  try {
    return await runBrowserChecks(distDir)
  } catch (error) {
    const message = error instanceof Error ? error.message.split('\n')[0] : String(error)
    return [{ item: '브라우저 점검', status: 'fail', detail: `Chrome 으로 열지 못함: ${message}` }]
  }
}

const results: GateResult[] = [
  ...(await browserResults()),
  checkSearchIndexSize(await sizesOrNull(join(dataDir, 'search-index.json'))),
  checkSummaries(await readDetails()),
]

console.log(`Gate G 화면 점검 — ${distDir}\n`)
console.log(formatGateTable(results))
console.log('\n제외(사용자·백엔드 몫): 배치 3일 연속 성공, 가중치 조정, 모바일 실기기, 레포 크기, 미매핑 제품')
process.exitCode = gateExitCode(results)
