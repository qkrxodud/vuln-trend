# VulnTrend — 팀 배정과 경계 (오케스트레이터 판)

작성일: 2026-10-30
기준 문서: `vuln-trend-wbs.md`(할 일·상태), `vuln-trend-plan.md`(사양), `CLAUDE.md` + `.claude/skills/*`(코드 규칙)
구성: 오케스트레이터 1(이 판, `vuln-trend-4a`) + 프론트 개발자 1 + 백엔드 개발자 2. 모두 같은 디렉터리에서 작업한다.

## 1. 공통 규칙

1. **WBS 체크박스는 오케스트레이터만 바꾼다.** 개발자는 작업 묶음(D2)을 끝내면 오케스트레이터에게 보고하고, 보고에는 끝낸 태스크(D3) 목록·테스트 결과(개수·실패 0)·`bash scripts/check-all.sh` 종료 코드·바꾼 파일 목록을 넣는다.
2. **파일 소유권을 지킨다(§3).** 남의 영역 파일을 고쳐야 하면 고치지 말고 소유자에게 메시지로 "어느 파일에 어떤 줄"을 요청한다. 같은 디렉터리라 동시에 같은 파일을 고치면 서로 덮어쓴다.
3. **git commit·push 는 오케스트레이터가 지시할 때만.** `git init` 은 백엔드 A 가 Phase 0.1 에서 한 번 한다.
4. **파일 삭제(`rm`)는 하지 않는다.** 필요하면 오케스트레이터에게 알린다. 시험용 파일은 스크래치패드에.
5. 코드 규칙은 `CLAUDE.md` 와 `vuln-trend-dev-conventions`·`domain-test-writing` 스킬을 따른다. 규칙과 부딪히면 우회하지 말고 "결정 대기"로 적고 보고한다.
6. 보고·질문은 `SendMessage` 로 `vuln-trend-4a` 에게. 번호를 붙인 짧은 항목으로.
7. **작업 묶음(D2)마다 멈춘다**(CLAUDE.md 작업 규칙 1, 사용자 재확인 2026-10-30). 보고 → 오케스트레이터 검증 → 사용자 확인 → 오케스트레이터가 "다음 진행"을 보낸다. 관문(§4)은 다른 판에 넘길 계약이 생기는 지점이다.
8. **공유 폴더에서 빌드를 깨지 않는다**(사용자 결정 2026-10-31). TDD Red 단계에서도 아직 없는 클래스는 컴파일되는 빈 뼈대를 먼저 둔다. 커밋은 자기 파일 경로만 지정해 add 하고(`git add -A` 금지), 커밋 순서는 오케스트레이터가 정한다(인덱스 잠금 충돌 방지).

## 2. 배정

| 역할 | WBS 범위 | 순서 |
|---|---|---|
| **백엔드 A — 골격·NVD** | 0.1 0.2 0.3 0.4 → 1.1~1.5 → 2.4(실패 규칙 통합, 러너) → 6.1~6.3(한국어 요약) | 0.1~0.4 를 가장 먼저 끝내고 "골격 준비" 를 알린다(관문 G0) |
| **백엔드 B — 보강·점수·출력** | 0.5(product-alias.yml 50개) → 2.1 2.2 2.3 → 3.1~3.5 → 4.1~4.4 | 0.5 의 yml 작성은 바로 시작. 코드는 G0 뒤에 |
| **프론트 — 사이트** | 5.1~5.13 전부 (+ `.github/workflows/`) | 기획서 7장·WBS 4.2 의 JSON 필드로 목업 JSON 을 만들어 바로 시작. M1 뒤 실제 JSON 으로 교체 |

Phase 6(요약)은 Phase 3 뒤면 가능하므로 A 가 Phase 1·2.4 를 끝낸 시점에 착수한다. Gate G·Phase 7 은 M1·M2 뒤 다시 배정한다.

## 3. 파일 소유권

패키지 루트 `com.vulntrend.batch` (결정 대기지만 사용자 반대가 없으면 이대로 간다). 계층: `runner → application → domain ← infrastructure`, 공통 `global`.

