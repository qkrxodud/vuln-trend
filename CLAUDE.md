# vuln-trend

보안 취약점 트렌드 사이트. 로컬 Spring Boot 배치(`batch/`, Java 17, 웹서버 없음)가 NVD·KEV·EPSS 를 모아 Postgres 에 적재하고 트렌드 점수를 매긴 뒤 `web/public/data/*.json` 을 써서 push 하면, GitHub Pages 의 React 사이트(`web/`)가 그 JSON 을 읽어 보여 준다.

- 기획서(단일 출처): `vuln-trend-plan.md` — 5장 스키마, 6장 실행 흐름, 7장 JSON 설계
- 진행 현황판: `vuln-trend-wbs.md` — 체크는 태스크(D3)에서만, 범위 변경은 이 파일을 먼저 고친다
- 리뷰 합의: `vuln-trend-review.md`

## 코드 규칙

코드 규칙의 **단일 출처는 `vuln-trend-dev-conventions` 스킬**이다(`.claude/skills/vuln-trend-dev-conventions/SKILL.md`, 코드 모양은 같은 폴더 `references/code-patterns.md`). 테스트 이름은 `domain-test-writing` 스킬을 따른다. 두 스킬과 `scripts/` 검사 도구는 이전 프로젝트(clarity-api)의 하네스에서 코드 규칙 부분만 가져와 이 프로젝트의 계층·개념 이름으로 바꾼 것이다.

`batch/` 코드를 작성·수정·리팩터링할 때는 반드시 두 스킬을 읽고 시작한다. 기계로 잡을 수 있는 규칙은 `bash scripts/check-all.sh` 가 잡는다.

### 작업 규칙 (사용자 지정, 반드시 지킬 것)

1. **작업 단위 = WBS 의 작업 묶음(D2) 하나.** 끝나면 멈추고 사용자 확인을 받는다. 확인 전에는 다음 묶음도, 커밋도 하지 않는다.
2. **TDD.** 테스트를 먼저 쓰고 실패를 확인한 뒤 최소 구현. 단위 테스트는 Fake(`src/test/.../fake`)로, Mock 은 외부 시스템에만.
3. **테스트 이름은 도메인 언어로 쓴다**(`domain-test-writing`). 문서처럼 읽혀야 한다.
4. **record 는 DTO 에서만, 생성자는 `@RequiredArgsConstructor`(빈) 또는 private `@Builder` + 정적 팩터리(그 외), 예외는 `global/error` 한 곳에, setter 금지, `if (!…)` 금지, 점수·확률은 `BigDecimal`.** 이유를 대며 예외를 만들지 않는다. 막히면 사용자에게 묻는다.
5. **계층 방향:** `runner → application → domain ← infrastructure`, `global` 은 아무도 참조하지 않는다. 기능 이름은 계층 아래 하위 패키지로.
6. **완료 조건: `bash scripts/check-all.sh` 종료 코드 0.** 완료 보고에는 테스트 결과(개수·실패 0), 커버리지(90% 이상), 기획서·WBS 갱신 내역을 넣는다.
7. **결정 대기 표기.** 규칙과 부딪혀 다른 방식을 택했으면 사용자 결정 전까지 문서·주석에 "결정 대기"로 적는다. 지금 결정 대기인 것: 패키지 루트(`com.vulntrend.batch` 가정), 계층 아래 기능 패키지 구조, `web/` 프론트 규칙(Phase 5 전).

## 실행

```bash
docker compose up -d                                   # Postgres 16 (localhost:5432, vuln/vuln/vuln)
(cd batch && ./gradlew bootRun)                        # 배치 1회 실행 (Phase 0.3 골격 후)
bash scripts/check-all.sh                              # 완료 전 전체 검사 (회귀 시험·record·예외·코드 규칙·테스트 이름·빌드/테스트/커버리지)
bash scripts/check-all.sh --skip-gradle                # 빠른 규칙 점검 (완료 판정에는 쓰지 않음)
```

## 검사 도구 (`scripts/`)

| 파일 | 잡는 것 |
|---|---|
| `check-all.sh` | 아래 전부 순서대로 실행, 종료 코드로 판정 |
| `tests/run-convention-tests.py` | `check-conventions.py` 자체의 회귀 시험(픽스처 `tests/conventions/`, 기대 위반 57건) |
| `check-records.sh` | `dto` 패키지 밖의 record |
| `check-exceptions.sh` | `global/error/exception` 밖의 예외 클래스, `global` 이 다른 계층을 import |
| `check-conventions.py` (테스트는 `fake`·`support` 패키지를 어느 계층에서든 import 가능) | 손으로 쓴 생성자, `if (!`, 람다 `!`, 싱글톤 빈의 바뀌는 필드·그릇, setter, 계층 방향, `@Value`, import 순서, 기본값 없는 `${…}`, `Fake*` 위치, 프레임워크 예외 문장 로그 |
| `check-display-names.sh` | 테스트 이름의 개발 용어 |

검사 도구가 놓친 위반이 두 번 나오면 문장으로 적지 말고 스크립트에 규칙을 추가하고, 픽스처와 `expected.txt` 에 잡을 것·잡지 말 것을 함께 넣는다.

**변경 이력:**
| 날짜 | 변경 내용 | 대상 | 사유 |
|------|----------|------|------|
| 2026-10-30 | 코드 컨벤션 도입: clarity-api 하네스에서 `clarity-dev-conventions`→`vuln-trend-dev-conventions`, `domain-test-writing`(용어 사전 교체), `check-*.sh`·`check-conventions.py`·회귀 시험 픽스처를 가져와 패키지 `com.vulntrend.batch`·계층 `runner` 로 바꿈. 웹 전용 규칙 12(전역 예외 처리기)·DBML 대조·ErrorCode 표 대조는 제외 | `CLAUDE.md`, `.claude/skills/`, `scripts/` | 개발 착수 전 코드 컨벤션 필요 — "코드 컨벤션만 하네스에서 가져와서 적용" |
