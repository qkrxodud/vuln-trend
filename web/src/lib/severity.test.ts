import { describe, expect, it } from 'vitest'
import type { Severity } from '../types/common.ts'
import { severityLabel } from './severity.ts'

describe('심각도 글자', () => {
  it('등급마다 글자를 주고, 미평가는 "미평가"다', () => {
    expect(severityLabel('CRITICAL')).toBe('Critical')
    expect(severityLabel('UNRATED')).toBe('미평가')
  })

  it('계약과 달리 값이 비어 오거나 모르는 값이어도 화면이 멈추지 않게 "미평가"로 적는다', () => {
    expect(severityLabel(null as unknown as Severity)).toBe('미평가')
    expect(severityLabel('UNKNOWN' as Severity)).toBe('미평가')
  })
})
