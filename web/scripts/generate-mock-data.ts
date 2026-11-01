// 목업 JSON 생성기 — M1(배치가 실제 JSON 을 쓰기) 전까지 화면 개발용.
// 실행: npm run mock:data -- --out <폴더>  (web/ 에서, Node 22.6+ 타입 제거 실행)
// 점수·왜 지금? 은 기획서 6장 MVP 점수식·규칙 템플릿을 그대로 따라 숫자가 서로 맞게 만든다.
// CVE 번호와 내용은 가짜다. 실제 취약점이 아니다.
import { existsSync, mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import type { CvssVersion, IsoDate, Severity } from '../src/types/common.ts'
import type { AffectedProduct, CveDetail, ReferenceLink } from '../src/types/cve.ts'
import type { KpiCounts, Meta } from '../src/types/meta.ts'
import type { SearchIndex } from '../src/types/search.ts'
import type { DailyCount, Stats } from '../src/types/stats.ts'
import type { ScoreBreakdown, TrendRow } from '../src/types/trend.ts'
import { mockOutputPlan, refuseOverwrite } from './mock-guard.ts'

// M1 뒤 public/data 는 배치 산출물이다. 덮어쓰려면 --force, 다른 곳에 쓰려면 --out <폴더>
const plan = mockOutputPlan(process.argv.slice(2), join(import.meta.dirname, '..', 'public', 'data'))
const refusal = refuseOverwrite(plan, existsSync(join(plan.outDir, 'meta.json')))
if (refusal !== null) {
  console.error(refusal)
  process.exit(1)
}
const OUT_DIR = plan.outDir
const NOW = new Date('2026-10-30T09:00:00+09:00')
const DAY_MS = 86_400_000
const HALF_LIFE_DAYS = 30

interface Seed {
  num: number
  product: string
  vendor: string
  cpeProduct: string
  slug: string
  english: string
  what: string | null
  cvss: number | null
  cvssVersion: CvssVersion | null
  epss: number | null
  percentile: number | null
  publishedDaysAgo: number
  kev?: { addedDaysAgo: number; dueInDays: number; ransomware: boolean }
}

// 화면에서 보고 싶은 경우를 고루 넣는다: KEV+랜섬웨어, KEV, EPSS 상위, 미평가, 요약 없음, 오래됐지만 KEV 신규 등재
const SEEDS: Seed[] = [
  {
    num: 41203,
    product: 'Chrome',
    vendor: 'google',
    cpeProduct: 'chrome',
    slug: 'chrome',
    english:
      'Type confusion in V8 in Google Chrome allows a remote attacker to execute arbitrary code via a crafted HTML page.',
    what: 'V8 엔진 타입 혼동으로 악성 웹페이지를 열면 원격 코드가 실행됩니다.',
    cvss: 8.8,
    cvssVersion: '3.1',
    epss: 0.91,
    percentile: 0.997,
    publishedDaysAgo: 2,
    kev: { addedDaysAgo: 1, dueInDays: 14, ransomware: false },
  },
  {
    num: 40117,
    product: 'FortiOS',
    vendor: 'fortinet',
    cpeProduct: 'fortios',
    slug: 'fortios',
    english:
      'An out-of-bounds write in the SSL-VPN component of Fortinet FortiOS allows an unauthenticated attacker to execute code via crafted requests.',
    what: 'SSL-VPN 메모리 쓰기 오류로 인증 없이 장비를 장악할 수 있습니다.',
    cvss: 9.8,
    cvssVersion: '3.1',
    epss: 0.94,
    percentile: 0.999,
    publishedDaysAgo: 4,
    kev: { addedDaysAgo: 3, dueInDays: 4, ransomware: true },
  },
  {
    num: 39950,
    product: 'Ivanti Connect Secure',
    vendor: 'ivanti',
    cpeProduct: 'connect_secure',
    slug: 'ivanti-connect-secure',
    english:
      'A stack-based buffer overflow in Ivanti Connect Secure before 22.7R2.6 allows a remote unauthenticated attacker to achieve remote code execution.',
    what: '스택 버퍼 오버플로로 인증 없이 원격 코드가 실행됩니다.',
    cvss: 9.0,
    cvssVersion: '3.1',
    epss: 0.88,
    percentile: 0.995,
    publishedDaysAgo: 6,
    kev: { addedDaysAgo: 5, dueInDays: 2, ransomware: true },
  },
  {
    num: 41877,
    product: 'Windows',
    vendor: 'microsoft',
    cpeProduct: 'windows_11_24h2',
    slug: 'windows',
    english: 'Windows Common Log File System Driver Elevation of Privilege Vulnerability.',
    what: 'CLFS 드라이버 결함으로 일반 사용자가 SYSTEM 권한을 얻습니다.',
    cvss: 7.8,
    cvssVersion: '3.1',
    epss: 0.42,
    percentile: 0.97,
    publishedDaysAgo: 0,
    kev: { addedDaysAgo: 0, dueInDays: 21, ransomware: false },
  },
  {
    num: 21544,
    product: 'Apache Tomcat',
    vendor: 'apache',
    cpeProduct: 'tomcat',
    slug: 'tomcat',
    english:
      'Path equivalence in Apache Tomcat partial PUT handling allows remote code execution when default servlet writes are enabled.',
    what: 'partial PUT 처리 결함으로 쓰기 허용 설정에서 원격 코드가 실행됩니다.',
    cvss: 9.8,
    cvssVersion: '3.1',
    epss: 0.93,
    percentile: 0.998,
    publishedDaysAgo: 210,
    kev: { addedDaysAgo: 2, dueInDays: 19, ransomware: false },
  },
  {
    num: 42010,
    product: 'PAN-OS',
    vendor: 'paloaltonetworks',
    cpeProduct: 'pan-os',
    slug: 'pan-os',
    english:
      'An authentication bypass in the management web interface of Palo Alto Networks PAN-OS allows an unauthenticated attacker with network access to invoke PHP scripts.',
    what: '관리 웹 화면 인증 우회로 관리자 기능을 호출할 수 있습니다.',
    cvss: 9.3,
    cvssVersion: '4.0',
    epss: 0.71,
    percentile: 0.985,
    publishedDaysAgo: 3,
  },
  {
    num: 41990,
    product: 'Linux Kernel',
    vendor: 'linux',
    cpeProduct: 'linux_kernel',
    slug: 'linux-kernel',
    english: 'In the Linux kernel, a use-after-free in netfilter nf_tables allows a local user to escalate privileges.',
    what: 'netfilter 해제 후 사용 결함으로 로컬 사용자가 root 가 됩니다.',
    cvss: 7.8,
    cvssVersion: '3.1',
    epss: 0.12,
    percentile: 0.95,
    publishedDaysAgo: 2,
  },
  {
    num: 42155,
    product: 'WordPress',
    vendor: 'wordpress',
    cpeProduct: 'wordpress',
    slug: 'wordpress',
    english:
      'A stored cross-site scripting vulnerability in the WordPress block editor allows contributors to inject scripts.',
    what: null,
    cvss: 6.4,
    cvssVersion: '3.1',
    epss: 0.003,
    percentile: 0.41,
    publishedDaysAgo: 1,
  },
  {
    num: 42201,
    product: 'OpenSSL',
    vendor: 'openssl',
    cpeProduct: 'openssl',
    slug: 'openssl',
    english: 'Processing a maliciously crafted X.509 certificate may lead to a denial of service in OpenSSL.',
    what: '조작된 인증서를 처리하면 서비스가 멈출 수 있습니다.',
    cvss: 7.5,
    cvssVersion: '3.1',
    epss: 0.02,
    percentile: 0.82,
    publishedDaysAgo: 1,
  },
  {
    num: 42260,
    product: 'GitLab',
    vendor: 'gitlab',
    cpeProduct: 'gitlab',
    slug: 'gitlab',
    english: 'An issue in GitLab CE/EE allows an attacker to take over accounts via a crafted password reset request.',
    what: '비밀번호 재설정 요청 조작으로 계정을 탈취할 수 있습니다.',
    cvss: 10.0,
    cvssVersion: '3.1',
    epss: 0.35,
    percentile: 0.96,
    publishedDaysAgo: 0,
  },
  {
    num: 42288,
    product: 'Jenkins',
    vendor: 'jenkins',
    cpeProduct: 'jenkins',
    slug: 'jenkins',
    english:
      'Jenkins CLI argument parsing allows unauthenticated attackers to read arbitrary files on the controller file system.',
    what: null,
    cvss: 9.8,
    cvssVersion: '3.1',
    epss: 0.08,
    percentile: 0.93,
    publishedDaysAgo: 0,
  },
  {
    num: 42301,
    product: 'Chrome',
    vendor: 'google',
    cpeProduct: 'chrome',
    slug: 'chrome',
    english:
      'Heap buffer overflow in Skia in Google Chrome allows a remote attacker to perform an out of bounds memory write via a crafted HTML page.',
    what: 'Skia 힙 오버플로로 악성 웹페이지에서 메모리 쓰기가 가능합니다.',
    cvss: 8.8,
    cvssVersion: '3.1',
    epss: 0.01,
    percentile: 0.72,
    publishedDaysAgo: 2,
  },
  {
    num: 42333,
    product: 'VMware vCenter Server',
    vendor: 'vmware',
    cpeProduct: 'vcenter_server',
    slug: 'vcenter-server',
    english: 'vCenter Server contains a heap-overflow vulnerability in the implementation of the DCERPC protocol.',
    what: 'DCERPC 처리 힙 오버플로로 네트워크 접근만으로 원격 코드가 실행됩니다.',
    cvss: 9.8,
    cvssVersion: '3.1',
    epss: 0.22,
    percentile: 0.955,
    publishedDaysAgo: 5,
  },
  {
    num: 42350,
    product: 'Cisco IOS XE',
    vendor: 'cisco',
    cpeProduct: 'ios_xe',
    slug: 'cisco-ios-xe',
    english:
      'A vulnerability in the web UI feature of Cisco IOS XE Software could allow an unauthenticated, remote attacker to create an account with privilege level 15.',
    what: '웹 UI 결함으로 인증 없이 최고 권한 계정을 만들 수 있습니다.',
    cvss: 10.0,
    cvssVersion: '3.1',
    epss: 0.05,
    percentile: 0.9,
    publishedDaysAgo: 1,
  },
  {
    num: 42371,
    product: 'PHP',
    vendor: 'php',
    cpeProduct: 'php',
    slug: 'php',
    english: 'In PHP versions before 8.3.14, argument injection in CGI mode on Windows allows remote code execution.',
    what: null,
    cvss: null,
    cvssVersion: null,
    epss: null,
    percentile: null,
    publishedDaysAgo: 0,
  },
  {
    num: 42390,
    product: 'Node.js',
    vendor: 'nodejs',
    cpeProduct: 'node.js',
    slug: 'nodejs',
    english: 'A path traversal in Node.js permission model allows bypassing file system restrictions.',
    what: '권한 모델 경로 우회로 허용되지 않은 파일을 읽을 수 있습니다.',
    cvss: 7.5,
    cvssVersion: '3.1',
    epss: 0.004,
    percentile: 0.5,
    publishedDaysAgo: 3,
  },
  {
    num: 42402,
    product: 'Apache HTTP Server',
    vendor: 'apache',
    cpeProduct: 'http_server',
    slug: 'apache-http-server',
    english: 'Improper input validation in mod_rewrite of Apache HTTP Server allows request smuggling.',
    what: 'mod_rewrite 입력 검증 결함으로 요청 밀반입이 가능합니다.',
    cvss: 7.5,
    cvssVersion: '3.1',
    epss: 0.01,
    percentile: 0.7,
    publishedDaysAgo: 4,
  },
  {
    num: 42415,
    product: 'macOS',
    vendor: 'apple',
    cpeProduct: 'macos',
    slug: 'macos',
    english: 'A logic issue was addressed with improved checks. An app may be able to break out of its sandbox.',
    what: '앱이 샌드박스를 벗어날 수 있는 논리 결함입니다.',
    cvss: 8.6,
    cvssVersion: '3.1',
    epss: 0.002,
    percentile: 0.3,
    publishedDaysAgo: 2,
  },
  {
    num: 42430,
    product: 'iOS',
    vendor: 'apple',
    cpeProduct: 'iphone_os',
    slug: 'ios',
    english:
      'A use-after-free issue in WebKit may lead to arbitrary code execution when processing maliciously crafted web content.',
    what: 'WebKit 해제 후 사용으로 악성 웹 콘텐츠에서 코드가 실행됩니다.',
    cvss: 8.8,
    cvssVersion: '3.1',
    epss: 0.03,
    percentile: 0.85,
    publishedDaysAgo: 2,
  },
  {
    num: 42444,
    product: 'Microsoft Exchange Server',
    vendor: 'microsoft',
    cpeProduct: 'exchange_server',
    slug: 'exchange-server',
    english: 'Microsoft Exchange Server Remote Code Execution Vulnerability.',
    what: '조작된 메일 처리로 서버에서 원격 코드가 실행됩니다.',
    cvss: 8.0,
    cvssVersion: '3.1',
    epss: 0.06,
    percentile: 0.91,
    publishedDaysAgo: 6,
  },
  {
    num: 42460,
    product: 'Confluence',
    vendor: 'atlassian',
    cpeProduct: 'confluence_data_center',
    slug: 'confluence',
    english: 'A template injection in Confluence Data Center allows an unauthenticated attacker to execute code.',
    what: '템플릿 주입으로 인증 없이 코드가 실행됩니다.',
    cvss: 9.8,
    cvssVersion: '3.1',
    epss: 0.15,
    percentile: 0.95,
    publishedDaysAgo: 7,
  },
  {
    num: 42477,
    product: 'curl',
    vendor: 'haxx',
    cpeProduct: 'curl',
    slug: 'curl',
    english: 'curl may leak credentials to a different host when following redirects with netrc.',
    what: null,
    cvss: 5.3,
    cvssVersion: '3.1',
    epss: 0.001,
    percentile: 0.2,
    publishedDaysAgo: 1,
  },
  {
    num: 42490,
    product: 'MySQL',
    vendor: 'oracle',
    cpeProduct: 'mysql',
    slug: 'mysql',
    english:
      'Vulnerability in the MySQL Server product of Oracle MySQL (component: InnoDB) allows a high privileged attacker to cause a hang.',
    what: 'InnoDB 결함으로 높은 권한 사용자가 서버를 멈출 수 있습니다.',
    cvss: 4.9,
    cvssVersion: '3.1',
    epss: 0.0005,
    percentile: 0.1,
    publishedDaysAgo: 5,
  },
  {
    num: 42505,
    product: 'Firefox',
    vendor: 'mozilla',
    cpeProduct: 'firefox',
    slug: 'firefox',
    english: 'Memory safety bugs present in Firefox 131 could be exploited to run arbitrary code.',
    what: '메모리 안전 버그로 임의 코드 실행 가능성이 있습니다.',
    cvss: 8.8,
    cvssVersion: '3.1',
    epss: 0.004,
    percentile: 0.52,
    publishedDaysAgo: 3,
  },
  {
    num: 42519,
    product: 'Redis',
    vendor: 'redis',
    cpeProduct: 'redis',
    slug: 'redis',
    english: 'An authenticated user may use a specially crafted Lua script to trigger a use-after-free in Redis.',
    what: 'Lua 스크립트로 해제 후 사용을 유발해 코드가 실행될 수 있습니다.',
    cvss: 9.9,
    cvssVersion: '3.1',
    epss: 0.03,
    percentile: 0.86,
    publishedDaysAgo: 2,
  },
  {
    num: 42533,
    product: 'SonicOS',
    vendor: 'sonicwall',
    cpeProduct: 'sonicos',
    slug: 'sonicos',
    english:
      'An improper access control vulnerability in SonicWall SonicOS management access may lead to unauthorized resource access.',
    what: null,
    cvss: null,
    cvssVersion: null,
    epss: null,
    percentile: null,
    publishedDaysAgo: 1,
  },
  {
    num: 42548,
    product: 'Android',
    vendor: 'google',
    cpeProduct: 'android',
    slug: 'android',
    english: 'In Framework, there is a possible escalation of privilege due to a missing permission check.',
    what: '권한 확인 누락으로 앱이 권한을 높일 수 있습니다.',
    cvss: 7.8,
    cvssVersion: '3.1',
    epss: 0.001,
    percentile: 0.25,
    publishedDaysAgo: 4,
  },
  {
    num: 42561,
    product: 'Drupal',
    vendor: 'drupal',
    cpeProduct: 'drupal',
    slug: 'drupal',
    english: 'Drupal core contains a potential PHP object injection vulnerability.',
    what: 'PHP 객체 주입 가능성이 있는 core 결함입니다.',
    cvss: 3.1,
    cvssVersion: '3.1',
    epss: 0.0008,
    percentile: 0.15,
    publishedDaysAgo: 6,
  },
  {
    num: 42574,
    product: 'Samba',
    vendor: 'samba',
    cpeProduct: 'samba',
    slug: 'samba',
    english: 'A flaw in Samba allows an authenticated user to cause a denial of service.',
    what: null,
    cvss: 6.5,
    cvssVersion: '3.1',
    epss: 0.0009,
    percentile: 0.18,
    publishedDaysAgo: 5,
  },
  {
    num: 42588,
    product: 'PostgreSQL',
    vendor: 'postgresql',
    cpeProduct: 'postgresql',
    slug: 'postgresql',
    english:
      'Incomplete tracking in PostgreSQL of tables with row security allows a reused query to view or change different rows.',
    what: '행 보안 추적 누락으로 다른 행을 보거나 바꿀 수 있습니다.',
    cvss: 3.8,
    cvssVersion: '3.1',
    epss: 0.0004,
    percentile: 0.08,
    publishedDaysAgo: 3,
  },
]

const round1 = (n: number): number => Math.round(n * 10) / 10

const toKstDate = (d: Date): IsoDate => new Date(d.getTime() + 9 * 3_600_000).toISOString().slice(0, 10)

const toKstDateTime = (d: Date): string => `${new Date(d.getTime() + 9 * 3_600_000).toISOString().slice(0, 19)}+09:00`

const daysAgo = (days: number): Date => new Date(NOW.getTime() - days * DAY_MS)

const severityOf = (cvss: number | null): Severity => {
  if (cvss === null) return 'UNRATED'
  if (cvss >= 9.0) return 'CRITICAL'
  if (cvss >= 7.0) return 'HIGH'
  if (cvss >= 4.0) return 'MEDIUM'
  return 'LOW'
}

// 기획서 6장 MVP 점수식: (KEV 50 + 랜섬웨어 10 + EPSS×30 + CVSS) × 0.5^(경과일/30)
const breakdownOf = (s: Seed): ScoreBreakdown => {
  const kev = s.kev ? 50 : 0
  const ransomware = s.kev?.ransomware ? 10 : 0
  const epss = (s.epss ?? 0) * 30
  const cvss = s.cvss ?? 0
  const raw = kev + ransomware + epss + cvss
  const referenceDaysAgo = Math.min(s.publishedDaysAgo, s.kev?.addedDaysAgo ?? s.publishedDaysAgo)
  const decayed = raw * 0.5 ** (referenceDaysAgo / HALF_LIFE_DAYS)
  return { kev, ransomware, epss: round1(epss), news: 0, poc: 0, cvss: round1(cvss), decay: round1(decayed - raw) }
}

const scoreOf = (b: ScoreBreakdown): number =>
  Math.round(b.kev + b.ransomware + b.epss + b.news + b.poc + b.cvss + b.decay)

// 기획서 6장 "왜 지금?" 규칙 템플릿. PoC·뉴스는 MVP 에서 0 이라 해당 행은 나오지 않는다
const whyNowOf = (s: Seed): string => {
  if (s.kev?.ransomware) return '랜섬웨어 캠페인에 사용 중'
  if (s.kev) return 'CISA 악용 목록 등재'
  if (s.percentile !== null && s.percentile >= 0.95)
    return `30일 내 악용 확률 상위 ${Math.max(1, Math.round((1 - s.percentile) * 100))}%`
  if (s.cvss === null) return 'CVSS 미평가, 최근 공개'
  return `CVSS ${s.cvss.toFixed(1)}, 최근 공개`
}

const titleOf = (s: Seed): string => {
  if (s.what) return s.what
  const firstSentence = s.english.split('. ')[0].replace(/\.$/, '')
  return firstSentence.slice(0, 80)
}

const idOf = (s: Seed): string => `CVE-2026-${s.num}`

const rowOf = (s: Seed): TrendRow => {
  const score_breakdown = breakdownOf(s)
  return {
    id: idOf(s),
    title: titleOf(s),
    score: scoreOf(score_breakdown),
    score_breakdown,
    why_now: whyNowOf(s),
    is_kev: s.kev !== undefined,
    kev_ransomware: s.kev?.ransomware ?? false,
    kev_due_date: s.kev ? toKstDate(new Date(NOW.getTime() + s.kev.dueInDays * DAY_MS)) : null,
    kev_added_at: s.kev ? toKstDate(daysAgo(s.kev.addedDaysAgo)) : null,
    published_at: toKstDateTime(daysAgo(s.publishedDaysAgo)),
    poc_count: 0,
    epss: s.epss,
    epss_percentile: s.percentile,
    cvss: s.cvss,
    cvss_version: s.cvssVersion,
    severity: severityOf(s.cvss),
    primary_product: s.product,
    summary_what: s.what,
    fix_versions: [],
    patch_url: `https://example.com/advisories/${idOf(s)}`,
  }
}

const affectedOf = (s: Seed): AffectedProduct[] => [
  {
    vendor: s.vendor,
    product: s.cpeProduct,
    display_name: s.product,
    slug: s.slug,
    ecosystem: null,
    vulnerable_range: '< 수정 버전 (목업)',
    fixed_version: null,
    fix_versions: [],
  },
]

const referencesOf = (s: Seed): ReferenceLink[] => [
  {
    url: `https://example.com/advisories/${idOf(s)}`,
    source: 'vendor',
    title: null,
    tags: ['Vendor Advisory', 'Patch'],
  },
  { url: `https://nvd.nist.gov/vuln/detail/${idOf(s)}`, source: 'nvd', title: null, tags: [] },
]

const detailOf = (s: Seed): CveDetail => {
  const published = daysAgo(s.publishedDaysAgo)
  return {
    ...rowOf(s),
    description: s.english,
    published_at: toKstDateTime(published),
    last_modified_at: toKstDateTime(published),
    kev_added_at: s.kev ? toKstDate(daysAgo(s.kev.addedDaysAgo)) : null,
    kev_required_action: s.kev
      ? 'Apply mitigations per vendor instructions or discontinue use of the product if mitigations are unavailable.'
      : null,
    summary: s.what
      ? {
          what: s.what,
          who: `${s.product} 를 쓰는 조직·사용자`,
          action: `${s.product} 를 최신 보안 업데이트 버전 이상으로 업데이트하세요.`,
          model: 'mock',
          generated_at: toKstDateTime(NOW),
        }
      : null,
    affected: affectedOf(s),
    references: referencesOf(s),
  }
}

const sumKpi = (rows: Seed[], windowStart: number, windowEnd: number): KpiCounts => {
  const inWindow = (daysAgoValue: number): boolean => daysAgoValue >= windowStart && daysAgoValue < windowEnd
  const fresh = rows.filter((s) => inWindow(s.publishedDaysAgo))
  return {
    new: fresh.length + 130,
    kev_new: rows.filter((s) => s.kev !== undefined && inWindow(s.kev.addedDaysAgo)).length,
    critical: fresh.filter((s) => (s.cvss ?? 0) >= 9.0).length + 8,
    high_risk: 318,
  }
}

const meta: Meta = {
  updated_at: toKstDateTime(NOW),
  version: 'mock-20261030-0900',
  today: sumKpi(SEEDS, 0, 1),
  // MOCK_NO_YESTERDAY_HIGH_RISK=1 이면 어제 고위험 스냅샷이 없는 경우(첫 실행)를 흉내 낸다 → 고위험 카드 증감 숨김
  yesterday: { ...sumKpi(SEEDS, 1, 2), high_risk: process.env.MOCK_NO_YESTERDAY_HIGH_RISK === '1' ? null : 313 },
  kev_added_7d: SEEDS.filter((s) => s.kev !== undefined && s.kev.addedDaysAgo < 7).length,
  kev_added_prev_7d: SEEDS.filter((s) => s.kev !== undefined && s.kev.addedDaysAgo >= 7 && s.kev.addedDaysAgo < 14)
    .length,
}

// 14일 막대: 평일 120~160, 주말 60~80 정도로 흉내
const DAILY_NEW = [138, 151, 72, 64, 144, 160, 133, 149, 157, 70, 61, 142, 133, 142]
const DAILY_CRITICAL = [9, 12, 3, 2, 10, 14, 8, 11, 13, 4, 2, 9, 7, 11]
const daily: DailyCount[] = DAILY_NEW.map((count, i) => ({
  date: toKstDate(daysAgo(13 - i)),
  new: count,
  critical: DAILY_CRITICAL[i],
}))

const stats: Stats = {
  daily,
  severity_7d: { critical: 54, high: 231, medium: 302, low: 41, unrated: 236 },
}

const rows = SEEDS.map(rowOf).sort((a, b) => b.score - a.score)
// 최근 7일 새 CVE: 7일 안 공개 전부, 공개 시각 내림차순 (/new 화면, 사용자 결정 2026-11-01)
const recent = SEEDS.filter((s) => s.publishedDaysAgo < 7)
  .sort((a, b) => a.publishedDaysAgo - b.publishedDaysAgo)
  .map(rowOf)
const search: SearchIndex = SEEDS.map((s) => ({ id: idOf(s), title: titleOf(s), product: s.product }))

const writeJson = (relativePath: string, value: unknown): void => {
  writeFileSync(join(OUT_DIR, relativePath), `${JSON.stringify(value, null, 2)}\n`)
}

mkdirSync(join(OUT_DIR, 'cve'), { recursive: true })
writeJson('meta.json', meta)
writeJson('stats.json', stats)
writeJson('index.json', rows)
writeJson('recent.json', recent)
writeJson('search-index.json', search)
// 상세는 마지막 하나만 빼고 만든다: "상세 JSON 없는 CVE → NVD 링크" 경로(P6)를 화면에서 확인하려고
SEEDS.slice(0, -1).forEach((s) => writeJson(`cve/${idOf(s)}.json`, detailOf(s)))

console.log(`Mock data written: ${rows.length} rows, ${SEEDS.length - 1} detail files -> ${OUT_DIR}`)
