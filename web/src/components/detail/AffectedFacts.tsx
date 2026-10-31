import { Fragment, useState, type ReactNode } from 'react'
import { AFFECTED_PREVIEW, affectedItems, fixedVersionLabel, type AffectedItem } from '../../lib/cve-detail.ts'
import { formatDueDate } from '../../lib/trend-list.ts'
import type { CveDetail } from '../../types/cve.ts'

const Fact = ({ label, children, tone }: { label: string; children: ReactNode; tone?: 'kev' }) => (
  <div className="flex flex-col gap-0.5">
    <dt className="text-xs text-ink-soft">{label}</dt>
    <dd className={`text-sm font-semibold tabular-nums ${tone === 'kev' ? 'font-bold text-kev' : ''}`}>{children}</dd>
  </div>
)

// 범위 조각은 통째로 다음 줄로 넘긴다("||" 사이에서 줄바꿈). 조각·슬러그가 한 줄보다 길면 아무 데서나 끊는다
const AffectedLine = ({ item }: { item: AffectedItem }) => (
  <li className="[overflow-wrap:anywhere]">
    {item.name}{' '}
    {item.ranges.map((range, index) => (
      <Fragment key={range}>
        {index === 0 ? null : ' || '}
        <span className="inline-block max-w-full">{range}</span>
      </Fragment>
    ))}
  </li>
)

// 실데이터는 한 CVE 에 영향 제품이 수십 개다. 처음 5개만 보이고 나머지는 펼친다
const AffectedList = ({ items }: { items: AffectedItem[] }) => {
  const [expanded, setExpanded] = useState(false)
  if (items.length === 0) return '정보 없음'
  const hidden = items.length - AFFECTED_PREVIEW
  const shown = expanded ? items : items.slice(0, AFFECTED_PREVIEW)
  return (
    <>
      <ul aria-label="영향 제품" className="flex flex-col gap-0.5">
        {shown.map((item, index) => (
          <AffectedLine key={index} item={item} />
        ))}
      </ul>
      {hidden > 0 ? (
        <button
          type="button"
          aria-expanded={expanded}
          onClick={() => setExpanded(expanded === false)}
          className="mt-1.5 text-[13px] font-semibold text-accent hover:underline"
        >
          {expanded ? '접기' : `${hidden}개 더 보기`}
        </button>
      ) : null}
    </>
  )
}

// 수정 버전 · KEV 패치 기한을 윗줄에, 영향 버전(제품 목록)은 아랫줄 전체 폭에 둔다
// (MVP 는 NVD CPE 범위, 수정 버전은 OSV 수집 후)
export const AffectedFacts = ({ detail }: { detail: CveDetail }) => (
  <dl className="grid grid-cols-2 gap-2.5 rounded-xl bg-paper px-4 py-3.5">
    <Fact label="수정 버전">{fixedVersionLabel(detail.affected)}</Fact>
    {detail.is_kev && detail.kev_due_date !== null ? (
      <Fact label="KEV 패치 기한" tone="kev">
        {formatDueDate(detail.kev_due_date)}
      </Fact>
    ) : null}
    <div className="col-span-2">
      <Fact label="영향 버전">
        <AffectedList items={affectedItems(detail.affected)} />
      </Fact>
    </div>
  </dl>
)
