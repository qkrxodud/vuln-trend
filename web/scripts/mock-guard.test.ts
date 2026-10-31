import { describe, expect, it } from 'vitest'
import { mockOutputPlan, refuseOverwrite } from './mock-guard.ts'

const DEFAULT_DIR = '/repo/web/public/data'

describe('목업 생성기는 실제 데이터를 덮어쓰지 않는다', () => {
  it('아무 인자도 없으면 기본 폴더에 덮어쓰기 없이 쓰려 한다', () => {
    expect(mockOutputPlan([], DEFAULT_DIR)).toEqual({ outDir: DEFAULT_DIR, force: false })
  })

  it('--out 으로 다른 폴더를, --force 로 덮어쓰기를 고를 수 있다', () => {
    expect(mockOutputPlan(['--out', '/tmp/mock', '--force'], DEFAULT_DIR)).toEqual({ outDir: '/tmp/mock', force: true })
  })

  it('쓸 폴더에 이미 meta.json 이 있으면 --force 없이는 멈추고 이유를 알린다', () => {
    const reason = refuseOverwrite({ outDir: DEFAULT_DIR, force: false }, true)

    expect(reason).toContain('meta.json')
    expect(reason).toContain('--force')
  })

  it('--force 를 주거나 폴더가 비어 있으면 쓴다', () => {
    expect(refuseOverwrite({ outDir: DEFAULT_DIR, force: true }, true)).toBeNull()
    expect(refuseOverwrite({ outDir: DEFAULT_DIR, force: false }, false)).toBeNull()
  })
})
