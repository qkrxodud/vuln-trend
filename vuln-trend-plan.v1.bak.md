# VulnTrend — 보안 취약점 트렌드 사이트 기획서

작성일: 2026-10-30

## 1. 개요

- 목적: 지금 대두되고 있는 소프트웨어 보안 취약점을 지속 갱신해 보여주는 공개 웹사이트
- 대상: 특정 조직이 아닌 모든 사람. 언어·플랫폼 구분 없이 전체 소프트웨어
- 제공 형태: 웹사이트만 (이메일·알림·RSS 없음)
- 핵심 가치: 수집이 아니라 **선별**. NVD에는 하루 100~200건이 올라오므로 "지금 뭐가 중요한가"를 점수화해 보여주는 것이 차별점
- 구성: Spring Boot 배치(로컬) + Docker PostgreSQL + React(Vite) + GitHub Pages

## 2. 전체 구조

```
[로컬] vuln-batch (Spring Boot, 웹서버 없음)
   수집(NVD/KEV/EPSS/OSV/뉴스RSS)
   → Docker PostgreSQL 적재
   → 트렌드 점수 계산 (SQL)
   → 상위 N건 한국어 요약 (Spring AI)
   → web/public/data/*.json 생성 (변경분만)
   → git commit & push
                    ↓
[GitHub Pages] React (Vite) 정적 사이트 — JSON fetch해서 렌더링
```

서버·API·Redis 없음. 사이트가 읽는 것은 오직 JSON 파일.

## 3. 데이터 소스

| 소스 | 역할 | 주기 | 방식 |
|---|---|---|---|
| NVD API 2.0 | CVE 원본, CVSS, CPE(영향 제품). 전 언어·플랫폼 커버 | 하루 1~2회 | REST, `lastModStartDate/EndDate` 증분 |
| CISA KEV | 실제 악용 확인 목록. 가장 강한 긴급도 신호 | 하루 1회 | JSON 파일 1개 |
| EPSS (FIRST) | 30일 내 악용 확률. CVSS보다 "지금 위험한가"에 가까움 | 하루 1회 | CSV 전체 받아 cve_id 조인 |
| OSV.dev / GHSA | 패키지 생태계(Packagist, PyPI, npm, Maven, Go, crates.io, RubyGems 등) 분류, 영향 버전 범위, CVE 없는 GHSA 건 | 하루 1회 | REST |
| 보안 뉴스 RSS | 언급량 → 트렌드 신호. BleepingComputer, The Hacker News 등 5~10개 | 1시간~하루 | Rome 파싱, `CVE-\d{4}-\d+` 정규식 추출 |
| (2단계) 벤더 어드바이저리 | MSRC, Chrome, Apple, Cisco 등 | 하루 1회 | RSS/JSON |
| (2단계) GitHub 검색 | CVE-ID로 PoC 저장소 탐지 → `poc_count` | 하루 1회 | GitHub API |

### 언어 커버리지

- NVD는 언어·플랫폼을 가리지 않음. PHP, WordPress 플러그인, Laravel, Python, npm, Go, Rust, Ruby, 펌웨어까지 CVE가 붙으면 전부 포함
- NVD는 "PHP 취약점" 같은 생태계 태그가 없고 CPE 문자열만 제공 → 생태계 필터는 OSV의 `ecosystem` 필드로 해결
- CVE 없이 공개되는 건(Wordfence/Patchstack 자체 ID, GHSA-only)은 OSV/GHSA로 보완
- 결론: NVD + OSV 두 축이면 사실상 전 언어 커버

### NVD API 주의사항

- API 키 없으면 30초에 5회, 키 있으면 50회. 반드시 키 발급
- `lastModStartDate ~ EndDate` 범위 최대 120일 → 초기 백필은 구간을 나눠 실행
- 원본 응답은 JSONB로 보관해 재조회 없이 점수 로직 재계산 가능하게

## 4. 로컬 환경

### docker-compose.yml

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: vuln
      POSTGRES_USER: vuln
      POSTGRES_PASSWORD: vuln
    ports: ["5432:5432"]
    volumes: ["./pgdata:/var/lib/postgresql/data"]
```

`pgdata/`, `.env`는 `.gitignore`. 레포에는 JSON 산출물만 올라감.

### Spring 설정

```properties
spring.main.web-application-type=none
spring.datasource.url=jdbc:postgresql://localhost:5432/vuln
spring.flyway.enabled=true
nvd.api-key=${NVD_API_KEY}
output.dir=../web/public/data
```

## 5. DB 스키마 (Flyway V1)

```sql
create table vulnerability (
  cve_id           varchar(20) primary key,
  title            text,
  description      text,
  cvss_v3_score    numeric(3,1),
  cvss_v3_severity varchar(10),
  cvss_v4_score    numeric(3,1),
  epss_score       numeric(6,5),
  is_kev           boolean default false,
  kev_added_at     date,
  poc_count        int default 0,
  mention_count    int default 0,
  trend_score      numeric(8,2) default 0,
  published_at     timestamptz,
  last_modified_at timestamptz,
  updated_at       timestamptz default now()
);
create index on vulnerability (trend_score desc);
create index on vulnerability (published_at desc);

