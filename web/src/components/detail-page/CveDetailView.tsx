import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { useCveDetail } from '../../hooks/use-data.ts'
import { nvdUrl } from '../../lib/cve-detail.ts'
import { groupReferences } from '../../lib/references.ts'
import { SEVERITY_BADGE } from '../../lib/severity.ts'
import { formatEpss, formatTopPercent } from '../../lib/trend-list.ts'
import type { CveDetail } from '../../types/cve.ts'
import { AffectedFacts } from '../detail/AffectedFacts.tsx'
import { CveBadges } from '../detail/CveBadges.tsx'
import { ExternalLink, NvdLink } from '../detail/links.tsx'
import { ScoreBreakdownList } from '../detail/ScoreBreakdownList.tsx'
import { LoadableView } from '../state/LoadableView.tsx'

const Section = ({ id, title, children }: { id: string; title: string; children: ReactNode }) => (
  <section aria-labelledby={id} className="flex flex-col gap-3">
    <h2 id={id} className="text-lg font-semibold">
      {title}
    </h2>
    {children}
  </section>
)

const ScoreTile = ({ label, value, sub }: { label: string; value: string; sub: string | null }) => (
  <li className="flex flex-col gap-1 rounded-2xl bg-paper px-5 py-4">
    <span className="text-sm text-ink-soft">{label}</span>
    <span className="text-[34px] leading-tight font-bold tracking-tight tabular-nums">{value}</span>
    {sub === null ? null : <span className="text-xs text-ink-faint">{sub}</span>}
  </li>
)

// 점수 3종: 트렌드 / EPSS(상위 N%) / CVSS(버전·심각도)
const ScoreTiles = ({ detail }: { detail: CveDetail }) => (
  <ul aria-label="점수" className="grid grid-cols-[repeat(auto-fit,minmax(min(100%,180px),1fr))] gap-3">
    <ScoreTile label="트렌드 점수" value={String(detail.score)} sub={null} />
    <ScoreTile label="EPSS" value={formatEpss(detail.epss)} sub={formatTopPercent(detail.epss_percentile)} />
    <ScoreTile
      label={detail.cvss_version === null ? 'CVSS' : `CVSS ${detail.cvss_version}`}
      value={detail.cvss === null ? '미평가' : detail.cvss.toFixed(1)}
      sub={detail.cvss === null ? null : SEVERITY_BADGE[detail.severity].label}
    />
  </ul>
)

const SummarySection = ({ detail }: { detail: CveDetail }) => (
  <Section id="cve-summary" title="한국어 요약">
    {detail.summary === null ? (
      <p className="text-sm text-ink-soft">
        한국어 요약은 트렌드 상위 20건에만 제공합니다. 아래 원문 설명을 확인하세요.
      </p>
    ) : (
      <>
        <dl className="grid grid-cols-[72px_minmax(0,1fr)] gap-x-3 gap-y-2 text-[15px] leading-relaxed">
          <dt className="text-ink-faint">무엇이</dt>
          <dd>{detail.summary.what}</dd>
          <dt className="text-ink-faint">누가</dt>
          <dd>{detail.summary.who}</dd>
          <dt className="text-ink-faint">조치</dt>
          <dd className="font-semibold">{detail.summary.action}</dd>
        </dl>
        <p className="rounded-[10px] bg-paper px-3 py-2.5 text-xs text-ink-muted">
          AI 가 생성한 요약입니다. 정확하지 않을 수 있으니 원문을 확인하세요.{' '}
          <a href={nvdUrl(detail.id)} target="_blank" rel="noopener noreferrer" className="text-accent underline">
            NVD 원문 보기
          </a>
        </p>
      </>
    )}
  </Section>
)

const DetailContent = ({ detail }: { detail: CveDetail }) => (
  <article className="flex flex-col gap-8">
    <title>{`${detail.id} — VulnTrend`}</title>
    <header className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center gap-2">
        <h1 className="font-mono text-2xl font-semibold">{detail.id}</h1>
        <CveBadges row={detail} />
      </div>
      <p className="text-xl font-semibold">{detail.title}</p>
      <ScoreTiles detail={detail} />
      <AffectedFacts detail={detail} />
    </header>

    <SummarySection detail={detail} />

    <Section id="cve-why-now" title="왜 지금?">
      <p className="text-[15px]">{detail.why_now}</p>
    </Section>

    <Section id="cve-score" title="점수 구성">
      <ScoreBreakdownList breakdown={detail.score_breakdown} />
    </Section>

    <Section id="cve-description" title="원문 설명">
      <p lang="en" className="text-[15px] leading-relaxed text-ink-soft">
        {detail.description}
      </p>
    </Section>

    <Section id="cve-references" title="참고 링크">
      <div className="flex flex-col gap-2 text-sm">
        {groupReferences(detail.id, detail.references).flatMap((group) =>
          group.links.map((link) => (
            <ExternalLink key={link.url} href={link.url} tag={group.label}>
              {link.title}
            </ExternalLink>
          )),
        )}
      </div>
    </Section>

    {/* 같은 제품의 최근 취약점 5건: 2단계(P21). 그때 이 자리에 붙인다 */}
  </article>
)

// 상세 파일을 만들지 않은 CVE (P6)
const NoDetail = ({ cveId }: { cveId: string }) => (
  <article className="flex flex-col gap-4">
    <title>{`${cveId} — VulnTrend`}</title>
    <h1 className="font-mono text-2xl font-semibold">{cveId}</h1>
    <p className="text-[15px] text-ink-soft">
      이 취약점은 상세 정보를 따로 만들지 않았습니다. 상세 페이지는 트렌드 상위·최근 7일 목록에 오른 취약점만 만듭니다.
    </p>
    <NvdLink cveId={cveId} />
  </article>
)

// /cve/:cveId 상세 페이지 본문 (WBS 5.8, 기획서 8장 상세 페이지)
export const CveDetailView = ({ cveId }: { cveId: string }) => {
  const detail = useCveDetail(cveId)
  return (
    <div className="flex flex-col gap-6">
      <Link to="/" className="self-start text-sm text-accent">
        ← 메인으로
      </Link>
      <LoadableView loadable={detail} label="취약점 상세">
        {(data) => (data === null ? <NoDetail cveId={cveId} /> : <DetailContent detail={data} />)}
      </LoadableView>
    </div>
  )
}
