# VulnTrend — 보안 취약점 트렌드 사이트 기획서

작성일: 2026-10-30
개정: 2026-10-30 v2 — 리뷰 합의안(`vuln-trend-review.md`) 및 사용자 결정 Q1~Q4 반영

## 1. 개요

- 목적: 지금 대두되고 있는 소프트웨어 보안 취약점을 지속 갱신해 보여주는 공개 웹사이트
- 대상: 특정 조직이 아닌 모든 사람. 언어·플랫폼 구분 없이 전체 소프트웨어
- 제공 형태: 웹사이트만 (이메일·알림·RSS 없음, 로그인 없음)
- 핵심 가치: 수집이 아니라 **선별**. NVD에는 하루 100~200건이 올라오므로 "지금 뭐가 중요한가"를 점수화해 보여주는 것이 차별점. 점수에는 **시간 감쇠**가 반드시 포함되어 오래된 유명 취약점이 상위를 고정 점유하지 않음
- 구성: Spring Boot 배치(로컬) + Docker PostgreSQL + React(Vite) + GitHub Pages
- 하지 않는 것: 방문자 자산 관리, 티켓, 조치 명령어 생성, 방문 분석 도구 (리뷰 결정)

## 2. 전체 구조

```
[로컬] vuln-batch (Spring Boot, 웹서버 없음)
   수집(NVD/KEV/EPSS/OSV/뉴스RSS)
   → Docker PostgreSQL 적재
   → 트렌드 점수 계산 (SQL, 시간 감쇠 포함)
   → 상위 20건 한국어 요약 (Spring AI)
   → web/public/data/*.json 생성 (변경분만)
   → 모든 단계 성공 시에만 git commit & push
                    ↓
[GitHub Pages] React (Vite) 정적 사이트 — JSON fetch해서 렌더링
```

서버·API·Redis 없음. 사이트가 읽는 것은 오직 JSON 파일. 코드와 데이터는 **단일 레포**(사용자 결정 Q1).

## 3. 데이터 소스

| 소스 | 역할 | 주기 | 방식 |
|---|---|---|---|
| NVD API 2.0 | CVE 원본, CVSS, CPE(영향 제품). 전 언어·플랫폼 커버 | 하루 1~2회 | REST, `lastModStartDate/EndDate` 증분 |
| CISA KEV | 실제 악용 확인 목록. 가장 강한 긴급도 신호. `dueDate`(패치 기한), `knownRansomwareCampaignUse` 활용 | 하루 1회 | JSON 파일 1개 |
| EPSS (FIRST) | 30일 내 악용 확률. CVSS보다 "지금 위험한가"에 가까움. `epss`와 `percentile` 둘 다 저장 | 하루 1회 | CSV 전체 받아 vuln_id 조인 |
| OSV.dev / GHSA | 패키지 생태계(Packagist, PyPI, npm, Maven, Go, crates.io, RubyGems 등) 분류, 영향 버전 범위, 수정 버전, GHSA summary(제목용) | 하루 1회 | REST |
| 보안 뉴스 RSS | 언급량 → 트렌드 신호. BleepingComputer, The Hacker News 등 5~10개 | 1시간~하루 | Rome 파싱, `CVE-\d{4}-\d+` 정규식 추출 |
| (2단계) 벤더 어드바이저리 | MSRC, Chrome, Apple, Cisco 등. "같은 패치로 함께 해결되는 CVE 묶음" 근거 | 하루 1회 | RSS/JSON |
| (2단계) GitHub 검색 | CVE-ID로 PoC 저장소 탐지 → `poc_count` | 하루 1회 | GitHub API |

### 언어 커버리지

- NVD는 언어·플랫폼을 가리지 않음. PHP, WordPress 플러그인, Laravel, Python, npm, Go, Rust, Ruby, 펌웨어까지 CVE가 붙으면 전부 포함
- NVD는 "PHP 취약점" 같은 생태계 태그가 없고 CPE 문자열만 제공 → 생태계 필터는 OSV의 `ecosystem` 필드로 해결
- CVE 없이 공개되는 건(Wordfence/Patchstack 자체 ID, GHSA-only)은 **MVP에서 제외**. 스키마의 PK는 `vuln_id`로 두어 나중에 GHSA-only를 추가할 때 마이그레이션이 없게 함
- 결론: NVD + OSV 두 축이면 사실상 전 언어 커버

### 수집 규칙

