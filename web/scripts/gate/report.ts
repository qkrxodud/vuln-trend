// Gate G 점검 결과를 항목별 표로 낸다 (WBS 9장)
export type GateStatus = 'pass' | 'fail' | 'review'

export interface GateResult {
  item: string
  status: GateStatus
  detail: string
  /** 사람이 볼 근거 줄 (의심 목록 등) */
  evidence?: string[]
}

const STATUS_LABEL: Record<GateStatus, string> = {
  pass: '✅ 통과',
  fail: '❌ 실패',
  review: '⚠️ 확인 필요',
}

const cell = (text: string): string => text.replaceAll('|', '/').replaceAll('\n', ' ')

export const formatGateTable = (results: GateResult[]): string => {
  const rows = results.map(
    (result) => `| ${cell(result.item)} | ${STATUS_LABEL[result.status]} | ${cell(result.detail)} |`,
  )
  const evidence = results
    .filter((result) => result.evidence !== undefined && result.evidence.length > 0)
    .map((result) => [`### ${result.item}`, ...(result.evidence ?? []).map((line) => `- ${line}`)].join('\n'))
  return [['| 항목 | 결과 | 근거 |', '|---|---|---|', ...rows].join('\n'), ...evidence].join('\n\n')
}

// 확인 필요(review)는 사람이 판단하므로 종료 코드를 실패로 만들지 않는다
export const gateExitCode = (results: GateResult[]): number =>
  results.some((result) => result.status === 'fail') ? 1 : 0
