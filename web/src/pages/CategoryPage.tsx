import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { CategoryChips } from '../components/categories/CategoryChips.tsx'
import { DetailPanel } from '../components/detail/DetailPanel.tsx'
import { SiteFooter } from '../components/footer/SiteFooter.tsx'
import { SiteHeader } from '../components/header/SiteHeader.tsx'
import { ErrorState } from '../components/state/ErrorState.tsx'
import { LoadingState } from '../components/state/LoadingState.tsx'
import { WatchRow } from '../components/watch/WatchRow.tsx'
import { useCategories, useMeta } from '../hooks/use-data.ts'
import { useIsDesktop } from '../hooks/use-media-query.ts'
import { AXIS_NAME, axisCategories, findCategory, type CategoryAxis } from '../lib/categories.ts'
import { formatCount } from '../lib/kpi.ts'
import { referenceDate } from '../lib/urgency.ts'
import type { Categories, Category } from '../types/categories.ts'
import type { Meta } from '../types/meta.ts'

const NOTE = 'm-0 text-[13px] text-ink-3'
const EMPTY = 'm-0 rounded-[10px] border border-dashed border-axis p-6 text-sm leading-relaxed text-ink-2'
const UNCLASSIFIED_NOTE = 'NVD 가 아직 분석하지 않은 새 CVE 는 분류 전입니다. 분류 전 건은 여기 세지 않습니다.'
const NEW_QUERY: Record<CategoryAxis, string> = { type: 'type', family: 'family' }

const CategoryBody = ({
  meta,
  axis,
  category,
  categories,
}: {
  meta: Meta
  axis: CategoryAxis
  category: Category
  categories: Categories
}) => {
  const [params, setParams] = useSearchParams()
  const isDesktop = useIsDesktop()
  const navigate = useNavigate()
  const today = referenceDate(meta.updated_at)
  const top = category.top
  const selectedId = isDesktop ? (params.get('cve') ?? top[0]?.id ?? null) : null

  const select = (cveId: string) => {
    if (isDesktop) {
      setParams({ cve: cveId }, { replace: true })
      return
    }
    navigate(`/cve/${cveId}`)
  }

  return (
    <>
      <title>{`${category.label} 취약점 — VulnTrend`}</title>
      <div className="flex flex-col gap-1">
        <p className="m-0 text-xs font-semibold text-ink-2">{AXIS_NAME[axis]}</p>
        <h1 className="m-0 text-2xl font-extrabold tracking-tight text-ink">{category.label}</h1>
        <p className="m-0 text-[13px] text-ink-2">
          {`최근 7일 ${formatCount(category.count_7d)}건 · 30일 ${formatCount(category.count_30d)}건 · KEV 누적 ${formatCount(category.kev_count)}건`}
        </p>
        <p className={NOTE}>{UNCLASSIFIED_NOTE}</p>
      </div>
      {/* 한 줄로 두어(높이 고정) 글꼴이 늦게 와도 아래가 밀리지 않게 한다 */}
      <div className="flex min-w-0">
        <CategoryChips
          axis={axis}
          list={axisCategories(categories, axis)}
          label={`다른 ${AXIS_NAME[axis]}`}
          currentKey={category.key}
          scroll
        />
      </div>
      {top.length === 0 ? (
        <p className={EMPTY}>이 분류에 든 취약점이 아직 없습니다.</p>
      ) : (
        <>
          <h2 className="m-0 text-[15px] font-bold text-ink">
            트렌드 점수 상위 {top.length}건
            <span className="ml-2 text-xs font-medium text-ink-2">최근 12개월 · 점수 순</span>
          </h2>
          <div className="grid items-start gap-5 lg:grid-cols-2">
            <div className="flex min-w-0 flex-col gap-2">
              <ul aria-label={`${category.label} 트렌드 점수 상위`} className="m-0 flex list-none flex-col gap-2 p-0">
                {top.map((row, index) => (
                  <li key={row.id}>
                    <WatchRow
                      row={row}
                      rank={index + 1}
                      today={today}
                      selected={row.id === selectedId}
                      onSelect={select}
                    />
                  </li>
                ))}
              </ul>
              {category.count_7d > 0 ? (
                <Link
                  to={`/new?${NEW_QUERY[axis]}=${encodeURIComponent(category.key)}`}
                  className="flex items-center justify-center rounded-[10px] border border-dashed border-axis bg-surface p-2.5 text-[13px] font-semibold text-ink hover:border-ink-3"
                >
                  {`최근 7일 ${category.label} ${formatCount(category.count_7d)}건 모두 보기 →`}
                </Link>
              ) : null}
            </div>
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

const Unknown = ({ axis, categories }: { axis: CategoryAxis; categories: Categories }) => (
  <>
    <title>없는 분류 — VulnTrend</title>
    <h1 className="m-0 text-2xl font-extrabold tracking-tight text-ink">없는 분류입니다</h1>
    <p className={NOTE}>아래 {AXIS_NAME[axis]} 중에서 골라 주세요.</p>
    <CategoryChips axis={axis} list={axisCategories(categories, axis)} label={AXIS_NAME[axis]} />
  </>
)

// meta 와 분류 파일이 다 온 뒤 한꺼번에 그린다 — 밀림 0 (v6 D7 규칙)
const CategoryContent = ({ axis }: { axis: CategoryAxis }) => {
  const { key = '' } = useParams()
  const meta = useMeta()
  const categories = useCategories()
  if (meta.status === 'error') return <ErrorState label="갱신 정보" onRetry={meta.retry} />
  if (categories.status === 'error') return <ErrorState label="분류" onRetry={categories.retry} />
  if (meta.status !== 'ready' || categories.status !== 'ready') return <LoadingState label="분류" />
  if (categories.data === null) {
    return (
      <>
        <h1 className="m-0 text-2xl font-extrabold tracking-tight text-ink">{AXIS_NAME[axis]}</h1>
        <p className={EMPTY}>분류는 다음 갱신부터 보입니다.</p>
      </>
    )
  }
  const category = findCategory(categories.data, axis, key)
  if (category === null) return <Unknown axis={axis} categories={categories.data} />
  return (
    <CategoryBody key={category.key} meta={meta.data} axis={axis} category={category} categories={categories.data} />
  )
}

// 분류 화면 /type/<key>(공격 유형), /family/<key>(제품군) (사용자 결정 2026-11-01)
const CategoryPage = ({ axis }: { axis: CategoryAxis }) => (
  <div className="mx-auto flex max-w-[1280px] flex-col gap-4 px-4 pt-5 pb-10 sm:px-7 sm:pt-6">
    <SiteHeader />
    {/* 불러오는 동안과 그린 뒤의 높이를 같게(화면 높이 이상) 두어 푸터·스크롤바가 움직이지 않게 한다 */}
    <main className="flex min-h-screen flex-col gap-3.5">
      <CategoryContent axis={axis} />
    </main>
    <SiteFooter />
  </div>
)

export default CategoryPage
