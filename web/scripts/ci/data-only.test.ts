import { describe, expect, it } from 'vitest'
import { isDataOnlyChange } from './data-only.ts'

describe('Pages 배포의 시험 건너뛰기 (데이터만 바뀐 push)', () => {
  it('배치가 web/public/data 만 바꾼 push 는 데이터만 바뀐 것이다', () => {
    expect(isDataOnlyChange(['web/public/data/meta.json', 'web/public/data/cve/CVE-2026-1.json'])).toBe(true)
  })

  it('코드가 하나라도 섞이면 시험을 돌린다', () => {
    expect(isDataOnlyChange(['web/public/data/meta.json', 'web/src/App.tsx'])).toBe(false)
    expect(isDataOnlyChange(['.github/workflows/pages.yml'])).toBe(false)
  })

  it('이름만 비슷한 다른 폴더는 데이터가 아니다', () => {
    expect(isDataOnlyChange(['web/public/data-old/x.json'])).toBe(false)
    expect(isDataOnlyChange(['web/public/datasets.json'])).toBe(false)
  })

  it('사이트 밖(배치·문서) 변경은 사이트 시험과 상관없어 데이터와 함께 바뀌어도 건너뛴다', () => {
    expect(isDataOnlyChange(['web/public/data/meta.json', 'vuln-trend-wbs.md', 'batch/src/main/Foo.java'])).toBe(true)
  })

  it('워크플로 변경은 사이트 밖이어도 시험을 돌린다', () => {
    expect(isDataOnlyChange(['web/public/data/meta.json', '.github/workflows/watch.yml'])).toBe(false)
  })

  it('바뀐 파일을 알 수 없으면(빈 목록) 시험을 돌린다', () => {
    expect(isDataOnlyChange([])).toBe(false)
    expect(isDataOnlyChange(['', '  '])).toBe(false)
  })
})
