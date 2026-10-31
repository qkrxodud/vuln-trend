// KPI 카드 숫자 표기 (기획서 8장 KPI 표, D11·D12)
const COUNT_FORMAT = new Intl.NumberFormat('ko-KR')

export const formatCount = (value: number): string => COUNT_FORMAT.format(value)

/** `+9` / `−2` / `0`. 빼기는 하이픈이 아니라 수학 기호(U+2212)로 적는다 */
export const formatDelta = (today: number, yesterday: number): string => {
  const delta = today - yesterday
  if (delta > 0) return `+${formatCount(delta)}`
  if (delta < 0) return `−${formatCount(-delta)}`
  return '0'
}

/** 화면 읽기 프로그램용 증감 문장 */
export const describeDelta = (today: number, yesterday: number): string => {
  const delta = today - yesterday
  const before = `어제 ${formatCount(yesterday)}건`
  if (delta > 0) return `${before}보다 ${formatCount(delta)}건 늘었습니다`
  if (delta < 0) return `${before}보다 ${formatCount(-delta)}건 줄었습니다`
  return `${before}과 같습니다`
}
