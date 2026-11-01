import { useState, type ReactNode } from 'react'
import {
  addedFact,
  affectedName,
  DESCRIPTION_CLAMP,
  dueFact,
  formatScorePart,
  referenceRows,
  scoreAxis,
  scoreRows,
  scoreSegment,
} from '../../lib/detail-model.ts'
import { AFFECTED_PREVIEW } from '../../lib/cve-detail.ts'
import { formatVersions, patchLinks, requiredAction, updateTargets } from '../../lib/fix-actions.ts'
import { formatKstDate } from '../../lib/kst-time.ts'
import { formatEpssPercent, formatEpssRank, isNewKev } from '../../lib/list-model.ts'
import { severityLabel } from '../../lib/severity.ts'
import { kevAddedDaysAgo, productName } from '../../lib/urgency.ts'
import type { CveDetail } from '../../types/cve.ts'
import { KevBadge, NewKevBadge, PocBadge, RansomBadge } from '../signal/SignalBadges.tsx'
import { Diamond } from '../signal/UrgentBadge.tsx'

export interface DetailBodyProps {
  detail: CveDetail
  today: string
  /** 주목 목록 점수 순위. 목록 밖이면 `null` */
  rank: number | null
  /** panel: 제목 h3·소제목 h4 / page: 소제목 h2 (h1 은 페이지의 CVE-ID) */
  level: 'panel' | 'page'
}

const NOTE = 'm-0 rounded-lg bg-surface-sunk px-3 py-2.5 text-[13px] leading-[1.55] text-ink-2'
const SOURCE = 'text-xs text-ink-3'
const LINK_BUTTON = 'self-start text-xs font-semibold text-ink underline underline-offset-2'

const Section = ({
  id,
  title,
  aside,
  level,
  children,
}: {
  id: string
  title: string
  aside?: string
  level: DetailBodyProps['level']
  children: ReactNode
}) => {
  const Heading = level === 'panel' ? 'h4' : 'h2'
  return (
    <section aria-labelledby={id} className="flex flex-col gap-2">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <Heading id={id} className="m-0 text-sm font-bold text-ink">
          {title}
        </Heading>
        {aside === undefined ? null : <span className="text-xs font-medium text-ink-2">{aside}</span>}
      </div>
      {children}
    </section>
  )
}

const Fact = ({ term, children, tone }: { term: string; children: ReactNode; tone?: 'urgent' | 'none' }) => (
  <div>
    <dt className="text-xs text-ink-2">{term}</dt>
    <dd
      className={`mt-0.5 flex flex-wrap items-center gap-1 [overflow-wrap:anywhere] ${
        tone === 'none' ? 'text-[13px] font-medium text-ink-2' : 'text-[15px] font-bold'
      } ${tone === 'urgent' ? 'text-urgent-ink' : tone === 'none' ? '' : 'text-ink'}`}
    >
      {children}
    </dd>
  </div>
)

const Small = ({ children }: { children: ReactNode }) => (
  <small className="text-xs font-medium text-ink-2">{children}</small>
)

const DueCell = ({ detail, today }: { detail: CveDetail; today: string }) => {
  const due = dueFact(detail, today)
  if (due.kind === 'not-kev')
    return (
      <Fact term="CISA 조치 기한" tone="none">
        없음 (KEV 아님)
      </Fact>
    )
  if (due.kind === 'overdue') {
    return (
      <Fact term="CISA 조치 기한">
        {due.days}일 지남 <Small>{due.date}</Small>
      </Fact>
    )
  }
  return (
    <Fact term="CISA 조치 기한" tone={due.kind === 'urgent' ? 'urgent' : undefined}>
      {due.kind === 'urgent' ? <Diamond /> : null}D-{due.days} <Small>{due.date}</Small>
    </Fact>
  )
}

