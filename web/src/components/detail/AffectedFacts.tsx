import type { ReactNode } from 'react'
import { affectedLines, fixedVersionLabel } from '../../lib/cve-detail.ts'
import { formatDueDate } from '../../lib/trend-list.ts'
import type { CveDetail } from '../../types/cve.ts'

const Fact = ({ label, children, tone }: { label: string; children: ReactNode; tone?: 'kev' }) => (
  <div className="flex flex-col gap-0.5">
    <dt className="text-xs text-ink-soft">{label}</dt>
    <dd className={`text-sm font-semibold tabular-nums ${tone === 'kev' ? 'font-bold text-kev' : ''}`}>{children}</dd>
  </div>
)

// 영향 버전 · 수정 버전 · KEV 패치 기한 (MVP 는 NVD CPE 범위, 수정 버전은 OSV 수집 후)
export const AffectedFacts = ({ detail }: { detail: CveDetail }) => (
  <dl className="grid grid-cols-[repeat(auto-fit,minmax(130px,1fr))] gap-2.5 rounded-xl bg-paper px-4 py-3.5">
    <Fact label="영향 버전">
      {detail.affected.length === 0
        ? '정보 없음'
        : affectedLines(detail.affected).map((line) => (
            <span key={line} className="block">
              {line}
            </span>
          ))}
    </Fact>
    <Fact label="수정 버전">{fixedVersionLabel(detail.affected)}</Fact>
    {detail.is_kev && detail.kev_due_date !== null ? (
      <Fact label="KEV 패치 기한" tone="kev">
        {formatDueDate(detail.kev_due_date)}
      </Fact>
    ) : null}
  </dl>
)
