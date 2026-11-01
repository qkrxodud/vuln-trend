import { useDeferredValue, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import { SiteFooter } from '../components/footer/SiteFooter.tsx'
import { SiteHeader } from '../components/header/SiteHeader.tsx'
import { DetailPanel } from '../components/detail/DetailPanel.tsx'
import { LoadingState } from '../components/state/LoadingState.tsx'
import { ErrorState } from '../components/state/ErrorState.tsx'
import { WatchRow } from '../components/watch/WatchRow.tsx'
import { useCategories, useMeta, useRecent } from '../hooks/use-data.ts'
import { useIsDesktop } from '../hooks/use-media-query.ts'
import { axisCategories, countByCategory, UNCLASSIFIED, type CategoryAxis } from '../lib/categories.ts'
import { formatCount } from '../lib/kpi.ts'
import { formatKstDateTime } from '../lib/kst-time.ts'
import {
  ALL_NEW,
  dateOptions,
  filterNew,
  isFiltered,
  NEW_PAGE,
  productSuggestions,
  sortNew,
  type NewFilter,
  type NewSort,
} from '../lib/new-cves.ts'
import { SEVERITY_BADGE } from '../lib/severity.ts'
import { referenceDate } from '../lib/urgency.ts'
import type { Categories } from '../types/categories.ts'
import type { Severity } from '../types/common.ts'
import type { Meta } from '../types/meta.ts'
import type { TrendRow } from '../types/trend.ts'

const SEVERITIES: Severity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'UNRATED']
const SUGGESTIONS = 30
const EMPTY = 'm-0 rounded-[10px] border border-dashed border-axis p-6 text-center text-sm leading-relaxed text-ink-2'
const TOGGLE =
  'rounded-full border border-axis bg-surface px-3 py-1.5 text-[13px] text-ink-2 aria-pressed:border-ink aria-pressed:bg-ink aria-pressed:font-semibold aria-pressed:text-white'
const CLEAR = 'rounded-full border border-axis bg-surface px-2.5 py-0.5 text-xs font-semibold text-ink'

const SELECT = 'max-w-[11rem] rounded-full border border-axis bg-surface px-2.5 py-1 text-[13px] text-ink'

// 공격 유형·제품군 고르기. 이름은 categories.json 의 label, 건수는 최근 7일 안에서 센다
const CategorySelect = ({
  axis,
  label,
  rows,
  categories,
  value,
  onChange,
}: {
  axis: CategoryAxis
  label: string
  rows: TrendRow[]
  categories: Categories
  value: string | null
  onChange: (value: string | null) => void
}) => {
  const counts = countByCategory(rows, axis)
  return (
    <label className="inline-flex items-center gap-1.5">
      {label}
      <select
        value={value ?? ''}
        onChange={(event) => onChange(event.target.value === '' ? null : event.target.value)}
        className={SELECT}
      >
        <option value="">모두</option>
        {axisCategories(categories, axis).map((category) => (
          <option key={category.key} value={category.key}>
            {`${category.label} (${formatCount(counts.get(category.key) ?? 0)})`}
          </option>
        ))}
        <option value={UNCLASSIFIED}>{`분류 전 (${formatCount(counts.get(UNCLASSIFIED) ?? 0)})`}</option>
      </select>
    </label>
  )
}

interface FiltersProps {
  rows: TrendRow[]
  categories: Categories | null
  filter: NewFilter
  sort: NewSort
  onFilter: (filter: NewFilter) => void
  onSort: (sort: NewSort) => void
}