const AddedCell = ({ detail, today }: { detail: CveDetail; today: string }) => {
  const added = addedFact(detail, today)
  if (added.kind === 'not-kev')
    return (
      <Fact term="CISA KEV 등재" tone="none">
        KEV 아님
      </Fact>
    )
  if (added.kind === 'unknown')
    return (
      <Fact term="CISA KEV 등재" tone="none">
        등재일 정보 없음
      </Fact>
    )
  return (
    <Fact term="CISA KEV 등재">
      {added.daysAgo}일 전 <Small>{added.date}</Small>
    </Fact>
  )
}

const Facts = ({ detail, today }: { detail: CveDetail; today: string }) => (
  <dl className="m-0 grid grid-cols-2 gap-x-3.5 gap-y-3 rounded-[10px] bg-surface-sunk px-3.5 py-3">
    <DueCell detail={detail} today={today} />
    <AddedCell detail={detail} today={today} />
    {detail.epss === null ? (
      <Fact term="악용 가능성 (EPSS)" tone="none">
        EPSS 점수 없음
      </Fact>
    ) : (
      <Fact term="악용 가능성 (EPSS)">
        {formatEpssPercent(detail.epss)}{' '}
        {detail.epss_percentile === null ? null : <Small>{formatEpssRank(detail.epss_percentile)}</Small>}
      </Fact>
    )}
    <Fact term="공개일">{formatKstDate(detail.published_at)}</Fact>
  </dl>
)

// 요약은 summary(AI) → summary_what → 영문 원문 순 (CONSENSUS 4절 상세). 원문은 길면 접는다
const Problem = ({ detail, level }: { detail: CveDetail; level: DetailBodyProps['level'] }) => {
  const [open, setOpen] = useState(false)
  const { summary } = detail
  const long = detail.description.length > DESCRIPTION_CLAMP
  const hasKorean = summary !== null || detail.summary_what !== null
  return (
    <Section id={`${level}-problem`} title="무엇이 문제인가" level={level}>
      {summary !== null ? (
        <>
          <p className="m-0 text-sm leading-relaxed text-ink-2">
            <b className="text-ink">무엇</b> {summary.what}
          </p>
          <p className="m-0 text-sm leading-relaxed text-ink-2">
            <b className="text-ink">영향</b> {summary.who}
          </p>
          <p className={SOURCE}>
            AI 요약 · {summary.model} · {formatKstDate(summary.generated_at)} — 원문과 다를 수 있습니다
          </p>
        </>
      ) : detail.summary_what !== null ? (
        <>
          <p className="m-0 text-sm leading-relaxed text-ink-2">{detail.summary_what}</p>
          <p className={SOURCE}>AI 요약</p>
        </>
      ) : (
        <p className={SOURCE}>한국어 요약이 아직 없습니다 · NVD 원문(영문)</p>
      )}
      <p
        id={`${level}-description`}
        lang="en"
        className={`m-0 text-[13px] leading-relaxed text-ink-2 [overflow-wrap:anywhere] ${long && open === false ? 'line-clamp-4' : ''}`}
      >
        {hasKorean ? <b className="text-ink">원문 </b> : null}
        {detail.description}
      </p>
      {long ? (
        <button
          type="button"
          aria-expanded={open}
          aria-controls={`${level}-description`}
          onClick={() => setOpen(open === false)}
          className={LINK_BUTTON}
        >
          {open ? '원문 접기' : '원문 전체 보기'}
        </button>
      ) : null}
    </Section>
  )
}

