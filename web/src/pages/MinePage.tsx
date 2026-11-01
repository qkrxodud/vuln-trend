import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { DetailPanel } from '../components/detail/DetailPanel.tsx'
import { SiteFooter } from '../components/footer/SiteFooter.tsx'
import { SiteHeader } from '../components/header/SiteHeader.tsx'
import { ErrorState } from '../components/state/ErrorState.tsx'
import { LoadingState } from '../components/state/LoadingState.tsx'
import { WatchRow } from '../components/watch/WatchRow.tsx'
import { useMeta, useRecent, useSearchIndex, useTrendIndex } from '../hooks/use-data.ts'
import { useIsDesktop } from '../hooks/use-media-query.ts'
import { useMine } from '../hooks/use-mine.ts'
import { nvdUrl } from '../lib/cve-detail.ts'
import { formatCount } from '../lib/kpi.ts'
import { mineRows, olderEntries, productCandidates, type OlderEntry } from '../lib/mine.ts'
import { NEW_PAGE, sortNew, type NewSort } from '../lib/new-cves.ts'
import { referenceDate } from '../lib/urgency.ts'
import type { Meta } from '../types/meta.ts'
import type { SearchIndex } from '../types/search.ts'
import type { TrendRow } from '../types/trend.ts'

const NOTE = 'm-0 text-[13px] text-ink-3'
const EMPTY = 'm-0 rounded-[10px] border border-dashed border-axis p-6 text-sm leading-relaxed text-ink-2'
const MORE =
  'self-stretch rounded-[10px] border border-dashed border-axis bg-surface p-2.5 text-[13px] font-semibold text-ink hover:border-ink-3'