- NVD `vulnStatus = Rejected` 건은 수집 시 제외. 이미 저장된 건이 Rejected로 바뀌면 `rejected=true`로 표시하고, JSON 출력 단계에서 per-CVE 파일 삭제 + 모든 목록·search-index에서 제외 (7장)
- CVSS 선택 우선순위: NVD v3.1 > CNA v3.1 > v4. v4만 있으면 v4를 사용하고 `cvss_version`에 기록해 UI에 버전 표기
- CVSS가 없는 건(Awaiting Analysis)은 심각도 "미평가"로 분류
- `title`은 NVD에 없음. 채우는 순서: GHSA `summary` > 한국어 요약 `what` 첫 줄 > `description` 첫 문장 80자
- 모든 시각은 UTC로 저장, 표시만 KST
- "최근 24시간"은 배치 실행 시각에서 24시간 역산 (KST 자정 기준 "오늘"은 09시 배치에서 거의 0건이라 부적합)

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
spring.datasource.url=jdbc:postgresql://localhost:${DB_PORT:5432}/vuln   # 5432 를 다른 앱이 쓰면 .env 에 DB_PORT
spring.flyway.enabled=true
nvd.api-key=${NVD_API_KEY}
output.dir=../web/public/data
summary.daily-limit=20
```

## 5. DB 스키마 (Flyway V1)

```sql
create table vulnerability (
  vuln_id          varchar(20) primary key,   -- CVE-xxxx (GHSA-only 대비해 vuln_id)
  title            text,                      -- 3장 title 규칙으로 채움
  description      text,
  cvss_score       numeric(3,1),
  cvss_severity    varchar(10),               -- CRITICAL/HIGH/MEDIUM/LOW/UNRATED
  cvss_version     varchar(5),                -- 3.1 / 4.0
  epss_score       numeric(6,5),
  epss_percentile  numeric(6,5),
  is_kev           boolean default false,
  kev_added_at     date,
  kev_due_date     date,
  kev_ransomware   boolean default false,
  poc_count        int default 0,
  mention_count    int default 0,             -- 최근 7일 창 집계 (누적 아님)
  last_mention_at  timestamptz,
  primary_product  varchar(100),              -- 5장 product_alias로 정규화된 display_name
  trend_score      numeric(8,2) default 0,
  published_at     timestamptz,
  last_modified_at timestamptz,
  rejected         boolean default false,
  updated_at       timestamptz default now()
);
create index on vulnerability (trend_score desc);
create index on vulnerability (published_at desc);

create table vulnerability_raw (      -- 소스별 원본, 재계산용
  vuln_id    varchar(20),
  source     varchar(20),
  payload    jsonb,
  fetched_at timestamptz,
  primary key (vuln_id, source)
);

create table affected_product (
  vuln_id   varchar(20) references vulnerability,
  vendor    varchar(100) not null,     -- OSV 패키지는 vendor 대신 ecosystem 값을 채움
  product   varchar(100) not null,
  ecosystem varchar(30),               -- OSV: Packagist, PyPI, npm, Maven, Go ...
  slug      varchar(100),              -- product_alias로 정규화된 제품 slug
  vulnerable_range text,
  fixed_version    varchar(50),      -- MVP: NVD CPE configurations의 versionStart/EndIncluding/Excluding으로 range만 채움. fixed_version은 OSV 수집 후
  primary key (vuln_id, vendor, product)
);
create index on affected_product (slug);
create index on affected_product (ecosystem);

create table product_alias (          -- CPE 제품명 정규화. 초기 데이터는 레포 내 YAML(약 50개) 수동 관리
  cpe_vendor   varchar(100),
  cpe_product  varchar(100),
  slug         varchar(100) not null, -- chrome
  display_name varchar(100) not null, -- Chrome
  primary key (cpe_vendor, cpe_product)
);

create table reference_link (
  vuln_id varchar(20) references vulnerability,
  url     text,
  source  varchar(20),                -- nvd / news / poc / vendor
  title   text,
  seen_at timestamptz,
  primary key (vuln_id, url)
);

create table summary (
  vuln_id      varchar(20) primary key references vulnerability,
  what         text,                  -- 무엇이 문제인가
  who          text,                  -- 누가 영향받나
  action       text,                  -- 뭘 해야 하나 ("X 이상으로 업데이트" 수준, 명령어 없음)
  model        varchar(50),
  generated_at timestamptz
);

create table daily_stat (
  stat_date      date primary key,     -- published_at의 KST 달력일 (배치 실행일 아님)
  new_count      int,                  -- 그 날 발행된 신규
  kev_new_count  int,                  -- 그 날 KEV 등재
  critical_count int,                  -- 그 날 신규 중 CVSS 9.0+
  high_risk_count int,                 -- 그 날 기준 전체 중 EPSS 0.5 이상 (스냅샷)
  high_count     int,
  medium_count   int,
  low_count      int,
  unrated_count  int
);

