# VulnTrend — PM 실행 체크리스트 (WBS)

작성일: 2026-10-30
기준 문서: `vuln-trend-plan.md` v2, `vuln-trend-review.md`
역할: 이 파일은 PM 판이 관리하는 **단일 진행 현황판**. 구현 판은 태스크를 끝낼 때 체크박스를 바꾸고, 막히면 §9 블로커에 적는다.

## 사용 규칙

- 깊이: `Phase(D1)` → `작업 묶음(D2)` → `태스크(D3)`. 체크는 D3에서만 한다. D2는 하위 D3가 전부 끝나면 완료, D1은 DoD를 통과하면 완료
- 표기: `- [ ]` 미착수 · `- [ ] ⏳` 진행 중 · `- [x]` 완료 · `- [ ] ⛔` 블로커(§9에 사유)
- 태스크 뒤 `→ 문서 N장`은 기획서 참조 위치. 세부 사양은 이 파일에 복사하지 않고 기획서를 본다
- 순서 변경·범위 추가는 이 파일을 먼저 고치고 구현한다. 기획서와 충돌하면 기획서를 고치고 §10 변경 이력에 남긴다
- 상태 집계(§1)는 Phase 하나를 닫을 때마다 갱신

---

## 1. 현황판

| Phase | 내용 | 예상 | 태스크 수 | 완료 | 상태 |
|---|---|---|---|---|---|
| 0 | 준비 | 1일 | 18 | 15 | 진행 중 |
| 1 | NVD 수집 | 3~4일 | 16 | 14 | 진행 중 |
| 2 | KEV·EPSS 보강 | 2일 | 10 | 10 | 완료(DoD 실측은 NVD 키 대기) |
| 3 | 점수·통계 | 1~2일 | 10 | 10 | 구현 완료(DoD 10초 실측은 NVD 키 대기) |
| 4 | JSON 출력·push | 2일 | 15 | 14 | 구현 완료(5분 실측·M1 은 키·원격 대기) |
| 5 | 프론트 | 6~8일 | 36 | 34 | 원격 레포 대기(2건) |
| 6 | 한국어 요약 | 2일 | 8 | 8 | 구현 완료(실요약 검수는 키 대기·Gate G) |
| G | 공개 전 점검 | 0.5일 | 10 | 0 | 미착수 |
| 7 | 다듬기(공개 후) | 2~3일 | 5 | 0 | 미착수 |

마일스톤
- **M1 JSON 산출**: Phase 0~4 완료. 이 시점부터 Phase 5·6 병행
- **M2 MVP 공개**: Phase 5·6 + G 통과. 목표 2~3주
- **M3 검색 유입**: Phase 7 완료

의존 관계
```
0 → 1 → 2 → 3 → 4 ──→ M1 ──→ 5 ─┐
                                  ├→ G → M2 → 7
                 (4의 JSON 스키마 확정 후) 6 ─┘
```
- Phase 5는 M1 전에도 **4.2~4.7의 JSON 필드 정의만 확정되면** 목업 JSON으로 착수 가능
- Phase 6은 Phase 3(상위 20건 산출) 뒤면 언제든 가능. 5와 병행 권장

---

## 2. Phase 0 — 준비 (1일)

### 0.1 레포·디렉토리
- [x] `vuln-trend/` git init, `batch/`·`web/` 두 디렉토리 생성 → 6장 모듈 구성
- [x] `.gitignore`: `pgdata/`, `.env`, `batch/build/`, `web/node_modules/`, `web/dist/`
- [ ] GitHub 원격 레포 생성(코드+데이터 단일 레포, Q1) → 2장

### 0.2 로컬 DB
- [x] `docker-compose.yml` 작성 (postgres:16, vuln/vuln/vuln, 5432, `./pgdata` 볼륨) → 4장
- [x] `docker compose up -d` 후 psql 접속 확인