| 소유자 | 경로 |
|---|---|
| 백엔드 A | `docker-compose.yml`, `.gitignore`, `.env.example`, `batch/build.gradle`·`settings.gradle`·`gradlew*`·`lombok.config`, `batch/src/main/resources/application*.properties`, `batch/src/main/resources/db/migration/V1__*.sql`, `…/runner/**`, `…/global/**`, `…/domain/vulnerability/**`(Vulnerability·CveId·CvssSelection·SyncState·SyncWindow), `…/domain/collect/**`(Collector·CollectResult·SourceName), `…/application/collect/**`, `…/infrastructure/nvd/**`, `…/infrastructure/persistence/vulnerability/**`, `…/infrastructure/persistence/syncstate/**`, (Phase 6) `…/domain/summary/**`·`…/application/summary/**`·`…/infrastructure/summary/**`·`…/infrastructure/persistence/summary/**`, 마이그레이션 V1·V2 이후 전부, (4.3) `…/domain/publish/**`·`…/infrastructure/publish/**` |
| 백엔드 B | `batch/src/main/resources/product-alias.yml`, `…/domain/product/**`(ProductAlias·primary_product 규칙), `…/domain/enrichment/**`(KEV·EPSS 값), `…/domain/score/**`(TrendScore·WhyNowTemplate·KPI), `…/domain/stat/**`·`…/application/stat/**`, `…/domain/export/**`, `…/domain/gate/**`·`…/application/gate/**`·`…/infrastructure/gate/**`, `…/application/product/**`·`…/application/enrich/**`·`…/application/score/**`·`…/application/export/**`, `…/infrastructure/kev/**`·`…/infrastructure/epss/**`·`…/infrastructure/feed/**`, `…/infrastructure/persistence/{product,enrichment,score,stat}/**`, `…/infrastructure/export/**`(JsonExporter) |
| 프론트 | `web/**` 전부(목업 JSON `web/public/data/**` 포함, M1 까지), `.github/workflows/**`, Gate G 화면 쪽 점검 스크립트(`web/scripts/`) |
| 오케스트레이터 | `vuln-trend-wbs.md`, `vuln-trend-plan.md`, 이 파일, `CLAUDE.md`, `.claude/**`, `scripts/**` |

공유 파일 변경 요청 예: B 가 `application.properties` 에 `kev.url` 키가 필요하면 → A 에게 "application.properties 에 `kev.url=https://…` 추가 요청" → A 가 넣고 답한다. `build.gradle` 의존성 추가도 같다.

테스트 fake(`src/test/.../fake/Fake*`)는 흉내 내는 창구의 소유자가 만든다. B 가 A 의 `VulnerabilityRepository` fake 가 필요하면 A 에게 요청한다.

## 4. 관문(gate)과 계약

| 관문 | 조건 | 알림 |
|---|---|---|
| **G0 골격 준비** | A: 0.1~0.4 완료. `batch/` 가 `./gradlew check` 통과, Flyway V1 로 테이블 8개, `Collector`·`CollectResult`·`SourceName`·`SyncWindow` 인터페이스/값 객체와 `BatchRunner` 뼈대가 있음 | A → 오케스트레이터 → B 에게 "코드 착수" |
| **G1 JSON 계약** | 프론트가 기획서 7장 + WBS 4.2 로 만든 타입 정의(`web/src/types/*.ts`)와 목업 JSON 을 오케스트레이터에게 보고. B 의 4.2 구현은 이 타입과 1:1 | 프론트 → 오케스트레이터 → B |
| **M1 JSON 산출** | B: 4.4 완료, `web/public/data/` 7종 생성 | B → 오케스트레이터 → 프론트에게 "실제 JSON 으로 교체" |
| **M2 MVP 공개** | 5·6·G 통과 | 오케스트레이터 → 사용자 |

계약은 문서가 단일 출처다: JSON 필드 = 기획서 7장, 스키마 = 기획서 5장, 점수식·실패 규칙 = 기획서 6장. 계약을 바꾸고 싶으면 코드를 고치기 전에 오케스트레이터에게 보낸다.

## 5. 현재 상태

| 역할 | 세션 | 지금 하는 것 | 다음 관문 |
|---|---|---|---|
| 백엔드 A | `vuln-trend-1f` | 대기(R10 a1f3fe7·OpenAI a7696c4 커밋) | 사용자 첫 하루 실행 뒤 결과 점검 |
| 백엔드 B | `vuln-trend-25` | R8 반영 완료 보고(커밋 대기, R10 해결 뒤) | — |
| 프론트 | `vuln-trend-0e` | gate:check 색인 크기를 gzip 기준으로 | 배포 성공 뒤 5.13 확인 2건, 새 디자인 파일 대기 |

### 재개 메모 (2026-10-30 일시 정지 — 지난 정지, 아래 2026-10-31 정지가 최신)