const Filters = ({ rows, categories, filter, sort, onFilter, onSort }: FiltersProps) => {
  const dates = dateOptions(rows)
  const toggleSeverity = (severity: Severity) =>
    onFilter({
      ...filter,
      severities: filter.severities.includes(severity)
        ? filter.severities.filter((item) => item !== severity)
        : [...filter.severities, severity],
    })
  return (
    <div className="flex flex-col gap-2.5 border-b border-border pb-3">
      <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="심각도로 거르기">
        {SEVERITIES.map((severity) => {
          const count = rows.filter((row) => row.severity === severity).length
          return count === 0 ? null : (
            <button
              key={severity}
              type="button"
              aria-pressed={filter.severities.includes(severity)}
              onClick={() => toggleSeverity(severity)}
              className={TOGGLE}
            >
              {SEVERITY_BADGE[severity].label} {formatCount(count)}
            </button>
          )
        })}
      </div>
      <div className="flex flex-wrap items-center gap-x-4 gap-y-2 text-[13px] text-ink-2">
        <label className="inline-flex items-center gap-1.5">
          <input
            type="checkbox"
            checked={filter.kevOnly}
            onChange={(event) => onFilter({ ...filter, kevOnly: event.target.checked })}
          />
          KEV 만
        </label>
        <label className="inline-flex items-center gap-1.5">
          <input
            type="checkbox"
            checked={filter.fixOnly}
            onChange={(event) => onFilter({ ...filter, fixOnly: event.target.checked })}
          />
          조치 정보 있음
        </label>
        <label className="inline-flex items-center gap-1.5">
          제품
          <input
            type="search"
            list="new-cve-products"
            value={filter.product}
            onChange={(event) => onFilter({ ...filter, product: event.target.value })}
            placeholder="이름 일부"
            className="w-40 rounded-full border border-axis bg-surface px-3 py-1 text-[13px] text-ink"
          />
          <datalist id="new-cve-products">
            {productSuggestions(rows, SUGGESTIONS).map((name) => (
              <option key={name} value={name} />
            ))}
          </datalist>
        </label>
        {categories === null ? null : (
          <>
            <CategorySelect
              axis="type"
              label="공격 유형"
              rows={rows}
              categories={categories}
              value={filter.attackType}
              onChange={(attackType) => onFilter({ ...filter, attackType })}
            />
            <CategorySelect
              axis="family"
              label="제품군"
              rows={rows}
              categories={categories}
              value={filter.family}
              onChange={(family) => onFilter({ ...filter, family })}
            />
          </>
        )}
        {dates.length === 0 ? null : (
          <label className="inline-flex items-center gap-1.5">
            공개일
            <select
              value={filter.date ?? ''}
              onChange={(event) => onFilter({ ...filter, date: event.target.value === '' ? null : event.target.value })}
              className="rounded-full border border-axis bg-surface px-2.5 py-1 text-[13px] text-ink"
            >
              <option value="">모든 날</option>
              {dates.map((option) => (
                <option key={option.date} value={option.date}>
                  {option.date.slice(5)} ({formatCount(option.count)})
                </option>
              ))}
            </select>
          </label>
        )}
        <div
          role="group"
          aria-label="정렬"
          className="ml-auto inline-flex gap-0.5 rounded-full border border-axis bg-surface p-0.5"
        >
          {(['latest', 'score'] as const).map((key) => (
            <button
              key={key}
              type="button"
              aria-pressed={sort === key}
              onClick={() => onSort(key)}
              className="rounded-full px-3 py-1 text-[13px] text-ink-2 aria-pressed:bg-ink aria-pressed:font-semibold aria-pressed:text-white"
            >
              {key === 'latest' ? '최신순' : '점수순'}
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}

const NewList = ({
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
      <ul aria-label="최근 7일 새 CVE 목록" className="m-0 flex list-none flex-col gap-2 p-0">
        {visible.map((row) => (
          <li key={row.id}>
            <WatchRow row={row} rank={null} today={today} selected={row.id === selectedId} onSelect={onSelect} />
          </li>
        ))}
      </ul>
      {rows.length > visible.length ? (
        <button
          type="button"
          onClick={() => setShown((count) => count + NEW_PAGE)}
          className="self-stretch rounded-[10px] border border-dashed border-axis bg-surface p-2.5 text-[13px] font-semibold text-ink hover:border-ink-3"
        >
          {Math.min(NEW_PAGE, rows.length - visible.length)}건 더 보기{' '}
          <span className="text-xs font-normal text-ink-3 tabular-nums">
            ({visible.length}/{formatCount(rows.length)})
          </span>
        </button>
      ) : null}
    </div>
  )
}

// 분류 화면에서 /new?type=키·?family=키 로 들어오면 그 분류로 거른 채 시작한다
const CATEGORY_PARAMS = { attackType: 'type', family: 'family' } as const

const NewBody = ({ meta, rows, categories }: { meta: Meta; rows: TrendRow[]; categories: Categories | null }) => {
  const [params, setParams] = useSearchParams()
  const [filter, setFilterState] = useState<NewFilter>(() =>
    categories === null
      ? ALL_NEW
      : { ...ALL_NEW, attackType: params.get(CATEGORY_PARAMS.attackType), family: params.get(CATEGORY_PARAMS.family) },
  )
  const [sort, setSort] = useState<NewSort>('latest')
  const deferredFilter = useDeferredValue(filter)
  const isDesktop = useIsDesktop()
  const navigate = useNavigate()
  const today = referenceDate(meta.updated_at)
  const matched = sortNew(filterNew(rows, deferredFilter), sort)
  const selectedId = isDesktop ? (params.get('cve') ?? matched[0]?.id ?? null) : null

  // 분류 필터는 주소에도 남겨 링크로 나눌 수 있게 한다
  const setFilter = (next: NewFilter) => {
    setFilterState(next)
    setParams(
      (current) => {
        const updated = new URLSearchParams(current)
        ;(['attackType', 'family'] as const).forEach((field) => {
          const value = next[field]
          if (value === null) updated.delete(CATEGORY_PARAMS[field])
          else updated.set(CATEGORY_PARAMS[field], value)
        })
        return updated
      },
      { replace: true },
    )
  }

  // 데스크톱은 주소에 ?cve= 로 남겨 옆 패널에, 좁은 화면은 상세 페이지로 (지금 규칙). 분류 필터는 그대로 둔다
  const select = (cveId: string) => {
    if (isDesktop) {
      setParams(
        (current) => {
          const updated = new URLSearchParams(current)
          updated.set('cve', cveId)
          return updated
        },
        { replace: true },
      )
      return
    }
    navigate(`/cve/${cveId}`)
  }

  return (
    <>
      <div>
        <h1 className="m-0 text-2xl font-extrabold tracking-tight text-ink">최근 7일 새 CVE</h1>
        <p className="mt-1 mb-0 text-[13px] text-ink-2">
          {formatCount(rows.length)}건 · 최근 7개 한국 달력일 공개 · 기준 {formatKstDateTime(meta.updated_at)}
        </p>
        <p className="mt-1 mb-0 text-[13px] text-ink-3">
          한국어 요약은 점수 상위 건에만 있습니다. 나머지는 NVD 영문 제목으로 보입니다.
          {categories === null ? null : ' NVD 가 아직 분석하지 않은 새 CVE 는 분류 전입니다.'}
        </p>
      </div>
      {rows.length === 0 ? (
        <p className={EMPTY}>
          최근 7일 동안 새로 공개된 CVE 가 없습니다.
          <br />
          2시간마다 다시 확인합니다.
        </p>
      ) : (
        <>
          <Filters
            rows={rows}
            categories={categories}
            filter={filter}
            sort={sort}
            onFilter={setFilter}
            onSort={setSort}
          />
          <div
            role="status"
            aria-label="목록 범위"
            className="flex min-h-7 flex-wrap items-center gap-2 text-[13px] text-ink-2"
          >
            {isFiltered(deferredFilter) ? (
              <>
                <span>
                  {formatCount(rows.length)}건 중 <b className="text-ink">{formatCount(matched.length)}건</b>
                </span>
                <button type="button" onClick={() => setFilter(ALL_NEW)} className={CLEAR}>
                  필터 해제 ✕
                </button>
              </>
            ) : (
              <span>
                모두 <b className="text-ink">{formatCount(rows.length)}건</b> ·{' '}
                {sort === 'latest' ? '최신순' : '점수순'}
              </span>
            )}
          </div>
          <div className="grid items-start gap-5 lg:grid-cols-2">
            {matched.length === 0 ? (
              <div className={EMPTY}>
                <p className="m-0">조건에 맞는 CVE 가 없습니다.</p>
                <button type="button" onClick={() => setFilter(ALL_NEW)} className={`mt-2 ${CLEAR}`}>
                  필터 해제
                </button>
              </div>
            ) : (
              <NewList
                key={`${sort}|${JSON.stringify(deferredFilter)}`}
                rows={matched}
                today={today}
                selectedId={selectedId}
                onSelect={select}
              />
            )}
            {isDesktop && selectedId !== null ? (
              <div className="sticky top-4">
                <DetailPanel cveId={selectedId} />
              </div>
            ) : null}
          </div>
        </>
      )}
    </>
  )
}

// 최근 7일 새 CVE 가 다 온 뒤 한꺼번에 그린다. 기다리는 동안 화면 높이 자리를 잡아 아래가 밀리지 않게 (v6 D7 규칙)
const NewContent = () => {
  const meta = useMeta()
  const recent = useRecent()
  const categories = useCategories()
  if (meta.status === 'error') return <ErrorState label="갱신 정보" onRetry={meta.retry} />
  if (recent.status === 'error') return <ErrorState label="최근 7일 새 CVE" onRetry={recent.retry} />
  // 분류 파일은 못 받아도 목록은 보인다(분류 필터만 뺀다)
  const categoriesReady = categories.status === 'ready' || categories.status === 'error'
  if (meta.status !== 'ready' || recent.status !== 'ready' || categoriesReady === false) {
    return (
      <div className="min-h-screen">
        <LoadingState label="최근 7일 새 CVE" />
      </div>
    )
  }
  return (
    <NewBody meta={meta.data} rows={recent.data} categories={categories.status === 'ready' ? categories.data : null} />
  )
}

// "최근 7일 새 CVE" 화면 /new (사용자 결정 2026-11-01). 이 화면에 들어올 때만 recent.json·categories.json 을 받는다
const NewCvesPage = () => (
  <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-4 pt-5 pb-10 sm:px-7 sm:pt-6">
    <title>최근 7일 새 CVE — VulnTrend</title>
    <SiteHeader />
    <main className="flex flex-col gap-3.5">
      <NewContent />
    </main>
    <SiteFooter />
  </div>
)

export default NewCvesPage