### 0.3 Spring Boot 배치 골격
- [x] Gradle 프로젝트, Java 17, `spring.main.web-application-type=none` → 4장
- [x] 의존성: postgresql 드라이버, flyway, JdbcTemplate(또는 MyBatis), Jackson, dotenv 로딩
- [x] `application.properties` 5개 키 (datasource, flyway, `nvd.api-key`, `output.dir`, `summary.daily-limit`)
- [x] `CommandLineRunner` 진입점 + `Collector` 인터페이스 + 순회 러너 뼈대 (아직 구현체 없음)

### 0.4 Flyway V1
- [x] 5장 스키마 그대로 테이블 8개 (`vulnerability`, `vulnerability_raw`, `affected_product`, `product_alias`, `reference_link`, `summary`, `daily_stat`, `sync_state`) + 인덱스 4개
- [x] `summary` 테이블은 what/who/action 컬럼 구조 확인 (review P20)
- [x] `./gradlew bootRun` 시 마이그레이션 성공

### 0.5 외부 키·초기 데이터
- [ ] NVD API 키 발급, `.env`에 `NVD_API_KEY` → 3장 NVD 주의사항
- [x] `batch/resources/product-alias.yml` 초기 50개(제품 50·별칭 258) 작성, 기동 시 `product_alias` 를 원본과 같게 교체(사용자 확정 2026-10-31) → 5장 메모 P11

### 0.6 코드 컨벤션 (2026-10-30 추가)
- [x] clarity-api 하네스에서 코드 규칙만 가져오기: `vuln-trend-dev-conventions`·`domain-test-writing` 스킬, `CLAUDE.md` 작업 규칙
- [x] 검사 도구 `scripts/check-all.sh` + `check-records.sh`·`check-exceptions.sh`·`check-conventions.py`·`check-display-names.sh` + 회귀 시험(57건 일치)
- [ ] 패키지 루트(`com.vulntrend.batch` 가정)·기능 하위 패키지 구조 확정 → 0.3 골격과 함께 (결정 대기)
- [x] `check-all.sh` 가 `batch/gradlew` 를 찾아 빌드·커버리지까지 돌아가는지 확인 → 0.3 뒤

**DoD**: 빈 DB에 bootRun 한 번으로 테이블 8개 + product_alias 50행. 레포 push 됨. `bash scripts/check-all.sh` 종료 코드 0.

---

## 3. Phase 1 — NVD 수집 (3~4일)

### 1.1 NVD API 클라이언트
- [x] REST 호출: `lastModStartDate/EndDate`, `resultsPerPage`, `startIndex` 페이지네이션 → 3장
- [x] rate limit 준수 (키 있을 때 30초 50회) + 429/5xx 재시도
- [x] 요청 구간 120일 상한 자동 분할

### 1.2 파싱 규칙
- [x] `vulnStatus=Rejected` 신규 건 제외, 기존 건은 `rejected=true` → 3장 수집 규칙
- [x] CVSS 선택: NVD v3.1 > CNA v3.1 > v3.0(사용자 결정 2026-10-31) > v4, `cvss_version` 기록, 없으면 `UNRATED` (S2)
- [x] `title` 임시값: description 첫 문장 80자 (GHSA·요약은 나중 단계에서 덮어씀, P3)
- [x] CPE configurations → `affected_product` (vendor, product, `vulnerable_range`는 versionStart/End로)
- [x] references 태그 Vendor Advisory / Patch → `reference_link(source=vendor)` (R4)
- [x] `published_at`, `last_modified_at` UTC 저장

### 1.3 저장
- [x] `vulnerability_raw` upsert (source=`nvd`, payload JSONB)
- [x] `vulnerability` upsert: JdbcTemplate batch + `on conflict (vuln_id) do update` (JPA saveAll 금지) → 5장 메모
- [x] `affected_product`, `reference_link` upsert (PK 충돌 무시)