- 사용자 요청으로 전 판 멈춤. 재개는 사용자가 "다시 진행"을 말하면 오케스트레이터가 세 판에 "재개"를 보낸다.
- 커밋: b47eae1(골격, web/ 제외. 원래 fc2758b 를 메시지만 amend). web/ 은 5.2 승인 뒤 따로 커밋.
- 열린 결정: R7 KEV 매핑 방식(B 제안 대기 — 열 추가 안이면 V1 수정 필요), 템플릿 잔재 파일 삭제(사용자 `! rm`), GitHub 원격 생성·NVD 키 발급(사용자).
- 멈춤 보고(각 판의 진행 정도)는 이 아래에 적는다.
- **백엔드 A(멈춤 보고)**: 1.1 세 항목 구현 완료(쪽 넘김, 0.6초 간격·429/5xx 지수 백오프 5회, 120일 분할) + NVD 키 없으면 시작 거부(역패치 확인) + 테스트 프로필로 러너 끔. 테스트 54개 실패 0. import 순서 1건 고친 뒤 check-all 전체 미재실행 → 재개하면 첫 단계로 돌림 → 1.1 완료 보고. 실제 수집은 사용자 NVD 키 발급 뒤 가능. 커밋 안 함(기준 b47eae1).
- **백엔드 B(멈춤 보고)**: 0.5 실데이터 대조 완료(KEV 1764건, NVD 2026-08 CVE 중 CPE 4516건, CPE 사전 41건). 프로젝트 파일 변경 0건. 초안 사본·조사 메모는 B 스크래치패드(`…/e6ddf5b2-…/scratchpad/product-alias.draft.yml`, `alias-findings.md`). 확인 필요 5곳 처리안: Windows 버전별 CPE 8개 → windows, nginx 는 f5:nginx·f5:nginx_open_source 추가, Citrix 는 gateway 도 같은 slug, Adobe 4개 한 slug, Oracle Java·nodejs 현재 표기 재확인. KEV 빈도 높은 제품 12개 추가 제안. 재개하면 yml 확정 → ProductAlias·정규화 → 기동 시 upsert 로더·테스트 → 0.5 보고.
- **사용자 결정 대기(B)**: (a) "초기 50개"를 제품(slug) 50개로 해석(행은 별칭 포함 80~100) (b) windows-server 를 windows 로 합칠지 (c) R7 혼합안 — yml 에 KEV 원문 별칭을 명시하고 적재·조회 양쪽에 같은 정규화, 테이블 열 추가 없음(V1 그대로), KEV "Multiple Products" 류는 매핑하지 않고 CPE 첫 항목으로 넘김.
- **프론트(멈춤 보고)**: 5.2 두 태스크 구현 완료. Vitest 도입, 테스트 26개 실패 0, 커버리지 줄 100%·분기 93%(90% 기준 걸림), build·oxlint·prettier 0. 남은 것: 브라우저에서 실제 목업 데이터가 화면에 뜨는지 확인(스냅숏이 로딩 중에 찍힘). 재개하면 이것부터 → 5.2 완료 보고. 커밋 안 함.

### 재개 메모 (2026-10-31 세 번째 정지 — 최신)

- 사용자 요청: "작업 마무리하고 멈춤". A 는 1.5 커밋만, 프론트는 A 다음 5.8 커밋만, B 는 2.3 을 마치고 멈춘다. 2.4·5.9·3.1 은 시작하지 않는다.
- 재개하면: B 의 2.3 완료 보고를 검증해 승인 → B 커밋 → A 2.4(ProductAssignService 연결 포함), B 3.1, 프론트 5.9. 5.10 모바일 전에 사용자에게 디자인 방향을 묻는다.
- 남은 사용자 작업: NVD 키(1.5 실측·Phase 1 DoD 가 막힘), GitHub 원격, 템플릿 잔재 삭제, 루트의 빈 package-lock.json(출처 확인 중).
- 개발 서버: 오케스트레이터 세션이 127.0.0.1:4500 에 web/ dev 서버를 띄워 둠.
- 멈춤 보고:
  - 백엔드 A: 1.5 커밋 3ced104. 2.4 는 파일 변경 없음(읽기만). 재개 계획: BatchRunner 에 수집 성공 뒤 연결 지점(private 메서드)을 두고, B 의 2.3 커밋 뒤 ProductAssignService 주입. DoD 는 실제 저장소 + NVD 스텁 + 실제 KevCollector(틀린 kev.url)로 확인.
  - 백엔드 B: 2.3 완료 보고, 오케스트레이터 검증 통과(공유 폴더 check-all 0, 테스트 312개 실패 0). 커밋 전. 사용자 확인 필요: (j) CPE 가 아직 없으면 KEV 제품 원문을 대표 제품으로(순위 4) (k) KEV 표기가 표에 없으면 CPE 표준 이름. 참고: 표본에서 CPE 첫 항목이 표에 걸리는 비율 24.5%(튜닝 대상).
  - 프론트: 5.8 커밋 a7f271e. 5.9 Red 테스트 2개(search.test.ts, SearchBox.test.tsx)를 커밋하지 않고 남김 → web build 가 TS2307 로 실패 → search.ts 뼈대로 복구(build 0, 테스트 152 중 5.9 Red 18 실패·134 통과, 커밋 안 함). 사용자 확인 필요: 검색어로 메인 목록도 거를지(디자인) / 드롭다운 검색만(WBS 5.9).