create table sync_state (
  source         varchar(20) primary key,
  last_synced_at timestamptz,          -- 성공한 실행에서만 전진
  last_run_at    timestamptz,
  status         varchar(10)
);
```

메모
- 수집 upsert는 `insert ... on conflict (vuln_id) do update`. JPA `saveAll`은 select 후 update라 느리므로 수집 경로는 MyBatis 또는 JdbcTemplate batch 사용. 조회·JSON 생성은 JPA 가능
- 검색은 프론트에서 `search-index.json`(제목+제품명) 필터로 충분. 필요해지면 `to_tsvector`
- 수집 upsert 세부(사용자 승인 2026-10-31): `title` 은 처음 들어갈 때만 NVD 값(coalesce) — GHSA·요약이 고친 제목을 NVD 재수집이 덮지 않음. `affected_product` 충돌 시 `vulnerable_range` 만 갱신(slug·ecosystem·fixed_version 보존). `product_alias` 는 yml 원본과 같게 통째 교체. CVSS 선택은 NVD 3.1 > CNA 3.1 > NVD 3.0 > CNA 3.0 > 4.0 > UNRATED, 점수 0.0 은 LOW
- `primary_product` 선정 규칙: KEV `vendorProject/product` > OSV package(ecosystem 포함) > CPE 첫 항목. 셋 모두 `product_alias`로 정규화. KEV 표기는 자유 형식이라 `product-alias.yml` 에 제품마다 KEV 원문 별칭을 명시하고, 적재·조회 양쪽에 같은 정규화(소문자, 영숫자 외 연속 문자는 `_`)를 쓴다. 테이블 열은 추가하지 않는다. KEV "Multiple Products" 류는 매핑하지 않고 다음 순위로 넘긴다. 초기 데이터 "50개"는 제품(slug) 50개이며 행은 별칭 포함 80~100개. Windows Server 는 `windows` 로 합친다(사용자 확정 2026-10-31, WBS R7). 순위 세부(사용자 확정 2026-10-31): KEV 표기(표) > CPE 첫 항목(표) > CPE 첫 항목 원문 > KEV 제품 원문(NVD 가 아직 CPE 를 달지 않은 KEV 신규 건) > 없음. KEV 표기가 표에 없으면 CPE 표준 이름을 쓴다. 미매핑 제품은 CPE product 원문을 그대로 노출하고, 메인 상위에 올라온 건만 2주 튜닝 루틴에서 매핑 추가
- `why_now`는 DB에 저장하지 않고 JSON 출력 시 규칙 템플릿으로 생성 (6장)

- 추가 표(V2, 사용자 승인 2026-10-31): `summary_call(vuln_id → vulnerability, called_at timestamptz, outcome GENERATED|DISCARDED|REFUSED|FAILED, model, PK(vuln_id, called_at))` — 하루 요약 상한을 호출 수로 세고, 버리거나 거절된 CVE 를 다시 부르지 않기 위해. FAILED 만 다음 실행에서 다시 고른다. 상위 20건 정렬은 index 와 같은 `trend_score desc nulls last, published_at desc nulls last, vuln_id`, 철회 제외
- 요약을 남길 때 제목이 "설명에서 만든 임시 제목"이면 what 첫 문장으로 바꾼다(GHSA 등 앞선 출처 제목은 덮지 않음, P3)

## 6. 배치 실행 흐름

```
1. NVD 증분   → vulnerability_raw upsert + vulnerability upsert (Rejected 제외)
               affected_product는 CPE configurations에서, reference_link(source=vendor)는 references tags("Vendor Advisory", "Patch")에서
2. KEV/EPSS   → is_kev / kev_due_date / kev_ransomware / epss_score / epss_percentile update
3. OSV        → affected_product(ecosystem, 버전 범위, fixed_version) upsert, title 보강
4. 뉴스 RSS   → reference_link(source=news) insert, mention_count = 최근 7일 창 집계
5. 점수       → update vulnerability set trend_score = ...  (SQL 한 방, 감쇠 포함)
6. daily_stat → 최근 14일 행 전부 재계산 (NVD가 과거 날짜로 소급 발행하므로 당일만 갱신하면 틀림)
7. 요약       → trend 상위 20건 중 summary 없는 것만 LLM 호출 후 insert (일 상한 20콜)
8. JSON 출력  → 쿼리 결과를 web/public/data/ 에 파일로 (내용 변경분만). rejected 건의 per-CVE 파일은 삭제
9. git push   → ProcessBuilder: git add -A && git commit -m "data: <시각>" && git push
```

### 실패 규칙

- 1~4단계 중 하나라도 실패하면 5단계 이후를 실행하지 않음. 반쪽 JSON을 push하지 않음
- 실패한 소스의 `sync_state.last_synced_at`은 전진시키지 않아 다음 실행에서 자동 재시도
- 7단계(요약) 실패는 치명적이지 않음 → 요약 없이 8·9단계 진행
- 사이트는 `meta.updatedAt`이 36시간을 넘으면 "데이터가 오래됐습니다" 배너를 띄움. 배치 실패를 방문자가 알 수 있는 유일한 수단

### 트렌드 점수

**MVP 점수식 (NVD + KEV + EPSS)**

```sql
update vulnerability set trend_score =
  (
      (is_kev::int * 50)
    + (kev_ransomware::int * 10)
    + (coalesce(epss_score, 0) * 30)
    + coalesce(cvss_score, 0)
  )
  * power(0.5, greatest(0,
      extract(epoch from now() - greatest(published_at,
                                           coalesce(kev_added_at::timestamptz, published_at),
                                           coalesce(last_mention_at, published_at)))
      / 86400) / 30.0)