### 1.4 `sync_state` 커서
- [x] 성공한 실행에서만 `last_synced_at` 전진, 실패 시 `status=FAIL` 기록 → 6장 실패 규칙
- [x] 같은 구간 두 번 실행해도 중복·누락 없음 (멱등 확인)

### 1.5 초기 백필
- [ ] ⛔ 최근 1년을 120일 구간으로 나눠 1회성 실행 — 실행 방식(`--backfill-days=365`)은 완료·커밋, 실제 실행은 NVD 키 대기(§12 R1) (프로파일 또는 CLI 인자)
- [ ] ⛔ 적재 건수·Rejected 제외 건수·UNRATED 비율 로그로 확인 — 로그 줄은 스텁으로 확인, 실측은 키 대기

**DoD**: 1년치 적재 완료, 증분 실행 2회 연속 성공, `vulnerability_raw`에서 임의 CVE의 원본 JSON이 조회됨.

---

## 4. Phase 2 — KEV·EPSS 보강 (2일)

### 2.1 KevCollector
- [x] CISA KEV JSON 1개 다운로드, `vulnerability_raw(source=kev)` 보관 → 3장
- [x] `is_kev`, `kev_added_at`, `kev_due_date`, `kev_ransomware` update (D2, P5)
- [x] KEV에 있으나 `vulnerability`에 없는 CVE는 로그만 (NVD 다음 증분에서 들어옴)

### 2.2 EpssCollector
- [x] FIRST EPSS CSV 전체 다운로드(현재 주소 epss.empiricalsecurity.com, 2026-10-31 확인)(gz 해제)
- [x] `epss_score`, `epss_percentile` 둘 다 update, `vuln_id` 조인 (P10)

### 2.3 `primary_product` 선정
- [x] 규칙 구현: KEV vendorProject/product > (OSV, 2단계) > CPE 첫 항목 → 5장 메모 P11
- [x] 셋 모두 `product_alias`로 정규화, 미매핑은 CPE product 원문
- [x] `affected_product.slug` 채우기

### 2.4 실패 규칙 통합
- [x] 러너: 1~4단계(NVD/KEV/EPSS/뉴스) 중 하나라도 실패하면 5단계 이후 실행 안 함 → 6장
- [x] 각 소스별 `sync_state` 독립 (한 소스 실패가 다른 소스 커서를 막지 않음)

**DoD**: KEV 전 건에 `is_kev=true`, EPSS 커버리지 90% 이상, 러너가 KEV URL 고의 오류 시 점수 단계로 넘어가지 않음.

---

## 5. Phase 3 — 점수·통계 (1~2일)

### 3.1 TrendScorer
- [x] 6장 MVP SQL 한 방 (KEV 50 + 랜섬 10 + EPSS×30 + CVSS) × 30일 반감 감쇠. Java 루프 금지
- [x] 감쇠 기준 시각 `greatest(published_at, kev_added_at, last_mention_at)` 확인 (P1)
- [x] `score_breakdown` 항별 기여도 쿼리 (kev, ransomware, epss, news=0, poc=0, cvss, decay) — 출력 단계에서 사용 (D9)

### 3.2 WhyNowTemplate
- [x] 6장 규칙 표 7줄 구현, 우선순위 위→아래 (D10)
- [x] EPSS percentile 상위 N% 문구 계산

### 3.3 daily_stat
- [x] `published_at` KST 달력일 기준, **최근 14일 행 전부 재계산** (R2) → 6장 6번
- [x] `high_risk_count`는 그 날 기준 전체 스냅샷

### 3.4 KPI 집계 쿼리
- [x] 24시간 롤링 창 4개: 신규 / KEV 신규(`kev_added_at`) / Critical(신규 중 9.0+) / 고위험(전체 EPSS 0.5+) → 8장 표
- [x] 어제 값 = 48~24시간 전 창 (D11). 단 어제 고위험은 daily_stat 어제 값(EPSS 덮어쓰기 때문), 비면 null·증감 숨김(사용자 확정)