// 점수 구성: 항목마다 0 기준 발산 막대 한 줄 (진짜 <table>, CHART-REVIEW D1·D2)
const ScoreTable = ({ detail }: { detail: CveDetail }) => {
  const axis = scoreAxis(detail.score_breakdown.decay)
  const track = (value: number, tone: 'plus' | 'net') => {
    const segment = scoreSegment(value, axis)
    const color =
      value < 0
        ? 'rounded-l bg-[repeating-linear-gradient(135deg,var(--color-decay)_0_3px,var(--color-surface)_3px_5px)] shadow-[inset_0_0_0_1px_var(--color-decay)]'
        : tone === 'net'
          ? 'rounded-r bg-ink'
          : 'rounded-r bg-sev-high'
    return (
      <div aria-hidden="true" className="relative mx-1.5 h-3">
        <span className="absolute -top-1.5 -bottom-1.5 w-px bg-ink" style={{ left: `${axis.x(0)}%` }} />
        {segment === null ? null : (
          <span
            className={`absolute inset-y-0 ${color}`}
            style={{ left: `${segment.left}%`, width: `${segment.width}%` }}
          />
        )}
      </div>
    )
  }
  return (
    <table className="w-full table-fixed border-collapse text-[13px]">
      <caption className="sr-only">점수 항목별 기여 — 0 기준, 더하는 항목은 오른쪽, 감쇠는 왼쪽</caption>
      <thead>
        <tr className="border-b border-border text-xs font-semibold text-ink-2">
          <th scope="col" className="w-[34%] py-1.5 pr-2 text-left font-semibold">
            항목
          </th>
          <th scope="col" className="py-1.5 text-left font-semibold">
            <div aria-hidden="true" className="relative mx-1.5 h-4 text-xs font-normal text-ink-3 tabular-nums">
              {axis.ticks.map((tick, index) => (
                <span
                  key={tick}
                  className={`absolute top-0.5 whitespace-nowrap ${
                    tick < 0 ? '' : index === axis.ticks.length - 1 ? '-translate-x-full' : '-translate-x-1/2'
                  }`}
                  style={{ left: `${axis.x(tick)}%` }}
                >
                  {tick < 0 ? `−${-tick}` : tick}
                </span>
              ))}
            </div>
            <span className="sr-only">막대</span>
          </th>
          <th scope="col" className="w-[52px] py-1.5 text-right font-semibold">
            점수
          </th>
        </tr>
      </thead>
      <tbody>
        {scoreRows(detail.score_breakdown).map((row) => (
          <tr key={row.key} data-zero={row.value === 0} className="border-b border-border data-[zero=true]:text-ink-3">
            <th scope="row" className="py-1.5 pr-2 text-left font-medium [overflow-wrap:anywhere]">
              {row.label}
            </th>
            <td>{track(row.value, 'plus')}</td>
            <td className={`text-right tabular-nums ${row.value === 0 ? 'font-medium' : 'font-bold'}`}>
              {formatScorePart(row.value)}
            </td>
          </tr>
        ))}
        <tr className="border-t-[1.5px] border-ink font-extrabold">
          <th scope="row" className="py-1.5 pr-2 text-left">
            = 점수
          </th>
          <td>{track(Math.max(0, detail.score), 'net')}</td>
          <td className="text-right tabular-nums">{detail.score}</td>
        </tr>
      </tbody>
    </table>
  )
}

