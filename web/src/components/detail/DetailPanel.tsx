import { Link } from 'react-router'
import { useCveDetail } from '../../hooks/use-data.ts'
import { DEFAULT_ACTION, SUMMARY_MISSING, vendorLinks } from '../../lib/cve-detail.ts'
import type { CveDetail } from '../../types/cve.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { AffectedFacts } from './AffectedFacts.tsx'
import { CveBadges } from './CveBadges.tsx'
import { ExternalLink, NvdLink } from './links.tsx'
import { ScoreBreakdownList } from './ScoreBreakdownList.tsx'

const PANEL_LABEL = '선택한 취약점 상세'

const SummaryBlock = ({ detail }: { detail: CveDetail }) => {
  if (detail.summary === null) {
    return (
      <div className="flex flex-col gap-2">
        <h4 className="text-sm font-semibold">
          원문 설명 <span className="text-[11px] font-medium text-ink-faint">{SUMMARY_MISSING}</span>
        </h4>
        <p className="text-sm leading-relaxed text-ink-soft">{detail.description}</p>
      </div>
    )
  }
  return (
    <div className="flex flex-col gap-2.5">
      <div className="flex items-center gap-2">
        <h4 className="text-sm font-semibold">요약</h4>
        <span className="rounded-full bg-chip px-2 py-0.5 text-[11px] text-ink-faint">AI 생성 · 원문 확인 필요</span>
      </div>
      <dl className="grid grid-cols-[72px_minmax(0,1fr)] gap-x-2.5 gap-y-1.5 text-sm leading-relaxed">
        <dt className="text-ink-faint">무엇이</dt>
        <dd>{detail.summary.what}</dd>
        <dt className="text-ink-faint">누가</dt>
        <dd>{detail.summary.who}</dd>
        <dt className="text-ink-faint">조치</dt>
        <dd className="font-semibold">{detail.summary.action}</dd>
      </dl>
    </div>
  )
}

const DetailBody = ({ detail }: { detail: CveDetail }) => (
  <>
    <div className="flex flex-col gap-2">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-mono text-lg font-semibold">{detail.id}</span>
        <CveBadges row={detail} />
      </div>
      <h3 className="text-base font-semibold">{detail.title}</h3>
    </div>

    <AffectedFacts detail={detail} />

    <SummaryBlock detail={detail} />

    <div className="flex flex-col gap-2.5">
      <div className="flex items-baseline justify-between">
        <h4 className="text-sm font-semibold">점수 구성</h4>
        <span className="text-[13px] text-ink-muted">
          트렌드 점수 <b className="text-base text-ink">{detail.score}</b>
        </span>
      </div>
      <ScoreBreakdownList breakdown={detail.score_breakdown} />
      <p className="rounded-[10px] bg-paper px-3 py-2.5 text-[13px] text-ink-soft">
        <b>왜 지금?</b> <span>{detail.why_now}</span>
      </p>
    </div>

    <div className="flex flex-col gap-2.5">
      <h4 className="text-sm font-semibold">조치</h4>
      {/* AI 요약이 있으면 조치 문장은 요약(면책 아래)에만 둔다. 같은 문장을 두 번 보이지 않게 */}
      {detail.summary === null ? <p className="text-sm leading-relaxed">{DEFAULT_ACTION}</p> : null}
      <div className="flex flex-col gap-1.5 text-[13px]">
        {vendorLinks(detail.references).map((link) => (
          <ExternalLink key={link.url} href={link.url} tag="벤더">
            {link.label}
          </ExternalLink>
        ))}
        <NvdLink cveId={detail.id} />
      </div>
    </div>
  </>
)

// 데스크톱(≥1024px) 목록 옆 sticky 패널. 고른 CVE 의 cve/*.json 을 그때 받는다 (WBS 5.7, D8)
export const DetailPanel = ({ cveId }: { cveId: string }) => {
  const detail = useCveDetail(cveId)
  return (
    <aside
      aria-label={PANEL_LABEL}
      className="sticky top-4 flex flex-col gap-5 rounded-[18px] border border-line bg-white p-[22px] shadow-[0_6px_24px_rgba(20,20,30,.05)]"
    >
      <LoadableView loadable={detail} label="취약점 상세">
        {(data) =>
          data === null ? (
            <div className="flex flex-col gap-3 text-sm">
              <span className="font-mono text-lg font-semibold">{cveId}</span>
              <p>이 취약점은 상세 정보를 따로 만들지 않았습니다.</p>
              <NvdLink cveId={cveId} />
            </div>
          ) : (
            <DetailBody detail={data} />
          )
        }
      </LoadableView>
      <Link
        to={`/cve/${cveId}`}
        className="flex items-center justify-center gap-1.5 rounded-[10px] bg-accent-soft p-[11px] text-sm font-semibold text-accent hover:bg-[#e6efea]"
      >
        상세 페이지 열기 →
      </Link>
      <p aria-live="polite" className="sr-only">
        {detail.status === 'ready' ? `${cveId} 상세 표시 중` : ''}
      </p>
    </aside>
  )
}