### 3.5 검증
- [x] 상위 20건 육안 확인: Log4Shell류 오래된 유명 건이 상위에 없음, 최근 KEV 등재 건이 상위에 있음 — 실데이터 표본 1,771건(TrendRankingSampleTest). 상위 100건 중 99건 KEV → 가중치는 1년치 적재 뒤 Gate G 에서 재검토(사용자 결정)

**DoD**: 점수 SQL 1회 실행 10초 이내, 상위 20건이 "지금 중요한가" 기준으로 납득됨.

---

## 6. Phase 4 — JSON 출력·push (2일)

### 4.1 JsonExporter 골격
- [x] 쿼리 → Jackson → 파일. 기존 파일과 바이트 비교해 같으면 쓰지 않음 → 7장 원칙
- [x] `trend_score` 정수 반올림, `score_breakdown` 소수 1자리 (R6)

### 4.2 파일별 생성
- [x] `meta.json`: `updatedAt`, `version`, `today{4}`, `yesterday{4}` (D11, P16)
- [x] `stats.json`: 최근 14일 일별 {신규, Critical}, 최근 7일 심각도 분포(미평가 포함)
- [x] `index.json`: 상위 100건, 행 필드 전부 (id, title, score, score_breakdown, why_now, is_kev, kev_due_date, poc_count, epss, epss_percentile, cvss, cvss_version, severity, primary_product, summary_what)
- [x] `recent.json`: 최근 7일 신규 중 트렌드 상위 100건(사용자 결정)
- [x] `search-index.json`: 전체 CVE id + 제목 + 제품명 (1년치) — 크기(예상 25~30MB, Gate G 4MB)는 1년치 적재 뒤 실측으로 압축·기간 결정(사용자 결정)
- [x] `cve/CVE-*.json`: 생성 범위(index ∪ recent ∪ 점수 상위 1000, 범위 밖은 삭제 — 사용자 승인) = index/recent 등장 건 + 점수 상위 N건 (상한 수천). 영향 제품·버전, 점수 구성, 원문, 참고 링크 포함 (P6)
- [x] `rejected=true` 건: 기존 `cve/*.json` 삭제, 모든 목록·search-index 제외 (R5)
- [x] `products/`, `ecosystems/`는 **2단계** (§8 백로그). MVP는 생성 안 함

### 4.3 GitPusher
- [x] ProcessBuilder: 데이터 폴더만 `git add -A -- <data>` → `git commit -m "data: <한국 시각>" -- <data>` → `git push` (코드 변경 섞이지 않음) → 6장 9번
- [x] 1~4단계 전부 성공 시만 실행. 변경 파일 0이면 commit 생략
- [x] 실패한 실행은 프로세스 종료 코드 1 (cron·Actions 가 실패를 알도록, 사용자 결정 2026-10-31)

### 4.4 파이프라인 검증
- [ ] ⛔ 전체 흐름 end-to-end 1회: 실행 시간 5분 이내 — 흐름은 DailyRunEndToEndTest 로 확인(스텁·임시 원격), 5분 실측은 NVD 키·원격 대기 → 11장
- [x] 두 번째 실행에서 변경 없는 파일의 git diff가 0임을 확인 — DailyRunEndToEndTest("0 written, 7 unchanged", 커밋 0)

**DoD (= M1)**: `web/public/data/` 7종 파일 생성, 레포에 데이터 커밋 1회 이상. 프론트 착수 가능.

---

## 7. Phase 5 — 프론트 (6~8일)

### 5.1 프로젝트 셋업
- [x] `npm create vite@latest web -- --template react-ts`, Tailwind, React Router, Recharts → 8장 프론트 기술
- [x] `vite.config.ts` `base = VITE_BASE` (초기 `/vuln-trend/`) (P8)
- [x] `404.html → index.html` SPA 리다이렉트 트릭
- [x] 타입 정의: meta / stats / index 행 / cve 상세 / search-index (4.2 필드와 1:1)