where rejected = false;
```

구현 메모(사용자 확정 2026-10-31, WBS 3.1): ① `kev_added_at` 은 `::timestamp at time zone 'UTC'`(UTC 자정) — `::timestamptz` 는 DB 세션 시간대를 타서 KST 면 9시간 어긋남 ② `now()` 대신 실행 시각(Clock)을 넘김 ③ 기준 시각을 하나도 모르면 감쇠 없이(비율 1) ④ 랜섬웨어 10점은 `is_kev` 일 때만 ⑤ 정렬은 저장된 `trend_score`(numeric(8,2)), 화면 숫자는 소수 1자리로 반올림한 7항의 합(드물게 1점 차이 가능)

- 시간 감쇠: 기준 시각은 `max(published_at, kev_added_at, last_mention_at)` 중 최신. 30일마다 절반. 없으면 Log4Shell류(KEV, EPSS 0.9+, CVSS 10)가 영원히 1위
- KEV에 새로 등재된 오래된 CVE는 `kev_added_at` 때문에 다시 상위로 떠오름 (의도된 동작)

**2단계 점수식 (뉴스 RSS + PoC 추가)**

```sql
-- 괄호 안에 아래 두 항 추가, 감쇠 기준에 last_mention_at 포함
    + (least(mention_count, 5) * 5)     -- mention_count는 최근 7일 창
    + (least(poc_count, 3) * 5)
```

- 우선순위: KEV > EPSS > 뉴스 언급 > PoC > CVSS
- 운영하며 조정. `vulnerability_raw` 덕분에 NVD 재조회 없이 전체 재계산 가능
- JSON에는 항별 기여도를 `score_breakdown`으로 함께 출력 (7장)

### "왜 지금?" 규칙 템플릿 (LLM 아님)

| 조건 | 문구 |
|---|---|
| KEV + PoC | 실제 악용 확인, 공개 PoC 존재 |
| KEV + 랜섬웨어 | 랜섬웨어 캠페인에 사용 중 |
| KEV | CISA 악용 목록 등재 |
| EPSS percentile ≥ 0.95 | 30일 내 악용 확률 상위 N% |
| PoC만 | 공개 PoC 존재, 스캔 증가 가능 |
| 뉴스 언급 ≥ 3 | 최근 7일 보안 뉴스 N건 언급 |
| 그 외 | CVSS N.N, 최근 공개 |

### 한국어 요약 (Claude Opus 5.5, Anthropic 공식 Java SDK — 사용자 결정 2026-10-31. 원래 Spring AI)

- 거절(refusal) 대비 서버 쪽 대체 `fallbacks: "default"`(베타 server-side-fallback-2026-07-01) 사용, `summary.model` 에는 실제 응답 모델 기록. 출력 속 버전 숫자가 원문에 없으면 버림(코드 검사). SDK 3.3.0 이 키를 Bearer 로 보내는 것으로 관찰됨 → 실제 키 첫 실행에서 401 여부 확인(사용자 결정 2026-10-31)
- 모델 `claude-opus-5-5`, 호출은 `com.anthropic:anthropic-java`. 환경 변수 `SUMMARY_MODEL`·`SUMMARY_API_KEY`(비면 요약 단계만 건너뜀, 시작은 거부하지 않음). 응답 `{what, who, action}` 은 구조화 출력, effort 는 low 부터. refusal 이면 그 건만 요약 없이. 어림 비용 하루 약 $0.20(20건)
- 대상: 트렌드 상위 20건 중 `summary` 없는 것. 일 상한 20콜. 생성된 건은 재생성하지 않음
- 출력 구조: `{what, who, action}` 각 1문장. `action`은 "X 버전 이상으로 업데이트" 수준까지만. **명령어·스크립트·설정값 생성 금지**
- 프롬프트 가드: 입력은 NVD description + 영향 제품·버전 범위만. "원문에 없는 버전·제품·수치를 만들지 말 것" 명시. 모르면 "원문 참고"로 답하게 함
- 사이트 표기: "AI 생성 요약, 원문 확인 필요" 면책 + 원문 링크 병기

### 모듈 구성

```
vuln-trend/
  docker-compose.yml
  batch/                  Spring Boot 배치 (CommandLineRunner)
    collector/            NvdCollector, KevCollector, EpssCollector, OsvCollector, NewsRssCollector
    scorer/               TrendScorer (SQL), WhyNowTemplate
    summarizer/           Spring AI 요약
    exporter/             JsonExporter (변경분만 쓰기), GitPusher
    resources/product-alias.yml   CPE 정규화 초기 데이터
  web/                    Vite + React + TS
    public/data/          배치가 쓰는 JSON (push 대상)
