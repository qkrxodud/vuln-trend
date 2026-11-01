import { scoreRanks } from '../lib/list-model.ts'
import { referenceDate } from '../lib/urgency.ts'
import { useMeta, useTrendIndex } from './use-data.ts'

/** 기준일(갱신일)과 주목 목록 순위. 아직 못 받았으면 순위 없이 보인다 */
export const useDetailContext = (cveId: string): { today: string | null; rank: number | null } => {
  const meta = useMeta()
  const index = useTrendIndex()
  return {
    today: meta.status === 'ready' ? referenceDate(meta.data.updated_at) : null,
    rank: index.status === 'ready' ? (scoreRanks(index.data).get(cveId) ?? null) : null,
  }
}