### 5.2 데이터 레이어
- [x] `meta.json` `cache: 'no-cache'` fetch, 나머지는 `?v=meta.version` 캐시 버스팅 (P16)
- [x] 로딩·오류 상태 컴포넌트

### 5.3 헤더
- [x] 로고 + "마지막 갱신 MM-DD HH:mm (N시간 전)" KST 고정 표기, 펄스·초 카운터 없음 (D5)
- [x] `updatedAt` 36시간 초과 시 "데이터가 오래됐습니다" 배너
- [x] 검색창 (5.9와 연결)
- [x] 필터 [전체 | KEV | Critical] — 목록 옆 필터와 상태 공유 (R1)

### 5.4 KPI 4
- [x] 24h 신규 / KEV 신규 / Critical / 고위험(EPSS 0.5+) + 어제 대비 증감 (D12)

### 5.5 차트
- [x] 최근 14일 일별 신규 막대, Critical 적층, x축 `10/30` 형식 (S1)
- [x] 최근 7일 심각도 도넛, 미평가 범주, 범례에 비율 (P14)
- [x] 툴팁 hover + 키보드 포커스 (S3)

### 5.6 트렌드 목록
- [x] 상위 20건 + "더 보기" (index.json 100건 내에서) (D15)
- [x] 행: CVE-ID, 배지(KEV/랜섬웨어/제품), KEV `dueDate`(KEV만), `summary_what` 없으면 영어 원문 첫 문장, 점수·EPSS "0.91 (상위 3%)"·CVSS
- [x] 보기 전환: 소프트웨어별(`primary_product` 묶음, 그룹은 최고 점수순) / 점수순 (D7)
- [x] 필터 전체/KEV/Critical 적용

### 5.7 상세 패널 (데스크톱 ≥1024px)
- [x] 행 선택 시 `cve/CVE-xxx.json` lazy fetch, URL `/cve/…`로 갱신 (D8)
- [x] 영향 제품·버전 (MVP는 NVD CPE 범위), 점수 구성 막대(0 아닌 항만), 왜 지금?, 조치 `action` 1~3줄 + 벤더 링크
- [x] 하단 "상세 페이지 열기" 링크
- [x] 패널 열림 `aria-live` 알림 (S3)

### 5.8 상세 페이지 `/cve/CVE-xxx`
- [x] 머리: CVE-ID, 배지, 점수 3종 큰 숫자, 영향 제품·버전
- [x] 본문: 한국어 요약(what/who/action + AI 면책 + 원문 링크) → 왜 지금? → 점수 구성 → 원문 설명 → 참고 링크
- [x] `cve/*.json` 없는 CVE는 NVD 링크로 안내 (P6)
- [x] 하단 "같은 제품 최근 5건"은 2단계 (P21) — 자리만 비워둠

### 5.9 검색
- [x] 검색창 포커스 시 `search-index.json` lazy load (P19) — 드롭다운만, 메인 목록은 거르지 않음(사용자 결정 2026-10-31)
- [x] CVE-ID / 제품명 클라이언트 필터, 결과 클릭 → 상세

### 5.10 모바일
- [x] 1열 레이아웃, 행 클릭 시 `/cve/` 페이지 이동 (패널 없음)
- [x] 디자인 모바일 레이아웃 1장 확보 — 사용자 결정으로 대신: 데스크톱 디자인 토큰 + 기획서 규칙(2026-10-31) (review 3장 7번)

### 5.11 푸터
- [x] NVD 비인증 문구, EPSS FIRST 출처, CVE 상표, 데이터 출처 4종, 면책(AI 요약 포함) → 8장 푸터 고지 (D14)

