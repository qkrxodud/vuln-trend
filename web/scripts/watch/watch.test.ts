import { describe, expect, it } from 'vitest'
import type { DeployRun, MetaProbe } from './site-health.ts'
import { runWatch, type WatchApi, type WatchIssue } from './watch.ts'

const NOW = new Date('2026-11-02T03:00:00Z')
const FRESH: MetaProbe = { ok: true, updatedAt: '2026-11-02T11:01:00+09:00' }
const STALE: MetaProbe = { ok: true, updatedAt: '2026-11-02T04:48:00+09:00' }
const VERY_STALE: MetaProbe = { ok: true, updatedAt: '2026-10-31T09:00:00+09:00' }
const OK_RUN: DeployRun = {
  id: 2,
  status: 'completed',
  conclusion: 'success',
  url: 'https://github.com/qkrxodud/vuln-trend/actions/runs/2',
  sha: 'abcdef1234',
  createdAt: '2026-11-02T02:01:00Z',
  title: 'data: 2026-11-02 11:01 KST',
}

// GitHub 대신 이슈를 기억하는 가짜. 실제 이슈를 만들지 않는다
class FakeGitHub implements WatchApi {
  issues: (WatchIssue & { title: string; open: boolean })[] = []
  meta: MetaProbe = FRESH
  runs: DeployRun[] = [OK_RUN]
  async probeMeta() {
    return this.meta
  }
  async deployRuns() {
    return this.runs
  }
  async openIssue() {
    return this.issues.find((issue) => issue.open) ?? null
  }
  async createIssue(title: string, body: string) {
    this.issues.push({ number: this.issues.length + 1, title, body, comments: [], open: true })
  }
  async comment(issue: number, body: string) {
    this.issues.find((item) => item.number === issue)?.comments.push(body)
  }
  async close(issue: number) {
    const found = this.issues.find((item) => item.number === issue)
    if (found !== undefined) found.open = false
  }
}

describe('감시 이슈 (매시 확인)', () => {
  it('문제가 없고 열린 이슈도 없으면 아무것도 하지 않는다', async () => {
    const github = new FakeGitHub()

    const action = await runWatch(github, NOW)

    expect(action.type).toBe('none')
    expect(github.issues).toEqual([])
  })

  it('갱신이 기준을 넘으면 이슈를 하나 열고, 무엇을 확인할지 적는다', async () => {
    const github = new FakeGitHub()
    github.meta = STALE

    await runWatch(github, NOW)

    expect(github.issues).toHaveLength(1)
    const [issue] = github.issues
    expect(issue.title).toBe('[사이트 감시] 경고 — 데이터 갱신 7시간 12분째 멈춤')
    expect(issue.body).toContain('마지막 갱신 2026-11-02 04:48 KST')
    expect(issue.body).toContain('확인 시각 2026-11-02 12:00 KST')
    expect(issue.body).toContain('Mac')
    expect(issue.body).toContain('<!-- site-watch:key=warn:stale -->')
  })

  it('같은 문제가 이어지면 매시 새 이슈나 댓글을 만들지 않는다', async () => {
    const github = new FakeGitHub()
    github.meta = STALE
    await runWatch(github, NOW)

    const action = await runWatch(github, new Date(NOW.getTime() + 60 * 60 * 1000))

    expect(action.type).toBe('none')
    expect(github.issues).toHaveLength(1)
    expect(github.issues[0].comments).toEqual([])
  })

  it('열린 이슈가 있는데 상황이 바뀌면(경고→위험, 배포 실패 추가) 그 이슈에 댓글로 알린다', async () => {
    const github = new FakeGitHub()
    github.meta = STALE
    await runWatch(github, NOW)
    github.meta = VERY_STALE

    await runWatch(github, NOW)

    expect(github.issues).toHaveLength(1)
    expect(github.issues[0].comments).toHaveLength(1)
    expect(github.issues[0].comments[0]).toContain('위험')
    expect(github.issues[0].comments[0]).toContain('<!-- site-watch:key=critical:very-stale -->')
  })

  it('댓글로 알린 상황도 기억해, 그 뒤 같은 상황이면 다시 쓰지 않는다', async () => {
    const github = new FakeGitHub()
    github.meta = STALE
    await runWatch(github, NOW)
    github.meta = VERY_STALE
    await runWatch(github, NOW)

    await runWatch(github, NOW)

    expect(github.issues[0].comments).toHaveLength(1)
  })

  it('회복되면 회복 댓글을 남기고 이슈를 닫는다', async () => {
    const github = new FakeGitHub()
    github.runs = [{ ...OK_RUN, conclusion: 'failure' }]
    await runWatch(github, NOW)
    github.runs = [{ ...OK_RUN, id: 3 }, ...github.runs]

    const action = await runWatch(github, NOW)

    expect(action.type).toBe('close')
    expect(github.issues[0].open).toBe(false)
    expect(github.issues[0].comments[0]).toContain('회복')
  })

  it('닫힌 뒤 다시 문제가 생기면 새 이슈를 연다', async () => {
    const github = new FakeGitHub()
    github.meta = STALE
    await runWatch(github, NOW)
    github.meta = FRESH
    await runWatch(github, NOW)
    github.meta = STALE

    await runWatch(github, NOW)

    expect(github.issues.map((issue) => issue.open)).toEqual([false, true])
  })
})