### 재개 메모 (2026-10-31 두 번째 정지 — 지난 정지)

- 사용자 요청: "지금 것만 완료하고 멈춤". A 는 1.3 커밋 두 개만, B 는 2.1, 프론트는 5.5 를 마치고 멈춘다. 1.4·2.2·5.6 은 시작하지 않는다.
- 재개하면: B 의 2.1, 프론트의 5.5 완료 보고를 검증해 사용자 승인 → 커밋 순서 A 없음 → B → 프론트 → 오케스트레이터 문서. 그다음 A 1.4, B 2.2, 프론트 5.6.
- 커밋 기록: b47eae1 골격 · 9ab8223 1.1 · 3b9a8bb 5.1·5.2 · 1310e6e docs · 9d6c219 5.3 · 32829f4 1.2 · eab04ad 0.5 · cce9ea6 5.4 · ebef0a7 docs · 79f0b19 1.3 · 5625014 KEV 설정·수집 순서
- 사용자 작업 남음: NVD 키 발급(.env), GitHub 원격 생성, 템플릿 잔재 삭제.
- 멈춤 보고:
  - 백엔드 A: 1.3 커밋 2개(79f0b19·5625014) 뒤 변경 없음. 1.4 는 파일을 바꾸지 않음(읽기만). 재개 시 1.4 부터.
  - 프론트: 5.5 완료 보고, 오케스트레이터 검증 통과(build·lint·format 0, vitest 70개, 줄 99.2%·분기 94.9%). build 에 Recharts 청크 500kB 경고(실패 아님) — 5.12/5.13 에서 차트 lazy import 제안. 재개 시 사용자 승인 → web/ 커밋 → 5.6.
  - 백엔드 B: 2.1 KEV 완료 보고, 오케스트레이터 검증 통과(공유 폴더 check-all 0, 테스트 224개 실패 0). A 의 KEV 설정은 이미 5625014 로 커밋됨(B 는 미커밋으로 알고 있음 → 재개 때 알림). 사용자 확인 필요: (f) CISA 가 목록에서 내린 CVE 는 KEV 표시를 지움(전부 지운 뒤 다시 붙임) (g) 목록이 비거나 쓸 건이 0개면 KEV 수집 실패 처리(표시 전부 지워지는 사고 방지). 재개 시 승인 → B 커밋 → 2.2(EPSS 주소 실제 확인, A 에게 EpssProperties 요청).