### 5.12 메타·접근성
- [x] 사이트 수준 OG 태그 (P23)
- [x] 색상 규칙 점검: 심각도 5색 고정, KEV 빨강, 나머지 회색. 색만으로 구분하지 않음 → 8장 색상·접근성

### 5.13 배포
- [x] GitHub Actions 워크플로: `web/` 빌드 → Pages 배포 (Source: GitHub Actions) (P17) — `.github/workflows/pages.yml`. actionlint 는 사용자가 직접 실행
- [ ] ⛔ 데이터 커밋 push 시에도 Actions가 재배포되는지 확인 — GitHub 원격 레포 대기
- [ ] ⛔ 첫 배포 URL 접속 확인 — GitHub 원격 레포 대기

**DoD**: 실제 JSON으로 메인·상세·검색이 데스크톱·모바일에서 동작, Actions 배포 녹색.

---

## 8. Phase 6 — 한국어 요약 (2일, MVP 포함 Q4)

### 6.1 설정
- [x] (Spring AI → 6.2 에서 Anthropic Java SDK 로 교체, 사용자 결정) 의존성, 모델·키 `.env`, `summary.daily-limit=20` → 6장 한국어 요약
- [x] 사용 모델명을 `summary.model`에 기록 (P9)

### 6.2 프롬프트
- [x] 입력: NVD description + 영향 제품·버전 범위만
- [x] 출력 `{what, who, action}` 각 1문장 JSON. `action`은 "X 이상으로 업데이트"까지만
- [x] 가드: "원문에 없는 버전·제품·수치 생성 금지", "명령어·스크립트·설정값 금지", 모르면 "원문 참고" (D4, P9)

### 6.3 실행
- [x] 대상: 상위 20건 중 `summary` 없는 것만, 생성 건 재생성 안 함, 일 상한 20콜
- [x] 7단계 실패는 비치명 → 요약 없이 8·9단계 진행 → 6장 실패 규칙
- [x] `title` 규칙에 `what` 첫 줄 반영 (P3)

**DoD**: 상위 20건에 한국어 요약이 붙고 사이트 목록·상세에 면책과 함께 보임. 하루 호출 20회 초과 안 함.

---

## 9. Gate G — 공개 전 점검 (M2 Go/No-go)

- [ ] 배치 3일 연속 성공 (로그·`sync_state` 확인)
- [ ] 상위 10건을 매일 육안 확인해 가중치 1회 이상 조정 → 11장
- [ ] stale 배너: `meta.updatedAt`을 임의로 과거로 바꿔 배너 노출 확인
- [ ] 푸터 고지 5종 전부 노출
- [ ] 모바일 실기기에서 메인 → 상세 이동 확인
- [ ] 캐시: 데이터 push 후 새로고침 시 새 `version` 반영
- [ ] `search-index.json` 크기 4MB 이하, 포커스 전에는 로드되지 않음
- [ ] 한국어 요약 20건에 명령어·없는 버전 생성 사례 0건 (수동 검수)
- [ ] 레포 크기 확인, 커밋당 변경 파일 수 기록 (성장 추세 기준점)
- [ ] 미매핑 `primary_product` 상위 노출 건을 `product-alias.yml`에 추가

**통과 시 M2 공개.**

---

## 10. Phase 7 — 다듬기 (공개 후, 2~3일)

- [ ] CVE별 정적 HTML (`cve/CVE-xxx/index.html`) `<title>`·description·OG 생성 → 8장
- [ ] `sitemap.xml` 생성
- [ ] 도메인 연결, `VITE_BASE=/` 전환
- [ ] 공개 2주 튜닝 루틴 결과 반영 (가중치·반감기·alias) → 11장
- [ ] 레포 500MB 접근 시 orphan 브랜치 재생성 절차 문서화

---

## 11. 2단계 백로그 (공개 후 순서대로)