// 제품 찾기: 검색 색인의 제품 이름 중에서 고른다(대소문자는 맞춰 준다)
const Picker = ({ search }: { search: SearchIndex }) => {
  const { mine, toggle } = useMine()
  const [text, setText] = useState('')
  const [message, setMessage] = useState<string | null>(null)
  const candidates = productCandidates(search)
  const add = () => {
    const wanted = text.trim().toLowerCase()
    if (wanted === '') return
    const found = candidates.find((candidate) => candidate.name.toLowerCase() === wanted)
    if (found === undefined) {
      setMessage('검색 색인에 없는 제품 이름입니다. 목록에서 골라 주세요.')
      return
    }
    if (mine.products.includes(found.name) === false) toggle(found.name)
    setText('')
    setMessage(null)
  }
  return (
    <div className="flex flex-col gap-2">
      <form
        className="flex flex-wrap items-center gap-2"
        onSubmit={(event) => {
          event.preventDefault()
          add()
        }}
      >
        <label className="inline-flex items-center gap-2 text-[13px] text-ink-2">
          제품 찾기
          <input
            type="search"
            list="mine-products"
            value={text}
            onChange={(event) => setText(event.target.value)}
            placeholder="예: GitLab"
            className="w-56 rounded-full border border-axis bg-surface px-3 py-1.5 text-[13px] text-ink"
          />
        </label>
        <datalist id="mine-products">
          {candidates.slice(0, 200).map((candidate) => (
            <option key={candidate.name} value={candidate.name}>
              {`${candidate.count}건`}
            </option>
          ))}
        </datalist>
        <button type="submit" className="rounded-full bg-ink px-3.5 py-1.5 text-[13px] font-semibold text-white">
          더하기
        </button>
      </form>
      {message === null ? null : <p className="m-0 text-xs text-ink-2">{message}</p>}
      {mine.products.length === 0 ? null : (
        <ul aria-label="고른 제품" className="m-0 flex list-none flex-wrap gap-1.5 p-0">
          {mine.products.map((product) => (
            <li
              key={product}
              className="inline-flex items-center gap-1 rounded-full border border-ink bg-surface py-0.5 pr-1 pl-2.5 text-[13px] font-semibold text-ink"
            >
              <span aria-hidden="true">★</span> {product}
              <button
                type="button"
                aria-label={`${product} 빼기`}
                onClick={() => toggle(product)}
                className="rounded-full px-1.5 text-ink-3 hover:text-ink"
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

const Rows = ({
  rows,
  today,
  selectedId,
  onSelect,
}: {
  rows: TrendRow[]
  today: string
  selectedId: string | null
  onSelect: (id: string) => void
}) => {
  const [shown, setShown] = useState(NEW_PAGE)
  const visible = rows.slice(0, shown)
  return (
    <div className="flex min-w-0 flex-col gap-2">
      <ul aria-label="내 제품 취약점" className="m-0 flex list-none flex-col gap-2 p-0">
        {visible.map((row) => (
          <li key={row.id}>
            <WatchRow row={row} rank={null} today={today} selected={row.id === selectedId} onSelect={onSelect} />
          </li>
        ))}
      </ul>
      {rows.length > visible.length ? (
        <button type="button" className={MORE} onClick={() => setShown((count) => count + NEW_PAGE)}>
          {Math.min(NEW_PAGE, rows.length - visible.length)}건 더 보기 ({visible.length}/{formatCount(rows.length)})
        </button>
      ) : null}
    </div>
  )
}

const Older = ({ entries }: { entries: OlderEntry[] }) => {
  const [shown, setShown] = useState(NEW_PAGE)
  if (entries.length === 0) return null
  const visible = entries.slice(0, shown)
  return (
    <section aria-labelledby="mine-older" className="flex flex-col gap-2">
      <h2 id="mine-older" className="m-0 text-[15px] font-bold text-ink">
        지난 12개월 {formatCount(entries.length)}건
        <span className="ml-2 text-xs font-medium text-ink-2">점수·상세 없음 · NVD 원문으로</span>
      </h2>
      <ul aria-label="지난 12개월 내 제품 CVE (점수·상세 없음)" className="m-0 flex list-none flex-col p-0">
        {visible.map((entry) => (
          <li key={entry.id} className="flex min-w-0 items-baseline gap-2 border-b border-border py-1.5 text-[13px]">
            <a
              href={nvdUrl(entry.id)}
              target="_blank"
              rel="noopener noreferrer"
              className="shrink-0 font-mono font-semibold text-ink underline-offset-2 hover:underline"
            >
              {entry.id}
              <span className="sr-only"> (새 창)</span> ↗
            </a>
            <span className="min-w-0 truncate text-ink-2" title={entry.title}>
              {entry.title}
            </span>
          </li>
        ))}
      </ul>
      {entries.length > visible.length ? (
        <button type="button" className={MORE} onClick={() => setShown((count) => count + NEW_PAGE)}>
          {Math.min(NEW_PAGE, entries.length - visible.length)}건 더 보기
        </button>
      ) : null}
    </section>
  )
}

const MineBody = ({
  meta,
  index,
  recent,
  search,
}: {
  meta: Meta
  index: TrendRow[]
  recent: TrendRow[]
  search: SearchIndex
}) => {
  const { mine, markSeen } = useMine()
  const [sort, setSort] = useState<NewSort>('score')
  const [params, setParams] = useSearchParams()
  const isDesktop = useIsDesktop()
  const navigate = useNavigate()
  const today = referenceDate(meta.updated_at)
  const rows = sortNew(mineRows([index, recent], mine.products), sort)
  const older = olderEntries(search, mine.products, new Set(rows.map((row) => row.id)))
  const selectedId = isDesktop ? (params.get('cve') ?? rows[0]?.id ?? null) : null

  // 이 화면을 본 때를 남겨, 다음 방문의 "새 n건"은 그 뒤 공개분만 센다
  useEffect(() => markSeen(meta.updated_at), [markSeen, meta.updated_at])

  const select = (cveId: string) => {
    if (isDesktop) {
      setParams({ cve: cveId }, { replace: true })
      return
    }
    navigate(`/cve/${cveId}`)
  }

  return (
    <>
      <div>
        <h1 className="m-0 text-2xl font-extrabold tracking-tight text-ink">내 제품</h1>
        <p className={NOTE}>고른 제품은 이 브라우저에만 저장됩니다.</p>
      </div>
      <Picker search={search} />
      {mine.products.length === 0 ? (
        <p className={EMPTY}>
          쓰는 제품을 고르면 그 제품의 취약점만 모아 봅니다. 위에서 제품을 찾아 더하거나, 목록·상세의 ☆ 내 제품을
          누르세요. 주목 목록과 최근 7일 새 CVE 에서 점수·조치 정보와 함께, 그 밖의 지난 12개월 건은 NVD 원문 링크로
          보입니다.
        </p>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-2 text-[13px] text-ink-2">
            <span>
              점수·조치 정보가 있는 <b className="text-ink">{formatCount(rows.length)}건</b> (주목 목록 + 최근 7일)
            </span>
            <div
              role="group"
              aria-label="정렬"
              className="inline-flex gap-0.5 rounded-full border border-axis bg-surface p-0.5"
            >
              {(['score', 'latest'] as const).map((key) => (
                <button
                  key={key}
                  type="button"
                  aria-pressed={sort === key}
                  onClick={() => setSort(key)}
                  className="rounded-full px-3 py-1 text-[13px] text-ink-2 aria-pressed:bg-ink aria-pressed:font-semibold aria-pressed:text-white"
                >
                  {key === 'latest' ? '최신순' : '점수순'}
                </button>
              ))}
            </div>
          </div>
          <div className="grid items-start gap-5 lg:grid-cols-2">
            {rows.length === 0 ? (
              <p className={EMPTY}>주목 목록과 최근 7일에는 고른 제품의 취약점이 없습니다.</p>
            ) : (
              <Rows key={sort} rows={rows} today={today} selectedId={selectedId} onSelect={select} />
            )}
            {isDesktop && selectedId !== null ? (
              <div className="sticky top-4">
                <DetailPanel cveId={selectedId} />
              </div>
            ) : null}
          </div>
          <Older entries={older} />
        </>
      )}
    </>
  )
}

// 필요한 데이터(meta·index·recent·검색 색인)가 다 온 뒤 한꺼번에 그린다 — 밀림 0 (v6 D7 규칙)
const MineContent = () => {
  const meta = useMeta()
  const index = useTrendIndex()
  const recent = useRecent()
  const search = useSearchIndex(true)
  const failed = [meta, index, recent, search].find((loadable) => loadable.status === 'error')
  if (failed !== undefined && failed.status === 'error')
    return <ErrorState label="내 제품 데이터" onRetry={failed.retry} />
  if (meta.status !== 'ready' || index.status !== 'ready' || recent.status !== 'ready' || search.status !== 'ready') {
    return <LoadingState label="내 제품" />
  }
  return <MineBody meta={meta.data} index={index.data} recent={recent.data} search={search.data} />
}

// "내 제품" 화면 /mine (사용자 결정 2026-11-01). 검색 색인·최근 7일은 이 화면에서 받는다
const MinePage = () => (
  <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-4 pt-5 pb-10 sm:px-7 sm:pt-6">
    <title>내 제품 — VulnTrend</title>
    <SiteHeader />
    {/* 불러오는 동안과 그린 뒤의 높이를 같게(화면 높이 이상) 두어 푸터·스크롤바가 움직이지 않게 한다 */}
    <main className="flex min-h-screen flex-col gap-3.5">
      <MineContent />
    </main>
    <SiteFooter />
  </div>
)

export default MinePage