// 영향 범위: 5개 + "그 외 n개 더 보기". 수정 버전은 값이 있을 때만 (사용자 결정 Q6)
const Affected = ({ detail, level }: { detail: CveDetail; level: DetailBodyProps['level'] }) => {
  const [open, setOpen] = useState(false)
  const items = detail.affected
  const shown = open ? items : items.slice(0, AFFECTED_PREVIEW)
  return (
    <Section
      id={`${level}-affected`}
      title="영향 범위"
      aside={items.length === 0 ? undefined : `${items.length}개 제품`}
      level={level}
    >
      {items.length === 0 ? (
        <p className={NOTE}>영향 제품 정보가 아직 없습니다. 아래 참고 링크의 원문을 확인하세요.</p>
      ) : (
        <>
          <ul className="m-0 flex list-none flex-col p-0">
            {shown.map((item, index) => (
              <li
                key={index}
                className="flex flex-col gap-0.5 border-b border-border py-[7px] text-[13px] last:border-b-0"
              >
                <b className="font-semibold [overflow-wrap:anywhere]">{affectedName(item)}</b>
                {item.vulnerable_range === null ? (
                  <span className={SOURCE}>영향 버전 범위 정보 없음</span>
                ) : (
                  // 범위 조각은 통째로 다음 줄로 넘긴다("||" 사이에서 줄바꿈, 실데이터 표시 수정 F2)
                  <code className="font-mono text-xs text-ink-2 [overflow-wrap:anywhere]">
                    {item.vulnerable_range.split('||').map((range, rangeIndex) => (
                      <span key={rangeIndex}>
                        {rangeIndex === 0 ? null : ' || '}
                        <span className="inline-block max-w-full">{range.trim()}</span>
                      </span>
                    ))}
                  </code>
                )}
                {item.fixed_version === null ? null : (
                  <code className="font-mono text-xs text-ink-2">수정: {item.fixed_version}</code>
                )}
              </li>
            ))}
          </ul>
          {items.length > AFFECTED_PREVIEW ? (
            <button type="button" aria-expanded={open} onClick={() => setOpen(open === false)} className={LINK_BUTTON}>
              {open ? '접기' : `그 외 ${items.length - AFFECTED_PREVIEW}개 더 보기`}
            </button>
          ) : null}
        </>
      )}
    </Section>
  )
}

const SUB = 'm-0 text-xs font-semibold text-ink-2'