| 순서 | 항목 | 선행 조건 | 기획서 |
|---|---|---|---|
| B1 | `OsvCollector`: ecosystem, 버전 범위, `fixed_version`, GHSA summary → title | M2 | 3장, 6장 3번 |
| B2 | `NewsRssCollector`: Rome 파싱, CVE 정규식, `reference_link(news)`, `mention_count` 7일 창 | M2 | 6장 4번, P15 |
| B3 | 2단계 점수식 (뉴스 ≤5×5, PoC ≤3×5) + `why_now` 뉴스 줄 활성화 | B2 | 6장 |
| B4 | GitHub PoC 탐지 → `poc_count`, PoC 배지, KPI 4 "고위험" → "PoC 공개" 교체 | M2 | 3장, D12 |
| B5 | `products/*.json`, `ecosystems/*.json` + `/product/`, `/ecosystem/` 페이지, 생태계 필터 | B1 | 7장, 8장 |
| B6 | localStorage 개인 체크리스트 (Q2, 디자인 자산 재사용) | M2 | D3 |
| B7 | 벤더 어드바이저리 수집 + "같은 패치로 함께 해결되는 CVE 묶음" | M2 | 3장, P21 |
| B8 | 1년 지난 `cve/` 파일 아카이브 병합 | 운영 1년 | 7장 |
| B9 | 배치를 GitHub Actions로 이전 (Postgres 덤프 vs 전체 재조회 결정) | 로컬 운영 안정 후 | 11장 |

---

## 12. 블로커·리스크

| # | 내용 | 영향 Phase | 상태 | 대응 |
|---|---|---|---|---|
| R1 | NVD API 키 발급 지연 / rate limit 429 | 1 | 열림 — 1.5 실측·Phase 1 DoD 가 이것에 막힘 | 키 발급을 Phase 0 첫날 처리. 429 시 백오프 |
| R2 | 초기 백필 1년치 소요 시간 (키 있어도 수만 건) | 1 | 열림 | 1회성이므로 야간 실행. 진행 로그 필수 |
| R3 | git push 시간이 파일 수에 비례 | 4 | 열림 | 변경분만 쓰기(4.1) + per-CVE 상한(4.2)으로 통제. 4.4에서 측정 |
| R4 | LLM 요약 품질 (없는 버전 생성) | 6 | 열림 | G 게이트에서 20건 수동 검수 |
| R5 | 디자인 모바일 레이아웃 미확보 | 5 | 닫힘 — 기획서 규칙으로 진행(사용자 결정) | 5.10 착수 전 디자인 판에 요청 |
| R7 | KEV vendorProject/product 가 자유 표기라 CPE 기준 `product_alias` 와 바로 맞지 않음 | 0.5, 2.3 | 닫힘 | 혼합안 확정(2026-10-31): yml 에 KEV 원문 별칭 + 같은 정규화, 열 추가 없음. 기획서 5장 메모 반영 |
| R8 | 1년치 search-index.json 이 25~30MB 예상(기획 3~4MB, Gate G 4MB) | 4, G | 열림 | 1년치 적재 뒤 실측 → 압축 쓰기·기간 축소(최근 N개월)·필드 배열화 중 결정 |

새 블로커는 여기에 행 추가. 해결되면 상태를 `닫힘`으로.

---