## 6. 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-10-30 | 초판. 3인 배정·소유권·관문 | 사용자: "오케스트레이터, front 1·backend 2 겹치지 않게" |
| 2026-10-30 | 1f=백엔드 A 확정(첫 backend 회신), 0e=백엔드 B 임시(역할 미지정 세션), 25 회신 대기 | 역할 확인 회신 |
| 2026-10-30 | 25=백엔드 B 확정, 0e=프론트로 변경 | 25 가 backend 로 회신, 0e 는 역할 미지정이었음 |
| 2026-10-30 | §1.7 을 "D2 마다 멈춤"으로 정정. G0 통과(37 테스트·check-all 0). G1: 전부 snake_case·index 행에 kev_ransomware(사용자 결정) | 사용자 결정, CLAUDE.md 규칙 1 과 충돌 해소 |
| 2026-10-30 | G1 확정(web/src/types, snake_case 검증) → B 전달. G0 사용자 승인 → A 첫 커밋·1.1 | 사용자 승인 |
| 2026-10-30 | 전 판 일시 정지, 재개 메모 추가 | 사용자 이동 |
| 2026-10-31 | 재개. B 결정 (a)~(c)는 사용자 답이 없어 권고안으로 진행, 결정 대기 표기 | 사용자 "이어서 진행" |
| 2026-10-31 | 1.1·5.2 승인, 커밋 순서 A→프론트(인덱스 잠금 회피). B 결정 (a)~(c) 확정 | 사용자 승인 |
| 2026-10-31 | 규칙 8(공유 폴더 빌드 유지·경로 지정 커밋) 추가, B 소유에 application/product. 1.2·0.5·5.3·5.4 승인 | 사용자 결정 |
| 2026-10-31 | 두 번째 일시 정지, 재개 메모 추가. WBS 1.3 체크 | 사용자 이동 |
| 2026-10-31 | 재개. 2.1·5.5 승인, KEV 규칙 (f)(g) 확정 | 사용자 "이어서 진행" |
| 2026-10-31 | 1.4·2.2·5.6·5.7 승인, B 소유에 infrastructure/feed 추가, 검증 worktree 2개 정리 | 사용자 승인 |
| 2026-10-31 | 1.5·5.8 승인. 공유 폴더가 B Red 로 판정 불가 → HEAD+A 파일 worktree 로 검증 후 정리 | 사용자 승인 |
| 2026-10-31 | 세 번째 일시 정지, 재개 메모 추가 | 사용자 자리 이동 |
| 2026-10-31 | 재개. 2.3 승인((j)(k) 확정), 5.9 드롭다운만 | 사용자 "이어서 진행" |
| 2026-10-31 | 2.4·3.1·5.9·5.10 승인. 모바일은 기획서 규칙으로(디자인 1장 대신) | 사용자 승인 |
| 2026-10-31 | 3.2·5.11 승인. A 가 Red 를 공유 폴더에서 돌려 몇 분간 빌드 깨짐 → 규칙 8 재확인 | 사용자 승인 |
| 2026-10-31 | 3.3·6.1 승인, 요약은 Opus 5.5 + 공식 Java SDK 로 결정, 5.12 대비·lazy 결정 | 사용자 결정 |
| 2026-10-31 | 3.4·5.12 승인. G1 yesterday.high_risk null 허용(사용자 확정 규칙의 귀결, 오케스트레이터 승인) | — |
| 2026-10-31 | 5.13 커밋(298ae30). 프론트 다음 배정 = Gate G 점검 준비(사용자 결정) | 사용자 결정 |
| 2026-10-31 | Gate G 점검·6.2·3.5 승인. 요약 fallbacks·버전 검사 결정 | 사용자 결정 |
| 2026-10-31 | 4.1 승인. B 소유에 domain/stat·application/stat·domain/export 추가 | 사용자 승인 |
| 2026-10-31 | 6.3 승인(V2 summary_call). 4.3 GitPusher B→A 재배정, A 소유에 infrastructure/publish·마이그레이션. 사용자 새 디자인 요청(링크는 직접 열 수 없어 파일 내보내기 요청) | 사용자 승인·요청 |
| 2026-10-31 | 4.3 승인, A 소유에 domain/publish | 사용자 승인 |
| 2026-10-31 | 4.2 승인 | 사용자 승인 |
| 2026-10-31 | 4.4 승인, Phase 4 구현 완료 | 사용자 승인 |
| 2026-10-31 | Gate G 백엔드 점검 승인, B 소유에 gate 패키지. 키 없으면 시작 거부 규칙 유지(Gate 점검도 키 뒤) | 사용자 승인 |
| 2026-10-31 | --gate-check 입구 커밋. 키 없이 할 코드 작업 종료 — 세 판 대기 | — |
| 2026-10-31 | NVD 키 → .env 이동(.env.example 원복), A 백필 착수 | 사용자 키 입력 |
| 2026-10-31 | 요약 제공자 OpenAI 로 전환(사용자 결정) | 사용자 결정 |
| 2026-11-01 | 백필 완료, B 에 R8 실측 배정. push 권한 미결정으로 하루 실행 보류 | — |
| 2026-11-01 | R8 결정 반영 배정(B·프론트) | 사용자 결정 |
| 2026-11-01 | R10: 공유 폴더 배치 시험 전면 금지(시험이 실제 .env 를 읽어 GitHub push·OpenAI 호출 시도) | 긴급 |
| 2026-11-01 | 공유 폴더 시험 금지 해제. 첫 하루 실행은 사용자 직접 | — |
