// 제품 순서 하나: 건수 → CISA 기한 임박 수 → 최고 점수 → 이름. 제품 막대(D4)와 목록 제품 칩(D5)이 같이 쓴다 (D3 지시)
export interface RankedProduct {
  name: string
  count: number
  urgent: number
  top: number
}

export const compareProducts = (a: RankedProduct, b: RankedProduct): number =>
  b.count - a.count || b.urgent - a.urgent || b.top - a.top || a.name.localeCompare(b.name, 'en')
