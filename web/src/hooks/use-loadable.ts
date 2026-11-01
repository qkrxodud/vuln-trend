import { useCallback, useEffect, useState } from 'react'
import type { Loadable } from './loadable.ts'

interface Settled<T> {
  key: string
  attempt: number
  outcome: { ok: true; data: T } | { ok: false; error: Error }
}

const toError = (value: unknown): Error => (value instanceof Error ? value : new Error(String(value)))

/**
 * key 가 바뀔 때마다 load 를 한 번 부른다. key 가 `null` 이면 요청하지 않는다(idle).
 * load 는 key 가 같으면 같은 결과를 내야 한다. 호출부는 useCallback 으로 고정한다.
 * resolved 를 주면 받은 값을 거기 남기고, 같은 key 로 나중에 붙는 훅은 처음 그릴 때부터 ready 다
 * (한 틱 "불러오는 중"으로 접혔다 펴지며 아래가 밀리는 일을 막는다, v6 D7·피드백 묶음)
 */
export const useLoadable = <T>(
  key: string | null,
  load: () => Promise<T>,
  resolved?: Map<string, unknown>,
): Loadable<T> => {
  const [attempt, setAttempt] = useState(0)
  const [settled, setSettled] = useState<Settled<T> | null>(null)

  useEffect(() => {
    if (key === null) return
    let cancelled = false
    const settle = (outcome: Settled<T>['outcome']) => {
      if (cancelled) return
      setSettled({ key, attempt, outcome })
    }
    load().then(
      (data) => {
        resolved?.set(key, data)
        settle({ ok: true, data })
      },
      (error: unknown) => settle({ ok: false, error: toError(error) }),
    )
    return () => {
      cancelled = true
    }
  }, [key, attempt, load, resolved])

  const retry = useCallback(() => setAttempt((previous) => previous + 1), [])

  if (key === null) return { status: 'idle' }
  const current = settled !== null && settled.key === key && settled.attempt === attempt
  if (current === false && attempt === 0 && resolved?.has(key) === true) {
    return { status: 'ready', data: resolved.get(key) as T }
  }
  if (settled === null) return { status: 'loading' }
  if (settled.key !== key || settled.attempt !== attempt) return { status: 'loading' }
  if (settled.outcome.ok) return { status: 'ready', data: settled.outcome.data }
  return { status: 'error', error: settled.outcome.error, retry }
}
