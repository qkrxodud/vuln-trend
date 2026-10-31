// 시각은 방문자 시간대와 상관없이 한국 시각(KST)으로 고정해 보인다 (기획서 8장, D5)
const KST_PARTS = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Seoul',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
})

const MINUTE_MS = 60_000
const HOUR_MS = 60 * MINUTE_MS
const STALE_AFTER_MS = 36 * HOUR_MS
const DAYS_FROM_HOURS = 48

/** `10-30 09:00` */
export const formatKstShort = (iso: string): string => {
  const parts = Object.fromEntries(KST_PARTS.formatToParts(new Date(iso)).map((part) => [part.type, part.value]))
  return `${parts.month}-${parts.day} ${parts.hour}:${parts.minute}`
}

/** `방금 전` / `30분 전` / `3시간 전` / `3일 전`. 초 단위는 보이지 않는다 */
export const formatTimeAgo = (iso: string, now: Date): string => {
  const elapsed = now.getTime() - Date.parse(iso)
  if (elapsed < MINUTE_MS) return '방금 전'
  if (elapsed < HOUR_MS) return `${Math.floor(elapsed / MINUTE_MS)}분 전`

  const hours = Math.floor(elapsed / HOUR_MS)
  if (hours < DAYS_FROM_HOURS) return `${hours}시간 전`
  return `${Math.floor(hours / 24)}일 전`
}

/** 갱신 뒤 36시간을 넘으면 오래된 데이터 (기획서 6장 실패 규칙) */
export const isStale = (iso: string, now: Date): boolean => now.getTime() - Date.parse(iso) > STALE_AFTER_MS
