import { describe, expect, it } from 'vitest'
import {
  CRITICAL_AFTER_HOURS,
  type DeployRun,
  formatAge,
  judgeHealth,
  latestSettledRun,
  WARN_AFTER_HOURS,
} from './site-health.ts'

const HOUR = 60 * 60 * 1000
const MINUTE = 60 * 1000
// 확인 시각 2026-11-02 12:00 KST (러너는 UTC 로 돈다)
const NOW = new Date('2026-11-02T03:00:00Z')
const fresh = { ok: true as const, updatedAt: '2026-11-02T11:01:00+09:00' }
const updatedHoursAgo = (ms: number) => ({ ok: true as const, updatedAt: new Date(NOW.getTime() - ms).toISOString() })

const run = (overrides: Partial<DeployRun> = {}): DeployRun => ({
  id: 41375496806,
  status: 'completed',
  conclusion: 'success',
  url: 'https://github.com/qkrxodud/vuln-trend/actions/runs/41375496806',
  sha: '6f27a198eae2b5c3a0df133e9177ee1791c8b751',
  createdAt: '2026-11-02T02:01:45Z',
  title: 'data: 2026-11-02 11:01 KST',
  ...overrides,
})

describe('데이터 나이 (공개 사이트 meta.json 의 마지막 갱신)', () => {
  it('경고는 6시간, 위험은 26시간을 넘었을 때다', () => {
    expect(WARN_AFTER_HOURS).toBe(6)
    expect(CRITICAL_AFTER_HOURS).toBe(26)
  })

  it('2시간마다 갱신되면 문제가 없다', () => {
    const report = judgeHealth(fresh, [run()], NOW)

    expect(report.level).toBe('ok')
    expect(report.problems).toEqual([])
  })

  it('딱 6시간은 괜찮고, 1분이라도 넘으면 경고다', () => {
    expect(judgeHealth(updatedHoursAgo(6 * HOUR), [run()], NOW).level).toBe('ok')
    expect(judgeHealth(updatedHoursAgo(6 * HOUR + MINUTE), [run()], NOW).level).toBe('warn')
  })

  it('딱 26시간은 경고, 넘으면 위험이다', () => {
    expect(judgeHealth(updatedHoursAgo(26 * HOUR), [run()], NOW).level).toBe('warn')
    expect(judgeHealth(updatedHoursAgo(26 * HOUR + MINUTE), [run()], NOW).problems).toEqual([
      expect.objectContaining({ kind: 'very-stale', level: 'critical' }),
    ])
  })

  it('갱신이 멈추면 마지막 갱신 시각을 한국 시각으로, 나이를 시간·분으로 알린다', () => {
    const report = judgeHealth({ ok: true, updatedAt: '2026-11-02T04:48:00+09:00' }, [run()], NOW)

    expect(report.problems).toEqual([
      {
        kind: 'stale',
        level: 'warn',
        text: '데이터 갱신이 7시간 12분째 멈춤 (마지막 갱신 2026-11-02 04:48 KST, 경고 기준 6시간)',
      },
    ])
  })

  it('한국 시각 날짜가 바뀌는 자정 무렵에도 나이는 실제 경과 시간으로 잰다', () => {
    const midnight = new Date('2026-11-02T15:30:00Z') // 11-03 00:30 KST
    const report = judgeHealth({ ok: true, updatedAt: '2026-11-02T23:01:00+09:00' }, [run()], midnight)

    expect(report.level).toBe('ok')
  })

  it('갱신 시각이 확인 시각보다 뒤(시계 차이)여도 문제로 보지 않는다', () => {
    expect(judgeHealth(updatedHoursAgo(-5 * MINUTE), [run()], NOW).level).toBe('ok')
  })

  it('meta.json 을 읽지 못하면 위험이다', () => {
    const report = judgeHealth({ ok: false, reason: 'HTTP 404' }, [run()], NOW)

    expect(report.problems).toEqual([
      { kind: 'meta-unreadable', level: 'critical', text: '공개 사이트 meta.json 을 읽지 못함 (HTTP 404)' },
    ])
  })

  it('갱신 시각을 읽을 수 없는 값이면 읽지 못한 것으로 본다', () => {
    const report = judgeHealth({ ok: true, updatedAt: 'not-a-date' }, [run()], NOW)

    expect(report.problems.map((problem) => problem.kind)).toEqual(['meta-unreadable'])
  })
})

describe('Pages 배포 결과 (pages.yml 최근 실행)', () => {
  it('마지막으로 끝난 배포가 실패면 경고하고, 그 실행 주소와 커밋을 댄다', () => {
    const report = judgeHealth(fresh, [run({ conclusion: 'failure' }), run({ id: 1 })], NOW)

    expect(report.problems).toEqual([
      {
        kind: 'deploy-failed',
        level: 'warn',
        text: 'Pages 배포 실패 (failure): data: 2026-11-02 11:01 KST · 6f27a19 · https://github.com/qkrxodud/vuln-trend/actions/runs/41375496806',
      },
    ])
  })

  it('실패 뒤 다음 배포가 성공했으면 회복된 것이다', () => {
    const runs = [run({ id: 2 }), run({ conclusion: 'failure' })]

    expect(judgeHealth(fresh, runs, NOW).level).toBe('ok')
  })

  it('진행 중이거나 다음 push 에 밀려 취소된 실행은 건너뛰고 그 전 결과를 본다', () => {
    const runs = [
      run({ id: 3, status: 'in_progress', conclusion: null }),
      run({ id: 2, conclusion: 'cancelled' }),
      run({ id: 1, conclusion: 'failure' }),
    ]

    expect(latestSettledRun(runs)?.id).toBe(1)
  })

  it('배포 기록이 없으면 배포는 따지지 않는다', () => {
    expect(judgeHealth(fresh, [], NOW).level).toBe('ok')
  })

  it('시간 초과·시작 실패도 실패로 본다', () => {
    expect(judgeHealth(fresh, [run({ conclusion: 'timed_out' })], NOW).level).toBe('warn')
    expect(judgeHealth(fresh, [run({ conclusion: 'startup_failure' })], NOW).level).toBe('warn')
  })
})

describe('판정 묶음', () => {
  it('문제가 여럿이면 가장 나쁜 수준을 전체 수준으로 하고, 문제 종류로 같은 상황인지 가린다', () => {
    const report = judgeHealth(updatedHoursAgo(30 * HOUR), [run({ conclusion: 'failure' })], NOW)

    expect(report.level).toBe('critical')
    expect(report.key).toBe('critical:deploy-failed,very-stale')
    expect(report.summary).toBe('위험 — 데이터 갱신 30시간 0분째 멈춤 외 1건')
  })

  it('문제가 없으면 정상 요약을 낸다', () => {
    const report = judgeHealth(fresh, [run()], NOW)

    expect(report.key).toBe('ok')
    expect(report.summary).toBe('정상 — 마지막 갱신 2026-11-02 11:01 KST (59분 전)')
  })

  it('나이는 분 단위로 버린다', () => {
    expect(formatAge(59 * MINUTE + 59_000)).toBe('59분')
    expect(formatAge(7 * HOUR + 12 * MINUTE)).toBe('7시간 12분')
  })
})
