import type { CSSProperties, ReactNode } from 'react'

type BadgeTone = 'kev' | 'ransomware' | 'neutral'

const TONE_CLASS: Record<BadgeTone, string> = {
  kev: 'bg-kev-bg text-kev',
  ransomware: 'border border-[#e3b7ab] bg-kev-bg text-kev',
  neutral: 'bg-chip font-normal text-ink-soft',
}

// 글자 배지. 색만으로 구분하지 않도록 항상 글자를 쓴다 (기획서 8장)
export const Badge = ({ tone, style, children }: { tone?: BadgeTone; style?: CSSProperties; children: ReactNode }) => (
  <span
    style={style}
    className={`rounded-full px-[9px] py-[3px] text-xs font-semibold whitespace-nowrap ${tone === undefined ? '' : TONE_CLASS[tone]}`}
  >
    {children}
  </span>
)
