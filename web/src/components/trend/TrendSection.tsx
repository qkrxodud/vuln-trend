import { useState } from 'react'
import { useTrendIndex } from '../../hooks/use-data.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { TrendList } from './TrendList.tsx'

// index.json 을 읽어 목록을 그린다. 고른 CVE 는 5.7 상세 패널이 이어받는다
export const TrendSection = () => {
  const trendIndex = useTrendIndex()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  return (
    <LoadableView loadable={trendIndex} label="트렌드 목록">
      {(rows) => <TrendList rows={rows} selectedId={selectedId} onSelect={setSelectedId} />}
    </LoadableView>
  )
}
