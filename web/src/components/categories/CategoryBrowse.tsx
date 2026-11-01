import { useCategories } from '../../hooks/use-data.ts'
import { CategoryChips } from './CategoryChips.tsx'

const AXIS_LABEL = 'w-[52px] shrink-0 pt-1.5 text-xs font-semibold text-ink-2'
// 칩 두 줄의 자리. 불러오는 중·없음·실패에도 같은 높이를 잡아 아래가 밀리지 않게 한다
const BODY = 'flex h-[84px] flex-col gap-1'
const NOTE = 'm-0 flex h-[84px] items-center text-[13px] text-ink-3'

const Body = () => {
  const categories = useCategories()
  if (categories.status === 'error') {
    return (
      <p className={NOTE}>
        분류를 불러오지 못했습니다.
        <button type="button" onClick={categories.retry} className="ml-2 font-semibold text-ink underline">
          다시 시도
        </button>
      </p>
    )
  }
  if (categories.status !== 'ready') {
    return (
      <p role="status" className={NOTE}>
        분류 불러오는 중…
      </p>
    )
  }
  if (categories.data === null) return <p className={NOTE}>분류는 다음 갱신부터 보입니다.</p>
  return (
    <div className={BODY}>
      <div className="flex min-w-0 gap-2">
        <span aria-hidden="true" className={AXIS_LABEL}>
          공격 유형
        </span>
        <CategoryChips axis="type" list={categories.data.attack_types} label="공격 유형" scroll />
      </div>
      <div className="flex min-w-0 gap-2">
        <span aria-hidden="true" className={AXIS_LABEL}>
          제품군
        </span>
        <CategoryChips axis="family" list={categories.data.product_families} label="제품군" scroll />
      </div>
    </div>
  )
}

// 메인 "분류로 보기" (사용자 결정 2026-11-01). categories.json 은 메인 첫 그리기를 기다리게 하지 않고 따로 받는다
export const CategoryBrowse = () => (
  <section
    aria-label="분류로 보기"
    className="flex min-w-0 flex-col gap-1.5 rounded-xl border border-border bg-surface px-5 pt-3.5 pb-2"
  >
    <h2 className="m-0 text-[15px] font-bold text-ink">
      분류로 보기
      <span className="ml-2 text-xs font-medium text-ink-2">
        숫자는 최근 7일 새 CVE · NVD 가 아직 분석하지 않은 새 CVE 는 분류 전입니다
      </span>
    </h2>
    <Body />
  </section>
)
