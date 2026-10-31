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
| 백엔드 A | `docker-compose.yml`, `.gitignore`, `.env.example`, `batch/build.gradle`·`settings.gradle`·`gradlew*`·`lombok.config`, `batch/src/main/resources/application*.properties`, `batch/src/main/resources/db/migration/V1__*.sql`, `…/runner/**`, `…/global/**`, `…/domain/vulnerability/**`(Vulnerability·CveId·CvssSelection·SyncState·SyncWindow), `…/domain/collect/**`(Collector·CollectResult·SourceName), `…/application/collect/**`, `…/infrastructure/nvd/**`, `…/infrastructure/persistence/vulnerability/**`, `…/infrastructure/persistence/syncstate/**`, (Phase 6) `…/domain/summary/**`·`…/application/summarize/**`·`…/infrastructure/ai/**` |
| 백엔드 B | `batch/src/main/resources/product-alias.yml`, `…/domain/product/**`(ProductAlias·primary_product 규칙), `…/domain/enrichment/**`(KEV·EPSS 값), `…/domain/score/**`(TrendScore·WhyNowTemplate·KPI), `…/application/product/**`·`…/application/enrich/**`·`…/application/score/**`·`…/application/export/**`, `…/infrastructure/kev/**`·`…/infrastructure/epss/**`, `…/infrastructure/persistence/{product,enrichment,score,stat}/**`, `…/infrastructure/export/**`(JsonExporter·GitPusher) |
| 프론트 | `web/**` 전부(목업 JSON `web/public/data/**` 포함, M1 까지), `.github/workflows/**` |
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
| 백엔드 A | `vuln-trend-1f` | 1.3 저장 완료 → 승인 대기 | — |
| 백엔드 B | `vuln-trend-25` | 0.5 커밋 → 2.1 KEV | — |
| 프론트 | `vuln-trend-0e` | 5.4 승인 → B 커밋 뒤 web/ 커밋 → 5.5 차트 | — |

### 재개 메모 (2026-10-30 일시 정지)

- 사용자 요청으로 전 판 멈춤. 재개는 사용자가 "다시 진행"을 말하면 오케스트레이터가 세 판에 "재개"를 보낸다.
- 커밋: b47eae1(골격, web/ 제외. 원래 fc2758b 를 메시지만 amend). web/ 은 5.2 승인 뒤 따로 커밋.
- 열린 결정: R7 KEV 매핑 방식(B 제안 대기 — 열 추가 안이면 V1 수정 필요), 템플릿 잔재 파일 삭제(사용자 `! rm`), GitHub 원격 생성·NVD 키 발급(사용자).
- 멈춤 보고(각 판의 진행 정도)는 이 아래에 적는다.
- **백엔드 A(멈춤 보고)**: 1.1 세 항목 구현 완료(쪽 넘김, 0.6초 간격·429/5xx 지수 백오프 5회, 120일 분할) + NVD 키 없으면 시작 거부(역패치 확인) + 테스트 프로필로 러너 끔. 테스트 54개 실패 0. import 순서 1건 고친 뒤 check-all 전체 미재실행 → 재개하면 첫 단계로 돌림 → 1.1 완료 보고. 실제 수집은 사용자 NVD 키 발급 뒤 가능. 커밋 안 함(기준 b47eae1).
- **백엔드 B(멈춤 보고)**: 0.5 실데이터 대조 완료(KEV 1764건, NVD 2026-08 CVE 중 CPE 4516건, CPE 사전 41건). 프로젝트 파일 변경 0건. 초안 사본·조사 메모는 B 스크래치패드(`…/e6ddf5b2-…/scratchpad/product-alias.draft.yml`, `alias-findings.md`). 확인 필요 5곳 처리안: Windows 버전별 CPE 8개 → windows, nginx 는 f5:nginx·f5:nginx_open_source 추가, Citrix 는 gateway 도 같은 slug, Adobe 4개 한 slug, Oracle Java·nodejs 현재 표기 재확인. KEV 빈도 높은 제품 12개 추가 제안. 재개하면 yml 확정 → ProductAlias·정규화 → 기동 시 upsert 로더·테스트 → 0.5 보고.
- **사용자 결정 대기(B)**: (a) "초기 50개"를 제품(slug) 50개로 해석(행은 별칭 포함 80~100) (b) windows-server 를 windows 로 합칠지 (c) R7 혼합안 — yml 에 KEV 원문 별칭을 명시하고 적재·조회 양쪽에 같은 정규화, 테이블 열 추가 없음(V1 그대로), KEV "Multiple Products" 류는 매핑하지 않고 CPE 첫 항목으로 넘김.
- **프론트(멈춤 보고)**: 5.2 두 태스크 구현 완료. Vitest 도입, 테스트 26개 실패 0, 커버리지 줄 100%·분기 93%(90% 기준 걸림), build·oxlint·prettier 0. 남은 것: 브라우저에서 실제 목업 데이터가 화면에 뜨는지 확인(스냅숏이 로딩 중에 찍힘). 재개하면 이것부터 → 5.2 완료 보고. 커밋 안 함.

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
