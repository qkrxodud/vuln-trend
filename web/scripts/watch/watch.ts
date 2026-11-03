// 감시 판정을 GitHub 이슈로 알린다. 라벨 하나로 열린 이슈를 하나만 두고(중복 방지), 상황이 바뀔 때만 댓글을 단다.
// 상황 값은 이슈 본문·댓글 끝의 숨은 표시(<!-- site-watch:key=… -->)로 기억한다
import { formatKstDateTime } from '../../src/lib/kst-time.ts'
import { type DeployRun, type HealthReport, judgeHealth, type MetaProbe } from './site-health.ts'

export const WATCH_LABEL = 'site-watch'

export interface WatchIssue {
  number: number
  body: string
  comments: string[]
}

/** run-watch.ts 가 GitHub API·공개 사이트로 채운다. 시험은 가짜로 채운다 */
export interface WatchApi {
  probeMeta(): Promise<MetaProbe>
  deployRuns(): Promise<DeployRun[]>
  openIssue(): Promise<WatchIssue | null>
  createIssue(title: string, body: string): Promise<void>
  comment(issue: number, body: string): Promise<void>
  close(issue: number): Promise<void>
}

export type WatchAction =
  | { type: 'none'; reason: string }
  | { type: 'open'; title: string; body: string }
  | { type: 'comment'; issue: number; body: string }
  | { type: 'close'; issue: number; body: string }

const KEY_MARK = /<!-- site-watch:key=([^ ]+) -->/g

const marker = (key: string) => `<!-- site-watch:key=${key} -->`

const lastKey = (issue: WatchIssue): string | null =>
  [issue.body, ...issue.comments].flatMap((text) => [...text.matchAll(KEY_MARK)].map((match) => match[1])).at(-1) ??
  null

const CHECKLIST = [
  '확인할 것',
  '- Mac 이 켜져 있고 잠들지 않았는지, `run-daily.sh` 로그(배치 실행 결과)',
  '- 데이터 커밋이 push 됐는지 (`data: … KST` 커밋)',
  '- Actions 의 "Deploy web to GitHub Pages" 실행 결과',
]

const reportText = (report: HealthReport, now: Date): string =>
  [
    `**${report.summary}**`,
    '',
    ...report.problems.map((problem) => `- ${problem.text}`),
    '',
    `확인 시각 ${formatKstDateTime(now.toISOString())} · 매시 자동 확인(.github/workflows/watch.yml)`,
  ].join('\n')

export const planAction = (report: HealthReport, issue: WatchIssue | null, now: Date): WatchAction => {
  if (report.level === 'ok') {
    if (issue === null) return { type: 'none', reason: report.summary }
    return { type: 'close', issue: issue.number, body: `회복 — ${report.summary}\n\n${marker(report.key)}` }
  }
  if (issue === null) {
    const body = [reportText(report, now), '', ...CHECKLIST, '', marker(report.key)].join('\n')
    return { type: 'open', title: `[사이트 감시] ${report.summary}`, body }
  }
  if (lastKey(issue) === report.key) return { type: 'none', reason: `이미 알림: ${report.summary}` }
  return {
    type: 'comment',
    issue: issue.number,
    body: `상황이 바뀜\n\n${reportText(report, now)}\n\n${marker(report.key)}`,
  }
}

export const runWatch = async (api: WatchApi, now: Date): Promise<WatchAction> => {
  const [meta, runs, issue] = await Promise.all([api.probeMeta(), api.deployRuns(), api.openIssue()])
  const action = planAction(judgeHealth(meta, runs, now), issue, now)
  if (action.type === 'open') await api.createIssue(action.title, action.body)
  if (action.type === 'comment') await api.comment(action.issue, action.body)
  if (action.type === 'close') {
    await api.comment(action.issue, action.body)
    await api.close(action.issue)
  }
  return action
}
