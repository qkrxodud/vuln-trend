import { describe, expect, it } from 'vitest'
import { withObjectParticle } from './korean.ts'

describe('안내 문구의 목적격 조사', () => {
  it('받침으로 끝나는 말에는 "을"을 붙인다', () => {
    expect(withObjectParticle('트렌드 목록')).toBe('트렌드 목록을')
  })

  it('받침 없이 끝나는 말에는 "를"을 붙인다', () => {
    expect(withObjectParticle('통계')).toBe('통계를')
  })

  it('한글이 아닌 글자로 끝나면 "을(를)"로 둘 다 적는다', () => {
    expect(withObjectParticle('CVE')).toBe('CVE을(를)')
  })
})
