// 외부 생존 감시(WBS §11a S1): 공개 사이트 meta.json 나이와 Pages 배포 결과를 판정한다.
// Mac 이 잠들거나 배포 시험이 실패해도 아무도 모르던 것(11-02 6f27a19)을 막는다. 입출력은 run-watch.ts 가 한다
import { formatKstDateTime } from '../../src/lib/kst-time.ts'

export type HealthLevel = 'ok' | 'warn' | 'critical'
export type ProblemKind = 'meta-unreadable' | 'stale' | 'very-stale' | 'deploy-failed'

export interface Problem {
  kind: ProblemKind
  level: Exclude<HealthLevel, 'ok'>
  text: string
}

export type MetaProbe = { ok: true; updatedAt: string } | { ok: false; reason: string }

export interface DeployRun {
  id: number
  status: string
  conclusion: string | null
  url: string
  sha: string
  createdAt: string
  title: string
}

export interface HealthReport {
  level: HealthLevel
  problems: Problem[]
  /** 같은 상황인지 가리는 값. 바뀔 때만 이슈에 댓글을 단다 */
  key: string
  summary: string
}

// 배치는 2시간마다 돈다. 6시간 = 두세 번 연속으로 빠짐, 26시간 = 하루를 넘김 (사용자 승인 2026-11-02)
export const WARN_AFTER_HOURS = 6
export const CRITICAL_AFTER_HOURS = 26

const MINUTE_MS = 60_000
const HOUR_MS = 60 * MINUTE_MS
const LEVEL_NAME: Record<HealthLevel, string> = { ok: '정상', warn: '경고', critical: '위험' }
const LEVEL_ORDER: HealthLevel[] = ['ok', 'warn', 'critical']
// 다음 push 에 밀려 취소됐거나 건너뛴 실행은 결과가 아니다
const UNSETTLED = new Set(['cancelled', 'skipped', 'neutral'])

/** `59분` / `7시간 12분`. 분 아래는 버린다 */
export const formatAge = (ms: number): string => {
  const minutes = Math.floor(ms / MINUTE_MS)
  if (minutes < 60) return `${minutes}분`
  return `${Math.floor(minutes / 60)}시간 ${minutes % 60}분`
}

export const latestSettledRun = (runs: DeployRun[]): DeployRun | null =>
  runs.find((run) => run.status === 'completed' && UNSETTLED.has(run.conclusion ?? '') === false) ?? null

const freshnessProblems = (meta: MetaProbe, now: Date): Problem[] => {
  if (meta.ok === false) {
    return [{ kind: 'meta-unreadable', level: 'critical', text: `공개 사이트 meta.json 을 읽지 못함 (${meta.reason})` }]
  }
  const updated = Date.parse(meta.updatedAt)
  if (Number.isNaN(updated)) {
    return [
      { kind: 'meta-unreadable', level: 'critical', text: `meta.json 의 갱신 시각을 읽지 못함 (${meta.updatedAt})` },
    ]
  }
  const age = Math.max(0, now.getTime() - updated)
  const since = `마지막 갱신 ${formatKstDateTime(meta.updatedAt)}`
  if (age > CRITICAL_AFTER_HOURS * HOUR_MS) {
    const text = `데이터 갱신이 ${formatAge(age)}째 멈춤 (${since}, 위험 기준 ${CRITICAL_AFTER_HOURS}시간)`
    return [{ kind: 'very-stale', level: 'critical', text }]
  }
  if (age > WARN_AFTER_HOURS * HOUR_MS) {
    const text = `데이터 갱신이 ${formatAge(age)}째 멈춤 (${since}, 경고 기준 ${WARN_AFTER_HOURS}시간)`
    return [{ kind: 'stale', level: 'warn', text }]
  }
  return []
}

const deployProblems = (runs: DeployRun[]): Problem[] => {
  const run = latestSettledRun(runs)
  if (run === null || run.conclusion === 'success') return []
  const text = `Pages 배포 실패 (${run.conclusion}): ${run.title} · ${run.sha.slice(0, 7)} · ${run.url}`
  return [{ kind: 'deploy-failed', level: 'warn', text }]
}

const shortText = (problem: Problem): string => problem.text.replace(/이 (.+)째 멈춤 \(.*\)$/, ' $1째 멈춤')

export const judgeHealth = (meta: MetaProbe, runs: DeployRun[], now: Date): HealthReport => {
  const problems = [...freshnessProblems(meta, now), ...deployProblems(runs)]
  if (problems.length === 0) {
    const age = meta.ok ? ` (${formatAge(now.getTime() - Date.parse(meta.updatedAt))} 전)` : ''
    const since = meta.ok ? `마지막 갱신 ${formatKstDateTime(meta.updatedAt)}${age}` : ''
    return { level: 'ok', problems, key: 'ok', summary: `정상 — ${since}` }
  }
  const level = problems
    .map((problem) => problem.level as HealthLevel)
    .reduce((worst, next) => (LEVEL_ORDER.indexOf(next) > LEVEL_ORDER.indexOf(worst) ? next : worst), 'ok')
  const kinds = problems.map((problem) => problem.kind).sort()
  const more = problems.length > 1 ? ` 외 ${problems.length - 1}건` : ''
  return {
    level,
    problems,
    key: `${level}:${kinds.join(',')}`,
    summary: `${LEVEL_NAME[level]} — ${shortText(problems[0])}${more}`,
  }
}
