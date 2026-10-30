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
| 백엔드 B | `batch/src/main/resources/product-alias.yml`, `…/domain/product/**`(ProductAlias·primary_product 규칙), `…/domain/enrichment/**`(KEV·EPSS 값), `…/domain/score/**`(TrendScore·WhyNowTemplate·KPI), `…/application/enrich/**`·`…/application/score/**`·`…/application/export/**`, `…/infrastructure/kev/**`·`…/infrastructure/epss/**`, `…/infrastructure/persistence/{product,enrichment,score,stat}/**`, `…/infrastructure/export/**`(JsonExporter·GitPusher) |
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
| 백엔드 A | `vuln-trend-1f` | 첫 커밋 → 1.1 NVD 클라이언트 | — |
| 백엔드 B | `vuln-trend-25` | 0.5 yml·적재 + R7 제안 (G0·G1 수신) | — |
| 프론트 | `vuln-trend-0e` | 5.2 데이터 레이어 | M1 대기 |

## 6. 변경 이력

| 날짜 | 변경 | 사유 |
|---|---|---|
| 2026-10-30 | 초판. 3인 배정·소유권·관문 | 사용자: "오케스트레이터, front 1·backend 2 겹치지 않게" |
| 2026-10-30 | 1f=백엔드 A 확정(첫 backend 회신), 0e=백엔드 B 임시(역할 미지정 세션), 25 회신 대기 | 역할 확인 회신 |
| 2026-10-30 | 25=백엔드 B 확정, 0e=프론트로 변경 | 25 가 backend 로 회신, 0e 는 역할 미지정이었음 |
| 2026-10-30 | §1.7 을 "D2 마다 멈춤"으로 정정. G0 통과(37 테스트·check-all 0). G1: 전부 snake_case·index 행에 kev_ransomware(사용자 결정) | 사용자 결정, CLAUDE.md 규칙 1 과 충돌 해소 |
| 2026-10-30 | G1 확정(web/src/types, snake_case 검증) → B 전달. G0 사용자 승인 → A 첫 커밋·1.1 | 사용자 승인 |
