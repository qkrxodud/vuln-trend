import { describe, expect, it } from 'vitest'
import { formatGateTable, gateExitCode, type GateResult } from './report.ts'

const results: GateResult[] = [
  { item: 'stale 배너', status: 'pass', detail: '36시간 넘은 데이터에서 배너가 보임' },
  { item: 'search-index.json 크기', status: 'fail', detail: '5.0MB > 4MB' },
  { item: '한국어 요약 검수', status: 'review', detail: '의심 1건', evidence: ['CVE-1 action: sudo 명령어'] },
]

describe('Gate G 결과 표', () => {
  it('항목마다 통과·실패·확인 필요를 한 줄씩 표로 적는다', () => {
    const table = formatGateTable(results)

    expect(table).toContain('| 항목 | 결과 | 근거 |')
    expect(table).toContain('| stale 배너 | ✅ 통과 | 36시간 넘은 데이터에서 배너가 보임 |')
    expect(table).toContain('| search-index.json 크기 | ❌ 실패 | 5.0MB > 4MB |')
    expect(table).toContain('| 한국어 요약 검수 | ⚠️ 확인 필요 | 의심 1건 |')
  })

  it('확인 필요 항목의 근거 목록은 표 아래에 항목별로 붙인다', () => {
    const table = formatGateTable(results)

    expect(table).toMatch(/한국어 요약 검수[^\n]*\n(?:.*\n)*- CVE-1 action: sudo 명령어/)
  })

  it('표 칸을 깨는 | 와 줄바꿈은 근거에서 지운다', () => {
    const table = formatGateTable([{ item: 'a', status: 'pass', detail: 'x | y\nz' }])

    expect(table).toContain('| a | ✅ 통과 | x / y z |')
  })

  it('실패가 하나라도 있으면 종료 코드 1, 확인 필요만 있으면 0 이다 (사람이 판단)', () => {
    expect(gateExitCode(results)).toBe(1)
    expect(gateExitCode(results.filter((result) => result.status !== 'fail'))).toBe(0)
  })
})
