// 매시 감시 실행 껍데기 (.github/workflows/watch.yml): node scripts/watch/run-watch.ts
// GITHUB_REPOSITORY·GITHUB_TOKEN 은 Actions 가 준다. SITE_URL 이 없으면 <owner>.github.io/<repo>/ 로 본다.
// WATCH_DRY_RUN=1 이면 읽기만 하고 이슈를 쓰지 않는다(로컬 확인용)
import { appendFileSync } from 'node:fs'
import type { DeployRun, MetaProbe } from './site-health.ts'
import { runWatch, WATCH_LABEL, type WatchApi, type WatchIssue } from './watch.ts'

const API = 'https://api.github.com'
const repo = process.env.GITHUB_REPOSITORY ?? ''
const [owner, name] = repo.split('/')
if (owner === undefined || name === undefined) throw new Error('GITHUB_REPOSITORY must be owner/repo')
const siteUrl = process.env.SITE_URL || `https://${owner.toLowerCase()}.github.io/${name}/`
const token = process.env.GITHUB_TOKEN ?? ''
const dryRun = process.env.WATCH_DRY_RUN === '1'
const now = new Date()

const github = async (path: string, init: RequestInit = {}): Promise<Response> => {
  const headers: Record<string, string> = {
    accept: 'application/vnd.github+json',
    'x-github-api-version': '2022-11-28',
    ...(token === '' ? {} : { authorization: `Bearer ${token}` }),
  }
  const response = await fetch(`${API}/repos/${repo}${path}`, { ...init, headers })
  if (response.ok || response.status === 404) return response
  throw new Error(`GitHub API ${init.method ?? 'GET'} ${path} failed: ${response.status} ${await response.text()}`)
}

const write = async (method: string, path: string, body: unknown): Promise<void> => {
  if (dryRun) {
    console.log(`[dry-run] ${method} ${path} ${JSON.stringify(body)}`)
    return
  }
  await github(path, { method, body: JSON.stringify(body) })
}

interface RunsResponse {
  workflow_runs: {
    id: number
    status: string
    conclusion: string | null
    html_url: string
    head_sha: string
    created_at: string
    display_title: string
  }[]
}

const api: WatchApi = {
  async probeMeta(): Promise<MetaProbe> {
    try {
      const response = await fetch(`${siteUrl}data/meta.json?watch=${now.getTime()}`, { cache: 'no-store' })
      if (response.ok === false) return { ok: false, reason: `HTTP ${response.status}` }
      const meta = (await response.json()) as { updated_at?: unknown }
      return { ok: true, updatedAt: String(meta.updated_at) }
    } catch (error) {
      return { ok: false, reason: error instanceof Error ? error.message : String(error) }
    }
  },
  async deployRuns(): Promise<DeployRun[]> {
    const response = await github('/actions/workflows/pages.yml/runs?branch=main&per_page=10')
    if (response.status === 404) return []
    const { workflow_runs } = (await response.json()) as RunsResponse
    return workflow_runs.map((run) => ({
      id: run.id,
      status: run.status,
      conclusion: run.conclusion,
      url: run.html_url,
      sha: run.head_sha,
      createdAt: run.created_at,
      title: run.display_title,
    }))
  },
  async openIssue(): Promise<WatchIssue | null> {
    const response = await github(`/issues?labels=${WATCH_LABEL}&state=open&sort=created&direction=asc&per_page=10`)
    const issues = (await response.json()) as { number: number; body: string | null; pull_request?: unknown }[]
    const issue = issues.find((item) => item.pull_request === undefined)
    if (issue === undefined) return null
    const comments = (await (await github(`/issues/${issue.number}/comments?per_page=100`)).json()) as {
      body: string | null
    }[]
    return { number: issue.number, body: issue.body ?? '', comments: comments.map((comment) => comment.body ?? '') }
  },
  async createIssue(title, body) {
    const label = await github(`/labels/${WATCH_LABEL}`)
    if (label.status === 404) {
      await write('POST', '/labels', { name: WATCH_LABEL, color: 'b42318', description: '사이트 생존 감시(watch.yml)' })
    }
    await write('POST', '/issues', { title, body, labels: [WATCH_LABEL] })
  },
  async comment(issue, body) {
    await write('POST', `/issues/${issue}/comments`, { body })
  },
  async close(issue) {
    await write('PATCH', `/issues/${issue}`, { state: 'closed', state_reason: 'completed' })
  },
}

const action = await runWatch(api, now)
const line = action.type === 'none' ? `none — ${action.reason}` : `${action.type}`
console.log(`site watch (${siteUrl}): ${line}`)
if (action.type !== 'none') console.log(action.body)
const summaryFile = process.env.GITHUB_STEP_SUMMARY
if (summaryFile !== undefined) appendFileSync(summaryFile, `사이트 감시: ${line}\n`)