create table vulnerability_raw (      -- 소스별 원본, 재계산용
  cve_id     varchar(20),
  source     varchar(20),
  payload    jsonb,
  fetched_at timestamptz,
  primary key (cve_id, source)
);

create table affected_product (
  cve_id    varchar(20) references vulnerability,
  vendor    varchar(100),
  product   varchar(100),
  ecosystem varchar(30),              -- OSV: Packagist, PyPI, npm, Maven, Go ...
  slug      varchar(100),             -- 제품별 JSON 파일명
  vulnerable_range text,
  fixed_version    varchar(50),
  primary key (cve_id, vendor, product)
);
create index on affected_product (slug);
create index on affected_product (ecosystem);

create table reference_link (
  cve_id varchar(20) references vulnerability,
  url    text,
  source varchar(20),                 -- nvd / news / poc / vendor
  title  text,
  primary key (cve_id, url)
);

create table summary (
  cve_id       varchar(20) primary key references vulnerability,
  summary_ko   text,
  generated_at timestamptz
);

create table daily_stat (
  stat_date      date primary key,
  new_count      int,
  kev_count      int,
  critical_count int,
  high_count     int,
  medium_count   int,
  low_count      int
);

create table sync_state (
  source         varchar(20) primary key,
  last_synced_at timestamptz,
  last_run_at    timestamptz,
  status         varchar(10)
);
```

메모
- 수집 upsert는 `insert ... on conflict (cve_id) do update`. JPA `saveAll`은 select 후 update라 느리므로 수집 경로는 MyBatis 또는 JdbcTemplate batch 사용. 조회·JSON 생성은 JPA 가능
- 검색은 프론트에서 `search-index.json`(제목+제품명) 필터로 충분. 필요해지면 `to_tsvector`

## 6. 배치 실행 흐름

```
1. NVD 증분   → vulnerability_raw upsert + vulnerability upsert, sync_state 갱신
2. KEV/EPSS   → is_kev / epss_score update
3. OSV        → affected_product(ecosystem, 버전 범위) upsert
4. 뉴스 RSS   → reference_link(source=news) insert, mention_count 집계
5. 점수       → update vulnerability set trend_score = ...  (SQL 한 방)
6. daily_stat → 일별 집계 갱신
7. 요약       → trend 상위 50 중 summary 없는 것만 LLM 호출 후 insert
8. JSON 출력  → 쿼리 결과를 web/public/data/ 에 파일로 (내용 변경분만)
9. git push   → ProcessBuilder: git add -A && git commit -m "data: <시각>" && git push
```

### 트렌드 점수 (초기 가중치)

```sql
update vulnerability set trend_score =
    (is_kev::int * 50)
  + (coalesce(epss_score, 0) * 30)
  + (least(mention_count, 5) * 5)
  + (least(poc_count, 3) * 5)
  + coalesce(cvss_v3_score, 0);
```

- 우선순위: KEV > EPSS > 뉴스 언급 > PoC > CVSS
- 운영하며 조정. `vulnerability_raw` 덕분에 NVD 재조회 없이 전체 재계산 가능
- 시간 감쇠(발행 후 30일 경과 시 감점)는 2단계에서 검토

### 모듈 구성

```
vuln-trend/
  docker-compose.yml
  batch/                  Spring Boot 배치 (CommandLineRunner)
    collector/            NvdCollector, KevCollector, EpssCollector, OsvCollector, NewsRssCollector
    scorer/               TrendScorer (SQL)
    summarizer/           Spring AI 요약
    exporter/             JsonExporter (변경분만 쓰기), GitPusher
  web/                    Vite + React + TS
    public/data/          배치가 쓰는 JSON (push 대상)
```

`Collector` 인터페이스 하나 두고 소스별 구현 → 순회. 소스 추가가 쉬움.

## 7. JSON 파일 설계 (사실상 API)

```
web/public/data/
  meta.json                 마지막 갱신 시각, 오늘 신규/KEV 신규/Critical/PoC 건수
  stats.json                최근 14일 일별 건수, 이번 주 심각도 분포
  index.json                트렌드 상위 100건 (id, 제목, 점수, KEV, PoC, EPSS, CVSS, 제품, 한줄요약)
  recent.json               최근 7일 신규
  search-index.json         전체 CVE id + 제목 + 제품명 (검색용, 가볍게)
  cve/CVE-2026-XXXXX.json   상세 1건씩
  products/chrome.json      제품별 목록 + 월별 추이
  ecosystems/packagist.json 생태계별 목록 (PHP/Python/JS/Java...)