// 어떻게 조치하나 (사용자 피드백 2026-11-01): ① 업데이트 ② 패치·제조사 공지 ③ CISA 요구 조치(원문) ④ AI 요약의 조치.
// 데이터에 있는 것만 보이고, 하나도 없으면 빈 상태 문구
const FixActions = ({ detail, level }: { detail: CveDetail; level: DetailBodyProps['level'] }) => {
  const targets = updateTargets(detail)
  const links = patchLinks(detail)
  const cisa = requiredAction(detail)
  const ai = detail.summary?.action ?? null
  const empty = targets.length === 0 && links.length === 0 && cisa === null && ai === null
  return (
    <Section id={`${level}-fix`} title="어떻게 조치하나" level={level}>
      {empty ? (
        <p className={NOTE}>수정 버전·조치 정보가 데이터에 없습니다. 아래 제조사 공지에서 패치 여부를 확인하세요.</p>
      ) : (
        <div className="flex flex-col gap-3 rounded-[10px] border border-border p-3.5">
          {targets.length === 0 ? null : (
            <div className="flex flex-col gap-1">
              <p className={SUB}>업데이트</p>
              <ul className="m-0 flex list-none flex-col gap-0.5 p-0 text-sm">
                {targets.map((target) => (
                  <li key={target.name} className="[overflow-wrap:anywhere]">
                    <b className="font-semibold text-ink">{target.name}</b>{' '}
                    <span className="font-mono text-[13px] text-ink">{formatVersions(target.versions)}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {links.length === 0 ? null : (
            <div className="flex flex-col gap-1">
              <p className={SUB}>패치·제조사 공지</p>
              <ul className="m-0 flex list-none flex-col gap-1 p-0 text-[13px]">
                {links.map((link) => (
                  <li key={link.url} className="flex min-w-0 items-baseline gap-2">
                    <span className={`${SOURCE} shrink-0`}>{link.kind}</span>
                    <a
                      href={link.url}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="min-w-0 font-semibold text-ink underline underline-offset-2 [overflow-wrap:anywhere]"
                    >
                      {link.text}
                      <span className="sr-only"> (새 창)</span> ↗
                    </a>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {cisa === null ? null : (
            <details className="text-[13px] text-ink-2">
              <summary className="cursor-pointer text-xs font-semibold text-ink-2">CISA 요구 조치(원문)</summary>
              <p lang="en" className="mt-1 mb-0 leading-relaxed [overflow-wrap:anywhere]">
                {cisa}
              </p>
            </details>
          )}
          {ai === null ? null : (
            <div className="flex flex-col gap-0.5">
              <p className="m-0 text-sm leading-relaxed text-ink-2">{ai}</p>
              <p className={SOURCE}>AI 요약의 조치 — 실제 조치는 제조사 공지를 따르세요</p>
            </div>
          )}
        </div>
      )}
    </Section>
  )
}

const References = ({ detail, level }: { detail: CveDetail; level: DetailBodyProps['level'] }) => {
  const rows = referenceRows(detail.id, detail.references)
  return (
    <Section id={`${level}-references`} title="참고 링크" aside={`${rows.length}개`} level={level}>
      <ul className="m-0 flex list-none flex-col gap-1.5 p-0 text-[13px]">
        {rows.map((row) => (
          <li key={row.url} className="flex min-w-0 items-baseline gap-2">
            <span className={`${SOURCE} w-11 shrink-0`}>{row.source}</span>
            <a
              href={row.url}
              target="_blank"
              rel="noopener noreferrer"
              className="min-w-0 text-ink underline-offset-2 [overflow-wrap:anywhere] hover:underline"
            >
              {row.text}
              <span className="sr-only"> (새 창)</span> ↗
            </a>
          </li>
        ))}
      </ul>
    </Section>
  )
}

// v6 상세 본문 (design/v6 c-list renderDetail). 옆 패널과 /cve 페이지가 같이 쓴다
export const DetailBody = ({ detail, today, rank, level }: DetailBodyProps) => {
  const TitleTag = level === 'panel' ? 'h3' : 'p'
  const addedAgo = kevAddedDaysAgo(detail, today)
  const severity = severityLabel(detail.severity)
  const cvss =
    detail.cvss === null
      ? ' · CVSS 없음'
      : ` · CVSS ${detail.cvss.toFixed(1)}${detail.cvss_version === null ? '' : ` (v${detail.cvss_version})`}`
  return (
    <div className="flex min-w-0 flex-col gap-5">
      <header>
        <div className="flex min-w-0 flex-wrap items-center gap-1.5">
          {level === 'panel' ? <span className="font-mono text-lg font-bold text-ink">{detail.id}</span> : null}
          {detail.kev_ransomware ? <RansomBadge /> : null}
          {isNewKev(detail, today) && addedAgo !== null ? (
            <NewKevBadge daysAgo={addedAgo} />
          ) : detail.is_kev ? (
            <KevBadge />
          ) : null}
          {detail.poc_count > 0 ? <PocBadge count={detail.poc_count} /> : null}
          <span
            title={productName(detail)}
            className={`max-w-[220px] truncate rounded-full bg-surface-sunk px-2 py-0.5 text-xs text-ink-2 ${detail.primary_product === null ? 'italic' : ''}`}
          >
            {productName(detail)}
          </span>
        </div>
        <TitleTag className="mt-2 mb-0 text-[15px] leading-[1.55] font-normal text-ink-2 [overflow-wrap:anywhere]">
          {detail.title}
        </TitleTag>
        <div className="mt-2 flex flex-wrap items-center gap-1.5">
          <span className="rounded-full bg-surface-sunk px-2 py-0.5 text-xs font-semibold whitespace-nowrap text-ink">
            {severity}
            {cvss}
          </span>
          <span className={SOURCE}>{rank === null ? `${detail.score}점` : `${rank}위 · ${detail.score}점`}</span>
        </div>
      </header>
      <Facts detail={detail} today={today} />
      <FixActions detail={detail} level={level} />
      <Problem detail={detail} level={level} />
      <p className={NOTE}>
        <b className="text-ink">왜 지금?</b> {detail.why_now}
      </p>
      <Section id={`${level}-score`} title="점수 구성" aside="0 기준 · 더하는 항목은 오른쪽, 감쇠는 왼쪽" level={level}>
        <ScoreTable detail={detail} />
      </Section>
      <Affected detail={detail} level={level} />
      <References detail={detail} level={level} />
    </div>
  )
}