```

`Collector` 인터페이스 하나 두고 소스별 구현 → 순회. 소스 추가가 쉬움.

## 7. JSON 파일 설계 (사실상 API)

```
web/public/data/
  meta.json                 updated_at, version, today{new, kev_new, critical, high_risk}, yesterday{같은 4개 — high_risk 는 null 가능, 그때 증감 숨김}
  stats.json                최근 14일 일별 {신규, Critical}, 최근 7일 심각도 분포(미평가 포함)
  index.json                트렌드 상위 100건 (UI는 20건 + "더 보기")
                            행: id, title, score, score_breakdown{kev, ransomware, epss, news, poc, cvss, decay},
                                why_now, is_kev, kev_ransomware, kev_due_date, poc_count, epss, epss_percentile, cvss, cvss_version,
                                severity, primary_product, summary_what(있으면)
  recent.json               최근 7일 신규
  search-index.json         전체 CVE id + 제목 + 제품명 (1년치 3~4MB, 검색창 포커스 시 lazy load)
  cve/CVE-2026-XXXXX.json   상세 1건씩 — 생성 범위 제한 (아래)
  products/chrome.json      제품별 목록 + 월별 추이
  ecosystems/packagist.json 생태계별 목록 (PHP/Python/JS/Java...)
```

원칙
- **필드 이름은 모든 파일에서 snake_case** (사용자 결정 2026-10-30). 배치는 출력 전용 Jackson 매퍼 하나(`SNAKE_CASE`)로 모든 파일을 출력하고(Spring 전역 설정은 NVD 응답 읽기를 깨므로 쓰지 않음, 사용자 확정 2026-10-31). 점수 구성 항은 소수 1자리(50.0) 그대로 쓰며, 프론트는 이름을 바꾸지 않고 그대로 쓴다. 상세 필드 정의는 `web/src/types/*.ts` 가 계약(G1)
- 메인이 읽는 `index.json`은 작게. 전체를 한 파일에 넣지 않음
- **per-CVE 파일 생성 범위**: index / recent / products / ecosystems 목록에 등장하는 건 + 점수 상위 N건만 (상한 수천 개). 그 외 CVE는 검색 결과에서 NVD 링크로 연결. 연 2~3만 파일을 전부 커밋하면 git 작업이 느려짐
- 내용이 같으면 파일을 건드리지 않아 git diff 최소화 (push 속도의 핵심). 감쇠 때문에 `trend_score`는 매 실행 바뀌므로 JSON에는 **정수로 반올림**해 출력, `score_breakdown` 항도 소수 1자리로 제한해 churn을 줄임
- `rejected=true` 건: 기존 `cve/*.json` 삭제, index/recent/products/ecosystems/search-index 전부에서 제외
- 캐시: GitHub Pages는 JSON에 캐시 헤더가 붙음. `meta.json`은 `cache: 'no-cache'`로 fetch, 나머지 파일은 `meta.version`을 `?v=`로 붙여 캐시 버스팅
- 레포 성장: 단일 레포 유지(Q1). 데이터 히스토리가 500MB를 넘으면 orphan 브랜치로 히스토리를 재생성. 1년 이상 지난 `cve/` 파일은 아카이브 JSON 하나로 병합

## 8. 노출 설계

### 메인 페이지 — "3초 안에 오늘 뭐가 위험한지"

위에서 아래로 숫자 → 추이 → 목록. 디자인 원본: Claude Design `VulnTrend Dashboard.dc.html` (수정 항목은 `vuln-trend-review.md` 3장)

```
┌─────────────────────────────────────────────────────────┐
│ VulnTrend                      마지막 갱신 10-30 09:00 (3시간 전) │
│ [검색: CVE-ID / 제품명]   필터: [전체 | KEV | Critical]        │
│ (36시간 초과 시) ⚠ 데이터가 오래됐습니다. 마지막 갱신 ...     │
├──────────┬──────────┬──────────┬──────────┤
│ 24h 신규 │ KEV 신규 │ Critical │ 고위험   │   ← meta.json (어제 대비 증감 표시)
│ 142 +9   │  3 +2    │  11 +4   │ 318 +5   │
├─────────────────────────┬───────────────────┤
│ 최근 14일 일별 신규 (막대, Critical 적층) │ 최근 7일 심각도 (도넛, 미평가 포함) │ ← stats.json
├─────────────────────────┴───────────────────┤
│ 지금 주목할 취약점   [소프트웨어별 | 점수순]  [전체|KEV|Critical]  ← index.json 상위 20 + 더 보기
│ ┌ 목록 (좌) ──────────────────┐ ┌ 선택 상세 패널 (우, sticky) ─┐
│ │ CVE-2026-41203 [KEV][랜섬웨어][Chrome] 기한 11-13 │ │ 제목 / 영향 제품·버전        │
│ │   V8 타입 혼동으로 원격 코드 실행 (요약 what)    │ │ 점수 구성 막대 (0 아닌 항만) │
│ │   점수 98 · EPSS 0.91 (상위 3%) · CVSS 8.8     │ │ 왜 지금? (규칙 템플릿)       │
│ │ ...                                            │ │ 조치: action 1~3줄 + 링크    │
│ └────────────────────────────┘ │ [상세 페이지 열기]          │
│                                └──────────────────────────┘
├─────────────────────────────────────────────────────────┤
│ 푸터: 데이터 출처(NVD/CISA KEV/FIRST EPSS/OSV) · NVD 비인증 문구 · CVE 상표 · 면책 │
└─────────────────────────────────────────────────────────┘
```

| 영역 | 보여주는 것 | 정의 | 출처 |
|---|---|---|---|
| KPI 1 "24시간 신규" | 최근 24시간 신규 CVE 수 + 어제 대비 | `published_at` 24시간 역산. KPI 4개만 롤링 창, 어제 값은 48~24시간 전 창 | `meta.json` |
| KPI 2 "KEV 신규" | 24시간 내 KEV 등재 수 | `kev_added_at` 24시간 이내 | `meta.json` |
| KPI 3 "Critical" | 24시간 신규 중 CVSS 9.0+ | | `meta.json` |
| KPI 4 "고위험" | 전체 중 EPSS 0.5 이상 건수 + 어제 대비 | 2단계에서 "PoC 공개"로 교체 | `meta.json` |
| 일별 신규 막대 | 최근 14일 추이, Critical 적층. x축은 `10/30` 형식 | `daily_stat` KST 달력일 기준. 롤링 창을 쓰면 하루 2회 실행 시 이웃 날과 창이 겹침 | `stats.json` |
| 심각도 도넛 | 최근 7일 신규 분포(ISO 주 아님), 미평가 범주 포함, 범례에 비율 | `daily_stat` 최근 7개 달력일 합 | `stats.json` |
| 트렌드 목록 | 상위 20건 + "더 보기". 보기 전환: 소프트웨어별(`primary_product` 묶음, 그룹은 최고 점수순) / 점수순 | | `index.json` |
| 목록 행 | CVE-ID, 배지(KEV/랜섬웨어/PoC/제품), KEV `dueDate`(KEV만), 요약 `what`(없으면 영어 원문 첫 문장), 점수·EPSS(percentile 병기)·CVSS | | `index.json` |
| 상세 패널 | 선택 시 `cve/CVE-xxx.json` lazy fetch. 영향 제품·버전(MVP는 NVD CPE 버전 범위, OSV 수집 후 `fixed_version` 추가), 점수 구성(0이 아닌 항만), 왜 지금?, 조치 `action` + 벤더 어드바이저리 링크(MVP는 NVD references의 Vendor Advisory/Patch 태그), "상세 페이지 열기" | 선택 시 URL도 `/cve/…`로 갱신 | `cve/*.json` |

MVP 필터는 **전체 / KEV / Critical** 하나의 세트. 상단 필터와 목록 옆 필터는 같은 상태를 공유

MVP에서 **넣지 않는 것**: 영향 자산 수, 조치 기한 SLA(KEV `dueDate`로 대체), 조치 절차 체크리스트·진행률·티켓(2단계 localStorage 개인 체크리스트로 재검토, Q2), 명령어 블록(영구 제외), "실시간 수집 중" 펄스·초 카운터, 생태계 필터(OSV 수집 후), PoC 필터·배지(PoC 탐지 후)

### 상세 페이지 `/cve/CVE-2026-XXXXX`

- 머리: CVE-ID, 배지, 점수 3종(트렌드/EPSS/CVSS) 큰 숫자, 영향 제품·버전 범위·수정 버전
- 본문: 한국어 요약(what / who / action, AI 생성 면책 + 원문 링크) → 왜 지금? → 점수 구성 → 원문 설명 → 참고 링크(벤더 어드바이저리, 뉴스, PoC)
- 하단: 같은 제품의 최근 취약점 5건 (2단계에서 "같은 패치로 함께 해결되는 CVE 묶음"으로 검토)
- 모바일에서는 메인의 패널 대신 이 페이지로 이동

### 제품별 `/product/chrome`, 생태계별 `/ecosystem/packagist`

- 메인과 같은 템플릿에 필터 고정 + 월별 추이 미니 차트
- 검색 유입용 페이지

### 반응형

- 데스크톱(≥ 1024px): 목록 + sticky 상세 패널 2열
- 모바일: 1열. 행 클릭 시 `/cve/` 페이지로 이동. 디자인에 모바일 레이아웃 1장 추가 필요

### 색상·접근성 규칙

- 심각도: Critical 빨강 / High 주황 / Medium 노랑 / Low 회색 / 미평가 연회색 — 고정
- KEV 배지: 빨강 하나만. 랜섬웨어 배지: KEV와 같은 계열. PoC 배지: 주황
- 그 외는 전부 회색 계열. 색을 더 쓰면 어수선해짐
- 색만으로 구분하지 않음: 심각도·KEV·PoC는 항상 텍스트 배지
- 막대·도넛 툴팁은 hover 외에 키보드 포커스로도 열림. 상세 패널 열림은 `aria-live`로 알림

### 푸터 고지 (필수)

- "This product uses the NVD API but is not endorsed or certified by the NVD."
- EPSS: FIRST 출처 표기(Jacobs et al., EPSS, FIRST.org) + 점수 생성 주체 Empirical Security 병기(first.org/epss 푸터 문구 기준, 2026-10-31 확인·사용자 승인). 데이터 주소 epss.empiricalsecurity.com
- CVE는 MITRE의 등록 상표
- 데이터 출처: NVD · CISA KEV · FIRST EPSS · OSV.dev / GitHub Advisory Database
- 면책: 정보 제공 목적, 정확성·완전성 보장 안 함. AI 요약은 원문 확인 필요
- 뉴스는 링크+제목만, 본문 재게시 금지

### 프론트 기술

- Vite + React + TypeScript, Tailwind, React Router, Recharts
- `vite.config.ts`의 `base`는 `VITE_BASE` 환경변수. 초기 `/vuln-trend/`, 도메인 연결 시 `/`
- GitHub Pages는 SPA 라우팅 미지원 → `404.html` → `index.html` 리다이렉트 트릭
- 배포는 **GitHub Actions 하나로 통일** (gh-pages 브랜치 수동 push 없음)
- OG 메타태그: 사이트 수준은 MVP. CVE별 정적 HTML(`cve/CVE-xxx/index.html`)에 `<title>`·description·OG를 넣는 SEO 보강과 sitemap.xml은 7단계
- 방문 분석 도구: 도입하지 않음 (Q3)

## 9. 로드맵

| 단계 | 내용 | 산출물 | 기간 |
|---|---|---|---|
| 0. 준비 | 레포 생성, Docker Postgres, NVD API 키 발급, Flyway V1, `product-alias.yml` 초기 50개 | `docker-compose.yml`, 스키마 | 1일 |
| 1. 수집 | NVD 증분 수집기 + `sync_state` 커서, Rejected 제외, CVSS 선택 규칙, 초기 백필(최근 1년) | `vulnerability`, `vulnerability_raw` | 3~4일 |
| 2. 보강 | KEV(dueDate, 랜섬웨어), EPSS(percentile). OSV, 뉴스 RSS는 공개 후 | `is_kev`, `epss_score` 등 | 2일 |
| 3. 점수 | `trend_score` MVP 식(감쇠 포함), `why_now` 템플릿, `daily_stat` 집계 | 점수 정렬 | 1~2일 |
| 4. 출력 | JSON 생성기(변경분만, per-CVE 범위 제한), 실패 시 push 금지, git push 자동화 | `web/public/data/*` | 2일 |
| 5. 프론트 | Vite React, 메인(패널 포함)·상세·검색, 차트, 모바일, 푸터 고지, stale 배너, Actions 배포 | GitHub Pages 공개 | 6~8일 |
| 6. 요약 | Spring AI로 상위 20건 한국어 요약 (what/who/action), 면책 표기 | `summary` | 2일 |
| 7. 다듬기 | CVE별 정적 HTML 메타태그, sitemap, 도메인 연결(`VITE_BASE=/`) | 검색 유입 | 2~3일 |

- 1~4단계가 끝나면 JSON이 나오므로 그 시점부터 프론트 병행 가능
- 6단계(요약)는 **MVP에 포함**(Q4). 일 20콜 이하라 비용 미미

### 첫 공개 범위 (MVP, 2~3주 목표)

- 데이터: NVD + KEV + EPSS
- 점수: KEV + 랜섬웨어 + EPSS + CVSS, 30일 반감 감쇠
- 화면: 메인(KPI 4, 차트 2, 목록 20 + 상세 패널) + 상세 + 검색 + 모바일 + 푸터 고지
- 요약: 상위 20건 한국어 (what/who/action)
- 공개 후 순서대로 추가: OSV·뉴스 RSS → PoC 탐지(KPI 4 교체) → 제품별/생태계별 페이지 → 체크리스트(localStorage) → SEO

## 10. 구현 가이드 (체크리스트)

1. 레포 `vuln-trend/` 생성, `batch/`, `web/` 두 디렉토리. `pgdata/`, `.env`는 gitignore
2. `docker compose up -d` → Flyway V1로 테이블 8개 생성 (PK는 `vuln_id`)
3. NVD API 키를 `.env`에 두고 `NvdCollector` 먼저. Rejected 제외, CVSS 우선순위 적용. 초기 백필은 120일 구간씩 나눠 1년치, 한 번만 실행
4. `KevCollector`(JSON 1개, dueDate·ransomware 포함), `EpssCollector`(CSV 전체 → vuln_id 조인, percentile 포함), `OsvCollector`(공개 후)
5. `NewsRssCollector`(공개 후): Rome으로 파싱, 본문에서 `CVE-\d{4}-\d+` 추출해 `reference_link` insert → `mention_count` 7일 창 집계
6. `TrendScorer`: 6장의 SQL 한 방(감쇠 포함). Java 루프로 돌리지 않음. `WhyNowTemplate`는 JSON 출력 시 적용
7. `JsonExporter`: 쿼리 → Jackson → 파일. 기존 파일과 비교해 같으면 skip. 점수는 정수 반올림. per-CVE는 목록 등장 건만, rejected 건 파일은 삭제. `meta.version` 갱신. `GitPusher`는 ProcessBuilder로 git 호출, 1~4단계 전부 성공 시만
8. `npm create vite@latest web -- --template react-ts`, Tailwind, React Router, Recharts. `VITE_BASE` 설정. `meta.json` no-cache fetch + `?v=` 캐시 버스팅
9. GitHub Pages: Actions로 `web/` 빌드·배포 (Source: GitHub Actions). JSON은 `public/data/`에 있으니 빌드에 자동 포함
10. Spring AI 요약(상위 20건, what/who/action, 명령어 금지 가드, 생성된 건 재생성 안 함)
11. 푸터 고지 4종 + 면책, stale 배너, 모바일 레이아웃 확인 후 공개
12. (7단계) CVE별 정적 HTML 메타태그 생성으로 SEO

## 11. 운영 가이드

- 배치는 하루 1~2회. 작업 스케줄러에 걸거나 아침에 수동 실행
- 배치 실행 시간 목표 5분 이내. 병목은 git push(파일 수) → 변경분만 쓰는 것이 중요
- 배치가 실패하면 push되지 않고, 36시간이 지나면 사이트에 stale 배너가 뜸. 배너가 보이면 로컬 배치 로그 확인
- 점수 가중치 변경 시 `vulnerability_raw`로 전체 재계산 (NVD 재조회 불필요)
- 공개 후 첫 2주는 메인 상위 10건을 매일 직접 보며 "진짜 지금 중요한가" 확인하고 가중치·감쇠 반감기 조정. 같은 루틴에서 미매핑 제품명을 `product-alias.yml`에 추가. 이 튜닝이 사이트 품질의 전부
- 방문 분석 도구는 쓰지 않으므로(Q3) 튜닝 피드백은 운영자 육안 확인에 의존. 필요해지면 재검토
- LLM 요약: 일 20콜 상한, 모델명을 `summary.model`에 기록. 프롬프트에 "원문에 없는 버전·제품·수치 생성 금지", 명령어 생성 금지. 사이트에 AI 생성 면책 + 원문 링크 병기
- 레포 크기 관리: per-CVE 파일은 목록 등장 건만 생성. 500MB 넘으면 orphan 브랜치로 히스토리 재생성, 1년 이상 지난 `cve/` 파일은 아카이브 병합
- 뉴스 기사는 링크+제목만 저장·노출. 본문 재게시 금지
- NVD/KEV/EPSS/OSV는 공개 데이터. 단 NVD 비인증 문구와 EPSS 출처 표기는 의무 (8장 푸터)

### GitHub Actions로 옮길 때 (선택)

- 로컬 PC 없이 자동 갱신하려면 Actions 스케줄(`cron: '0 */6 * * *'`)로 배치 실행 후 commit
- NVD 키, LLM 키는 Secrets
- 단, Postgres 상태를 Actions에서 유지할 수 없으므로 (a) Postgres 덤프를 레포에 커밋하거나 (b) 매 실행 전체 재조회 중 선택 필요. 로컬 운영이 안정된 뒤 결정