## 13. 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-10-30 | 초판. 기획서 v2 로드맵 0~7 + 백로그 9건 | — |
| 2026-10-30 | Phase 0 에 0.6 코드 컨벤션 4건 추가(2건 완료). 태스크 14→18 | 개발 착수 전 코드 규칙·검사 도구 필요(clarity-api 하네스에서 이식) |
| 2026-10-30 | 0.1~0.4(원격 레포 제외)·0.6-4·5.1 체크. 기획서 7장 snake_case 통일·`kev_ransomware` 추가, 4장 `DB_PORT` | G0 검증 통과, 프론트 5.1 사용자 승인 |
| 2026-10-31 | 1.1·5.2 체크. R7 닫힘(KEV 별칭 혼합안), 0.5 "50개"=제품 50개·Windows Server 합침 | 사용자 승인·확정 |
| 2026-10-31 | 0.5·1.2·5.3·5.4 체크. CVSS 3.0 을 3.1 다음 순위로, 0.0 은 LOW, product_alias 는 통째 교체 | 사용자 승인·결정 |
| 2026-10-31 | 1.3 체크. 수집 순서 NVD→KEV→EPSS→OSV→NEWS 고정(B 요청, A 반영) | 사용자 승인 |
| 2026-10-31 | 2.1·5.5 체크. KEV: 목록에서 내려간 CVE 표시 해제, 빈 목록이면 실패(사용자 확정) | 사용자 승인 |
| 2026-10-31 | 1.4·2.2·5.6·5.7 체크. EPSS: 점수 없는 CVE 는 값 없이 두고 빈 파일은 실패, KEV·EPSS 공통 내려받기(사용자 확정) | 사용자 승인 |
| 2026-10-31 | 5.8 체크. 1.5 는 실행 방식만 완료, 실측 2항목 ⛔(NVD 키 대기) | 사용자 승인 |
| 2026-10-31 | 1.5 실행 방식 커밋(3ced104), 5.8 커밋(a7f271e) | — |
| 2026-10-31 | 2.3 체크. 대표 제품 순위에 KEV 원문(4순위)·KEV 미매핑 시 CPE 표준 이름(사용자 확정). 5.9 는 드롭다운만 | 사용자 결정 |
| 2026-10-31 | 2.4·3.1·5.9·5.10 체크, Phase 2 구현 완료. R5 닫힘. 4.3 에 실패 시 종료 코드 1 추가(15태스크). 기획서 6장 점수 SQL 구현 메모 | 사용자 승인·결정 |
| 2026-10-31 | 3.2·5.11 체크, 러너 점수 단계 연결. EPSS 출처에 Empirical Security 병기 | 사용자 승인 |
| 2026-10-31 | 3.3·6.1 체크. 요약 모델 Claude Opus 5.5 + Anthropic Java SDK(Spring AI 대신). 고위험 스냅숏·어제 고위험 규칙 확정 | 사용자 결정 |
| 2026-10-31 | 3.4·5.12 체크. 5.12 에 대비 AA 토큰·차트 지연 로드. G1: yesterday.high_risk null 허용 | 사용자 승인 |
| 2026-10-31 | 5.13 워크플로 체크. 실제 배포 확인 2건 ⛔ 원격 레포 대기 | 사용자 승인 |
| 2026-10-31 | 3.5·6.2 체크, Phase 3 구현 완료. 요약에 서버 쪽 대체(fallbacks)·출력 버전 검사 추가(6.3, 사용자 결정). Gate G 화면 점검 스크립트 커밋 | 사용자 승인·결정 |
| 2026-10-31 | 4.1 체크. 출력 전용 SNAKE_CASE 매퍼, 점수 구성 소수 1자리 | 사용자 승인 |
| 2026-10-31 | 6.3 체크, Phase 6 구현 완료. summary_call 표(V2) 추가. 4.3 GitPusher 를 A 로 재배정 | 사용자 승인, 부하 조정 |
| 2026-10-31 | recent.json 범위 = 최근 7일 중 트렌드 상위 100건 | 사용자 결정 |
| 2026-10-31 | 4.3 첫 항목 체크(GitPusher 단독). 원격이 없으면 push 실패를 그대로 둠(사용자 결정) | 사용자 승인 |
| 2026-10-31 | 4.2 체크(8건). 상세 범위 index∪recent∪상위 1000, 범위 밖 삭제. R8 search-index 크기 리스크 등록 | 사용자 승인 |
| 2026-10-31 | 4.3·4.4 체크, Phase 4 구현 완료. 실패 실행은 스택 추적 그대로·종료 코드 1 | 사용자 승인 |
