// 데이터 출처 4종. 순서는 기획서 8장 "푸터 고지"
const DATA_SOURCES = [
  { label: 'NVD', href: 'https://nvd.nist.gov/' },
  { label: 'CISA KEV', href: 'https://www.cisa.gov/known-exploited-vulnerabilities-catalog' },
  { label: 'FIRST EPSS', href: 'https://www.first.org/epss/' },
  { label: 'OSV.dev / GitHub Advisory Database', href: 'https://osv.dev/' },
] as const

const SOURCES_LABEL = '데이터 출처'

// 모든 화면 맨 아래 고지 (WBS 5.11, D14). NVD 비인증 문구는 NVD 이용 약관이 정한 영어 원문 그대로 둔다
// EPSS 는 FIRST EPSS SIG 가 관리하고 점수는 Empirical Security 가 만든다 (first.org/epss 푸터, 2026-10-31 확인)
export const SiteFooter = () => (
  <footer className="flex flex-col gap-2.5 border-t border-border pt-5 text-xs leading-relaxed text-ink-3">
    <div className="flex flex-wrap items-baseline gap-x-4 gap-y-1.5">
      <span aria-hidden className="font-semibold text-ink-2">
        {SOURCES_LABEL}
      </span>
      <ul aria-label={SOURCES_LABEL} className="flex flex-wrap gap-x-4 gap-y-1.5">
        {DATA_SOURCES.map((source) => (
          <li key={source.href}>
            <a href={source.href} target="_blank" rel="noopener noreferrer" className="hover:text-ink-2">
              {source.label}
            </a>
          </li>
        ))}
      </ul>
    </div>
    <p>
      This product uses the NVD API but is not endorsed or certified by the NVD. EPSS 데이터는 FIRST.org의 Exploit
      Prediction Scoring System(Jacobs et al.)을 사용하며, 점수는 Empirical Security 가 생성합니다. CVE는 The MITRE
      Corporation의 등록 상표입니다.
    </p>
    <p>
      이 사이트는 정보 제공 목적이며 정확성·완전성을 보장하지 않습니다. 한국어 요약은 AI가 생성한 것으로 반드시 원문을
      확인하세요.
    </p>
  </footer>
)