## 12. 결정 사항 요약

| 항목 | 결정 |
|---|---|
| 대상 | 전 소프트웨어, 모든 사용자 (공개 서비스, 로그인 없음) |
| 제공 형태 | 웹사이트만 |
| 호스팅 | GitHub Pages (정적), 로컬 배치 → push. 배포는 Actions 단일 경로 |
| 레포 | 코드 + 데이터 단일 레포 (Q1). per-CVE 파일 상한 + orphan 재생성 |
| 백엔드 | Spring Boot 배치 (웹서버 없음), Java 17. 요약은 Anthropic Java SDK + Claude Opus 5.5 |
| DB | Docker PostgreSQL 16 (로컬 전용, JSONB 원본 보관). PK `vuln_id` |
| 프론트 | React (Vite + TS), Thymeleaf 아님. `VITE_BASE` 환경변수 |
| 1차 소스 | NVD + KEV(dueDate, 랜섬웨어) + EPSS(percentile) |
| 2차 소스 | OSV/GHSA, 뉴스 RSS, 벤더 어드바이저리, GitHub PoC |
| 점수 | MVP: KEV + 랜섬웨어 + EPSS + CVSS, 30일 반감 감쇠. 2단계: 뉴스(7일 창) + PoC 추가 |
| KPI | 24시간 신규 / KEV 신규 / Critical / 고위험(EPSS 0.5+). 전일 대비 증감 표시 |
| 목록 | 상위 20건 + 더 보기. 소프트웨어별 / 점수순 전환 |
| 상세 | 데스크톱 sticky 패널 + `/cve/` 페이지 둘 다. 모바일은 페이지 |
| 요약 | MVP 포함 (Q4). 상위 20건, what/who/action, 명령어 생성 금지 |
| 조치 체크리스트 | 2단계 localStorage (Q2). MVP 제외 |
| 분석 도구 | 도입 안 함 (Q3) |
| 고지 | 푸터에 NVD 비인증 문구, EPSS 출처, CVE 상표, 면책 |
| 차별점 | 감쇠 포함 트렌드 점수 선별 + 한국어 요약 + 차트 중심 노출 |
