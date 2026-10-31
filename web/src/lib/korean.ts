// 한국어 조사 고르기. 안내 문구에 데이터 이름을 끼워 넣을 때 쓴다
const HANGUL_FIRST = 0xac00
const HANGUL_LAST = 0xd7a3
const FINAL_CONSONANT_COUNT = 28

/** 마지막 글자 받침 유무로 "을"/"를"을 붙인다. 한글이 아니면 "을(를)" */
export const withObjectParticle = (word: string): string => {
  const code = word.charCodeAt(word.length - 1)
  if (code < HANGUL_FIRST || code > HANGUL_LAST) return `${word}을(를)`

  const hasFinalConsonant = (code - HANGUL_FIRST) % FINAL_CONSONANT_COUNT !== 0
  return `${word}${hasFinalConsonant ? '을' : '를'}`
}
