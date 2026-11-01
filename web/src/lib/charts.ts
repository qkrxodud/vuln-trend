// 차트 공통 표기 (기획서 8장 차트 표, S1)

/** `2026-10-30` → `10/30`, `2026-11-03` → `11/3` */
export const formatMonthDay = (isoDate: string): string => {
  const [, month, day] = isoDate.split('-')
  return `${Number(month)}/${Number(day)}`
}
