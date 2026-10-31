import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useCveDetail } from '../../hooks/use-data.ts'
import { affectedLines, DEFAULT_ACTION, fixedVersionLabel, nvdUrl, vendorLinks } from '../../lib/cve-detail.ts'
import { SEVERITY_BADGE } from '../../lib/severity.ts'
import { formatDueDate } from '../../lib/trend-list.ts'
import type { CveDetail } from '../../types/cve.ts'
import { LoadableView } from '../state/LoadableView.tsx'
import { Badge } from '../trend/Badge.tsx'
import { ScoreBreakdownList } from './ScoreBreakdownList.tsx'

const PANEL_LABEL = '선택한 취약점 상세'

const LinkTag = ({ children }: { children: ReactNode }) => (
  <span className="rounded bg-chip px-1.5 py-0.5 text-[11px] font-semibold text-ink-soft">{children}</span>
)

const NvdLink = ({ cveId }: { cveId: string }) => (
  <a href={nvdUrl(cveId)} target="_blank" rel="noopener noreferrer" className="flex items-center gap-2 text-accent">
    <LinkTag>NVD</LinkTag>NVD 원문 {cveId}
  </a>
)

const Fact = ({ label, children, tone }: { label: string; children: ReactNode; tone?: 'kev' }) => (
  <div className="flex flex-col gap-0.5">
    <dt className="text-xs text-ink-soft">{label}</dt>
    <dd className={`text-sm font-semibold tabular-nums ${tone === 'kev' ? 'font-bold text-kev' : ''}`}>{children}</dd>
  </div>
)

const SummaryBlock = ({ detail }: { detail: CveDetail }) => {
  if (detail.summary === null) {
    return (
      <div className="flex flex-col gap-2">
        <h4 className="text-sm font-semibold">
          원문 설명 <span className="text-[11px] font-medium text-ink-faint">한국어 요약은 상위 20건에만 제공</span>
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

const DetailBody = ({ detail }: { detail: CveDetail }) => {
  const severity = SEVERITY_BADGE[detail.severity]
  return (
    <>
      <div className="flex flex-col gap-2">
        <div className="flex flex-wrap items-center gap-2">
          <span className="font-mono text-lg font-semibold">{detail.id}</span>
          {detail.is_kev ? <Badge tone="kev">KEV</Badge> : null}
          {detail.kev_ransomware ? <Badge tone="ransomware">랜섬웨어</Badge> : null}
          <Badge style={{ background: severity.background, color: severity.color }}>{severity.label}</Badge>
          {detail.primary_product === null ? null : <Badge tone="neutral">{detail.primary_product}</Badge>}
        </div>
        <h3 className="text-base font-semibold">{detail.title}</h3>
      </div>

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
            <a
              key={link.url}
              href={link.url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-2 text-accent"
            >
              <LinkTag>벤더</LinkTag>
              {link.label}
            </a>
          ))}
          <NvdLink cveId={detail.id} />
        </div>
      </div>
    </>
  )
}

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
