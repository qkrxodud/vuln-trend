// WCAG 2 상대 휘도와 대비. 색 토큰이 본문 글자 기준(4.5:1)을 넘는지 테스트에서 잰다
const channel = (value: number): number => {
  const srgb = value / 255
  return srgb <= 0.03928 ? srgb / 12.92 : ((srgb + 0.055) / 1.055) ** 2.4
}

const luminance = (hex: string): number => {
  const [r, g, b] = [1, 3, 5].map((start) => channel(Number.parseInt(hex.slice(start, start + 2), 16)))
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

export const contrastRatio = (foreground: string, background: string): number => {
  const [lighter, darker] = [luminance(foreground), luminance(background)].sort((a, b) => b - a)
  return (lighter + 0.05) / (darker + 0.05)
}