```

원칙
- 메인이 읽는 `index.json`은 작게. 전체를 한 파일에 넣지 않음
- 상세는 CVE당 파일 1개. GitHub Pages 용량 1GB, 파일 수 제한 사실상 없음
- 내용이 같으면 파일을 건드리지 않아 git diff 최소화 (push 속도의 핵심)
- 레포가 수백 MB를 넘으면 1년 이상 지난 `cve/` 파일을 아카이브 JSON 하나로 합치는 정리 배치 추가

## 8. 노출 설계

### 메인 페이지 — "3초 안에 오늘 뭐가 위험한지"

위에서 아래로 숫자 → 추이 → 목록.

```
┌─────────────────────────────────────────────────────────┐
│ VulnTrend                           마지막 갱신 10-30 09:00 │
│ [검색: CVE-ID / 제품명]   필터: [KEV만] [Critical] [생태계▾] │
├──────────┬──────────┬──────────┬──────────┤
│ 오늘 신규 │ KEV 신규 │ Critical │ PoC 공개 │   ← meta.json
│   142    │    3     │    11    │    7     │
├─────────────────────────┬───────────────────┤
│ 최근 14일 일별 신규 (막대) │ 이번 주 심각도 (도넛) │   ← stats.json
├─────────────────────────┴───────────────────┤
│ 지금 주목할 취약점 (트렌드 점수순)              ← index.json
│ CVE-2026-41203 [KEV][PoC][Chrome]  점수 98  EPSS 0.91  CVSS 8.8 │
│   V8 엔진 타입 혼동으로 원격 코드 실행, 실제 공격에 악용 중        │
│ CVE-2026-39877 [KEV][Windows]      점수 87  EPSS 0.64  CVSS 7.8 │
│   ...                                                   │
└─────────────────────────────────────────────────────────┘
```

| 영역 | 보여주는 것 | 출처 |
|---|---|---|
| 요약 숫자 4개 | 오늘 신규, KEV 신규, Critical(9.0+), PoC 공개 | `meta.json` |
| 일별 신규 막대 | 최근 14일 추이 | `stats.json` |
| 심각도 도넛 | 이번 주 분포, 범례에 비율 | `stats.json` |
| 트렌드 목록 | CVE-ID + 배지(KEV/PoC/제품) + 한 줄 요약 + 점수 3종 | `index.json` |

### 상세 페이지 `/cve/CVE-2026-XXXXX`

- 머리: CVE-ID, 배지, 점수 3종(트렌드/EPSS/CVSS) 큰 숫자, 영향 제품·버전 범위
- 본문: 한국어 3줄 요약(무엇이 문제 / 누가 영향 / 뭘 해야 하나) → 원문 설명 → 참고 링크(벤더 어드바이저리, 뉴스, PoC)
- 하단: 같은 제품의 최근 취약점 5건

### 제품별 `/product/chrome`, 생태계별 `/ecosystem/packagist`

- 메인과 같은 템플릿에 필터 고정 + 월별 추이 미니 차트
- 검색 유입용 페이지

### 색상 규칙

- 심각도: Critical 빨강 / High 주황 / Medium 노랑 / Low 회색 — 고정
- KEV 배지: 빨강 하나만. PoC 배지: 주황
- 그 외는 전부 회색 계열. 색을 더 쓰면 어수선해짐

### 프론트 기술

- Vite + React + TypeScript, Tailwind, React Router, Recharts
- `vite.config.ts`의 `base`를 레포명(`/vuln-trend/`)으로
- GitHub Pages는 SPA 라우팅 미지원 → `404.html` → `index.html` 리다이렉트 트릭
- SEO: SPA라 약함. 배치에서 CVE별 정적 HTML(`cve/CVE-xxx/index.html`)에 `<title>`·메타 description 넣어 생성하는 방식으로 2단계에서 보완. sitemap.xml도 배치 생성

## 9. 로드맵

| 단계 | 내용 | 산출물 | 기간 |
|---|---|---|---|
| 0. 준비 | 레포 생성, Docker Postgres, NVD API 키 발급, Flyway V1 | `docker-compose.yml`, 스키마 | 1일 |
| 1. 수집 | NVD 증분 수집기 + `sync_state` 커서, 초기 백필(최근 1년) | `vulnerability`, `vulnerability_raw` | 3~4일 |
| 2. 보강 | KEV, EPSS, OSV 수집. 뉴스 RSS 5~10개 | `is_kev`, `epss_score`, `ecosystem`, `mention_count` | 3일 |
| 3. 점수 | `trend_score` SQL, `daily_stat` 집계 | 점수 정렬 | 1일 |
| 4. 출력 | JSON 생성기(변경분만), git push 자동화 | `web/public/data/*` | 2일 |
| 5. 프론트 | Vite React, 메인·상세·제품별·검색, 차트 | GitHub Pages 공개 | 5~7일 |
| 6. 요약 | Spring AI로 상위 50건 한국어 요약 | `summary_ko` | 2일 |
| 7. 다듬기 | 정적 HTML 메타태그, sitemap, 도메인 연결 | 검색 유입 | 2~3일 |

- 1~4단계가 끝나면 JSON이 나오므로 그 시점부터 프론트 병행 가능
- 6단계(요약)는 LLM 비용이 드니 사이트가 돌아간 뒤 추가

### 첫 공개 범위 (MVP, 2주 목표)

- 데이터: NVD + KEV + EPSS
- 화면: 메인 + 상세 + 검색
- 공개 후 순서대로 추가: OSV·뉴스 RSS → 요약 → 제품별/생태계별 페이지 → SEO

## 10. 구현 가이드 (체크리스트)

1. 레포 `vuln-trend/` 생성, `batch/`, `web/` 두 디렉토리. `pgdata/`, `.env`는 gitignore
2. `docker compose up -d` → Flyway V1로 테이블 8개 생성
3. NVD API 키를 `.env`에 두고 `NvdCollector` 먼저. 초기 백필은 120일 구간씩 나눠 1년치, 한 번만 실행
4. `KevCollector`(JSON 1개), `EpssCollector`(CSV 전체 → cve_id 조인), `OsvCollector`
5. `NewsRssCollector`: Rome으로 파싱, 본문에서 `CVE-\d{4}-\d+` 추출해 `reference_link` insert → `mention_count` 집계
6. `TrendScorer`: 6장의 SQL 한 방. Java 루프로 돌리지 않음
7. `JsonExporter`: 쿼리 → Jackson → 파일. 기존 파일과 비교해 같으면 skip. `GitPusher`는 ProcessBuilder로 git 호출
8. `npm create vite@latest web -- --template react-ts`, Tailwind, React Router, Recharts. `base` 설정
9. GitHub Pages: Settings → Pages → Source `gh-pages` 브랜치. Actions로 `web/` 빌드·배포. JSON은 `public/data/`에 있으니 빌드에 자동 포함
10. Spring AI 요약(상위 50건, 생성된 건 재생성 안 함) → CVE별 정적 HTML 메타태그 생성으로 SEO

## 11. 운영 가이드

- 배치는 하루 1~2회. 작업 스케줄러에 걸거나 아침에 수동 실행
- 배치 실행 시간 목표 5분 이내. 병목은 git push(파일 수) → 변경분만 쓰는 것이 중요
- 점수 가중치 변경 시 `vulnerability_raw`로 전체 재계산 (NVD 재조회 불필요)
- 공개 후 첫 2주는 메인 상위 10건을 매일 직접 보며 "진짜 지금 중요한가" 확인하고 가중치 조정. 이 튜닝이 사이트 품질의 전부
- 레포 크기 관리: 수백 MB 넘으면 오래된 `cve/` 파일 아카이브 병합
- 뉴스 기사는 링크+제목만 저장·노출. 본문 재게시 금지
- NVD/KEV/EPSS/OSV는 공개 데이터라 라이선스 문제 없음

### GitHub Actions로 옮길 때 (선택)

- 로컬 PC 없이 자동 갱신하려면 Actions 스케줄(`cron: '0 */6 * * *'`)로 배치 실행 후 commit
- NVD 키, LLM 키는 Secrets
- 단, Postgres 상태를 Actions에서 유지할 수 없으므로 (a) Postgres 덤프를 레포에 커밋하거나 (b) 매 실행 전체 재조회 중 선택 필요. 로컬 운영이 안정된 뒤 결정

## 12. 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 대상 | 전 소프트웨어, 모든 사용자 (공개 서비스) |
| 제공 형태 | 웹사이트만 |
| 호스팅 | GitHub Pages (정적), 로컬 배치 → push |
| 백엔드 | Spring Boot 배치 (웹서버 없음), Java 17 |
| DB | Docker PostgreSQL 16 (로컬 전용, JSONB 원본 보관) |
| 프론트 | React (Vite + TS), Thymeleaf 아님 |
| 1차 소스 | NVD + KEV + EPSS |
| 2차 소스 | OSV/GHSA, 뉴스 RSS, 벤더 어드바이저리, GitHub PoC |
| 차별점 | 트렌드 점수 선별 + 한국어 요약 + 차트 중심 노출 |
