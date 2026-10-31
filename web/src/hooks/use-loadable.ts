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
 */
export const useLoadable = <T>(key: string | null, load: () => Promise<T>): Loadable<T> => {
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
      (data) => settle({ ok: true, data }),
      (error: unknown) => settle({ ok: false, error: toError(error) }),
    )
    return () => {
      cancelled = true
    }
  }, [key, attempt, load])

  const retry = useCallback(() => setAttempt((previous) => previous + 1), [])

  if (key === null) return { status: 'idle' }
  if (settled === null) return { status: 'loading' }
  if (settled.key !== key || settled.attempt !== attempt) return { status: 'loading' }
  if (settled.outcome.ok) return { status: 'ready', data: settled.outcome.data }
  return { status: 'error', error: settled.outcome.error, retry }
}
