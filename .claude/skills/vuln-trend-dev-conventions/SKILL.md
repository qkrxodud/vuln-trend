---
name: vuln-trend-dev-conventions
description: vuln-trend 배치(batch/, Spring Boot·Java 17·JdbcTemplate·Flyway) 코드를 작성·수정·리팩터링할 때 반드시 사용. 수집기·점수 계산·요약·JSON 내보내기·설정·예외·DTO·마이그레이션 작성, 버그 수정, "구현해줘", "고쳐줘", "추가해줘", "리팩터링", TDD Green 단계에 적용. 생성자 주입·정적 팩터리·record·예외 위치·싱글톤 상태·계층 방향·배치 실패 규칙과 그 이유를 담는다. 테스트 이름 짓기는 domain-test-writing 을 함께 쓴다.
---

# vuln-trend 개발 규칙

이 규칙들은 사용자가 직접 정했거나, 이전 프로젝트(clarity-api)에서 **실제로 어겨서 지적받은** 것들을 가져온 것이다. 기계로 잡을 수 있는 것은 `bash scripts/check-all.sh`가 잡는다. 스크립트가 통과해도 아래 "왜"를 이해하고 지켜야 스크립트가 못 보는 변형을 피할 수 있다.

코드 모양 예시는 `references/code-patterns.md`(값 객체·리포지토리 어댑터·서비스·설정·예외·러너·수집기 템플릿)를 본다.

**결정 대기(Phase 0.3 골격을 만들 때 사용자가 확정):** 패키지 루트. 지금은 `com.vulntrend.batch` 로 두었고, 바뀌면 `scripts/check-conventions.py` 의 `PACKAGE_ROOT` 와 `check-exceptions.sh` 의 같은 변수(또는 환경 변수 `CHECK_PACKAGE_ROOT`)만 고친다.

## 1. 객체를 만드는 방법

| 대상 | 방법 | 왜 |
|---|---|---|
| Spring 빈(서비스·어댑터·수집기·러너·설정) | `private final` 필드 + **Lombok `@RequiredArgsConstructor`**. 생성자를 직접 쓰지 않는다. **적용 범위는 운영 코드(src/main)** — 테스트 안의 도우미 클래스는 손으로 쓴 생성자를 허용한다 | 사용자 지정 규칙. 직접 쓴 생성자는 "예외적 이유"가 붙으며 늘어난다(이전 프로젝트에서 6번 재발) |
| 빈에 설정 값이 필요함(`nvd.api-key`, `output.dir`, `summary.daily-limit` 등) | `global/config`의 `@ConfigurationProperties` 클래스(`final` 필드 + `@RequiredArgsConstructor`)를 주입. **`@Value`는 어디에도 쓰지 않는다**(생성자·`@Bean` 메서드 인자 포함). 컬렉션 필드는 `@Getter(AccessLevel.NONE)` + `List.copyOf` 사본 getter로 내보낸다 | `@Value` 때문에 생성자를 손으로 쓰게 된 일과, 같은 비밀 값을 두 경로로 읽은 일이 있었다. 바인딩된 목록은 `ArrayList`라서 그대로 내보내면 누구나 바꿀 수 있다 |
| 빈 시작 시 검사(필수 설정 누락 등 — `NVD_API_KEY` 가 대표) | `@PostConstruct` 메서드에서 **값 자체가 비었는지** 검사하고 던진다. 설정 파일의 `${…}`에는 기본값(`${VAR:}`)을 두고, 필수 값은 이 검사에서 거부한다. **실제 `application*.properties`·프로필을 올린 컨텍스트에서 환경 값을 빼고 시작이 거부되는지** 확인하는 테스트를 둔다(시스템 환경 변수를 빼고 돌린다) | 기본값 없는 `${NVD_API_KEY}`는 해석에 실패해도 오류가 나지 않고 글자 그대로 들어가서 "값이 없으면 시작하지 않는다"던 안전장치가 실제로는 동작하지 않는다(이전 프로젝트에서 재현) |
| 빈이 아닌 객체(값 객체·결과 객체·도우미) | private `@Builder` 생성자 + 정적 팩터리 `of`/`from`. 생성 규칙·정리 로직은 팩터리나 private 생성자 안에서 | 사용자 지정 정적 팩터리 형식. 이름 있는 팩터리가 의도를 드러냄 |
| 빈이 아니고 밖에 노출되지 않는 private 중첩 값 클래스 | private `@Builder` 생성자 + 정적 팩터리, **또는** Lombok `@RequiredArgsConstructor(access = AccessLevel.PRIVATE)` + 정적 팩터리 | 바깥에서 만들 수 없는 private 타입이라 빌더가 주는 이름 붙은 인자보다 간결함이 낫다 |
| enum (`CvssVersion`, `SyncStatus`, `ErrorCode` 등) | 손 생성자를 쓰지 않고 Lombok `@RequiredArgsConstructor`(Lombok 이 enum 생성자를 private 으로 만든다) | 생성자 규칙을 enum 에도 적용한다. 검사(`check-conventions.py` 규칙 1)가 최상위·중첩 enum 손 생성자를 잡는다 |
| 유틸리티 클래스 | `@NoArgsConstructor(access = AccessLevel.PRIVATE)` | 손으로 쓴 private 생성자도 검사에 걸린다 |
| JPA 엔티티·`@Embeddable` (조회·JSON 생성에 JPA 를 쓸 때만) | `@NoArgsConstructor(access = PROTECTED)` + private `@Builder` 생성자 + 정적 팩터리 | JPA 요구 + 같은 형식 |
| 예외 | `global/error/exception`에서 `VulnTrendException`을 상속, `super(ErrorCode.X, 상세)` | 유일하게 생성자를 직접 쓰는 곳 |

## 2. record 는 DTO 에서만

- record는 **`dto` 패키지**의 요청·응답·명령 객체에만 쓴다: `application/{기능}/dto`(명령·결과), `infrastructure/{소스}/dto`(NVD·KEV·EPSS·OSV 응답을 Jackson 으로 받는 모양), `global/error/dto`.
- `domain/{기능}/dto` 의 record 는 저장소 → 출력으로 값을 나르는 운반용에 한한다(예: `domain/score/dto/TrendListing`, 2026-10-31 승인). 규칙·행위를 가진 도메인 개념은 일반 클래스로 쓴다.
- 엔티티·값 객체·서비스 내부 값·테스트 도우미는 일반 클래스로 쓴다.
- 왜: 사용자 규칙("record는 숨겨져서 읽기 힘들다"). 서비스 내부 값과 테스트 도우미에 record를 써서 **두 번 지적받았다.** `check-records.sh` 가 잡는다.

## 3. 싱글톤 빈에는 바뀌는 상태를 두지 않는다

- 빈의 인스턴스 필드는 모두 `final`이고, 담긴 값도 바꿀 수 없어야 한다(`List.copyOf`, `Set.copyOf`, `Collectors.toUnmodifiableSet()`).
- 실행마다 다른 값(이번 수집 구간, 적재 건수, 실패한 소스 목록)은 지역 변수·메서드 인자·**결과 객체**로 다룬다. 러너가 단계별 결과를 모아 돌려받는 구조로 쓴다.
- 왜: 빈은 모든 호출이 공유한다. 배치가 단일 스레드라도 "하루 1회 실행 + 백필 1회성 실행"이 섞이면 상태가 새어 나간다. 요청 횟수 제한(rate limit) 창처럼 **상태 자체가 목적인 빈**이 필요해지면 혼자 넓히지 말고 사용자에게 묻는다. 허용 목록은 `check-conventions.py` 의 `ALLOWED_STATEFUL_BEANS` 로 강제되며 지금은 **비어 있다**(규칙 3-b).

## 4. 계층과 패키지

```
runner → application → domain ← infrastructure        global(error·config)은 모두가 쓰되 아무도 참조하지 않음
```

| 계층 | 담는 것 | 기획서 모듈 이름과의 대응 |
|---|---|---|
| `runner` | `CommandLineRunner` 진입점, CLI 인자(백필 구간 등) 해석, 단계 순서·실패 규칙 적용 | — (웹의 presentation 자리) |
| `application` | 유스케이스: 수집 순회, 점수 갱신, 일별 통계 재계산, 요약 발급, JSON 내보내기·게시 지시 | `collector` 순회 / `scorer` / `summarizer` / `exporter` 의 **서비스** |
| `domain` | `Vulnerability`, `TrendScore`, `CvssSelection`, `WhyNowTemplate`, 수집 진행 기록(`SyncState`), 리포지토리·`Collector`·`Summarizer`·`ExportSink` **인터페이스** | 규칙·불변식(점수식, CVSS 선택 순서, Rejected 처리) |
| `infrastructure` | NVD·KEV·EPSS·OSV·RSS HTTP 클라이언트, JdbcTemplate 리포지토리 어댑터, Spring AI 요약 어댑터, JSON 파일 쓰기, `ProcessBuilder` git push | `collector` 구현체 / 점수 SQL / Spring AI / `JsonExporter`·`GitPusher` |
| `global` | `error`(ErrorCode·VulnTrendException·예외들), `config`(`@ConfigurationProperties`, `Clock` 빈) | — |

- 기능 이름(`collect`·`score`·`summarize`·`export`)은 각 계층 아래 **하위 패키지**로 둔다(`application/collect`, `infrastructure/nvd`). 기획서 6장의 평평한 `collector/scorer/…` 디렉터리를 그대로 최상위에 두지 않는다 — 계층 방향을 스크립트로 검사하려면 첫 마디가 계층 이름이어야 한다. (결정 대기: 사용자가 기획서 모양을 원하면 `LAYER_IMPORTS` 를 그에 맞게 바꾼다.)
- 도메인은 Spring·HTTP·JDBC를 모른다. 시각은 `Instant now`를 인자로 받는다(서비스는 `Clock` 빈에서 얻음). 모든 시각은 UTC `Instant`.
- **import 방향(스크립트로 검사):** domain→{domain, global}, application→{application, domain, global}, runner→{runner, application, domain, global}, infrastructure→{infrastructure, domain, global}, global→{global}.
  - 여러 계층이 함께 쓰는 상수(예: 소스 이름 `nvd`·`kev`, 점수 상한)는 그 개념이 속한 도메인 쪽에 둔다. 인프라 클래스의 상수를 러너가 import하면 안 된다.
  - 빈 등록(`@Bean`)은 **그 빈이 속한 계층의 설정 클래스**에서 한다.
- **가짜 구현체(`Fake*`)는 `src/test/.../fake`에만 둔다**(스크립트 규칙 10). fake는 fake·application·domain·global을 import할 수 있다 — 도메인 리포지토리뿐 아니라 애플리케이션 창구(예: `ExportSink`)도 흉내 내기 때문이다. 러너·인프라는 보지 않는다.
- 리포지토리는 도메인 인터페이스 + `Jdbc<Root>Repository`(`@Repository`, `NamedParameterJdbcTemplate`)로 둔다. 수집 경로의 upsert 는 **`batchUpdate` + `insert … on conflict (vuln_id) do update`** 로 쓴다. JPA `saveAll`은 select 후 update 라 느리므로 수집 경로에 쓰지 않는다(기획서 5장).
- **예외는 `global/error` 한 곳에만 둔다.** 새 오류는 `ErrorCode`에 먼저 추가한다. 도메인 규칙 위반(`DomainValidationException`), 외부 소스 실패(`SourceFetchException` — 어느 소스인지 포함), 게시 실패(`PublishException`) 정도로 종류를 나눈다. `global`은 다른 계층을 import하지 않는다(`check-exceptions.sh`).

## 5. 공통 코드 규칙 (사용자 지정, 요약)

- `if (!…)`과 람다 안의 `!`(`x -> !…`) 금지 → `isNotX()` 도우미, `Objects.isNull`/`nonNull`, `Predicate.not(...)`. 중첩 if는 메서드로 빼고, `else` 대신 early return.
- setter 금지 → 처음 값은 팩터리에서, 바꿀 값은 `changeX`나 행위 이름 메서드로(`markRejected()`, `advanceTo(Instant)`).
- enum 상태 판단은 enum에게 묻는다(`status.isRejected()`, `cvssVersion.isUnrated()`).
- **점수·확률은 `BigDecimal`** (`trend_score`, `epss_score`, `epss_percentile`, `cvss_score`). `double`/`Double` 로 계산·비교하지 않는다. DB 컬럼은 `numeric`, 비교는 `compareTo`.
- 바뀌면 안 되는 값은 `final`로 둔다. 메서드는 호출 순서대로 위에서 아래로 배치한다.
- 주석은 한국어, 로그는 누구나 읽을 수 있는 영어로 쓴다.
- **로그에 프레임워크·외부 예외의 `getMessage()`를 넣지 않는다**(`check-conventions` 규칙 11). HTTP 클라이언트·Jackson 의 문장에는 응답 본문과 **API 키가 든 요청 URL·헤더**가 그대로 들어 있다. 고정 문장 + 예외 종류 이름(`getClass().getSimpleName()`) + 소스 이름·구간만. 우리 예외(`global/error` 의 `VulnTrendException` 계열)는 문장이 고정이라 허용. `NVD_API_KEY` 값은 어떤 로그에도 남기지 않는다.
- import는 static 먼저, 그다음 일반 import를 알파벳순으로 쓰고, 쓰지 않는 import는 지운다.

## 6. 배치 실행·저장 (기획서 6장 실패 규칙을 코드 규칙으로)

| 상황 | 방법 | 근거 |
|---|---|---|
| 1~4단계(NVD·KEV/EPSS·OSV·RSS) 중 하나라도 실패 | 5단계(점수) 이후를 실행하지 않는다. 러너는 단계 결과 객체를 받아 **early return**. 반쪽 JSON을 push하지 않는다 | 기획서 6장 실패 규칙 |
| 요약(7단계) 실패 | 치명적이지 않음 — 로그만 남기고 8·9단계 진행. 일 상한 20콜 초과도 같은 취급 | 기획서 6장 |
| 수집 진행 기록(`sync_state.last_synced_at`) | **성공한 실행에서만** 전진. 실패 시 `status=FAIL` 기록, 커서는 그대로 → 다음 실행이 자동 재시도 | 실패한 소스를 건너뛰면 그 구간이 영영 빈다 |
| 같은 구간 두 번 실행 | 중복·누락 없음(멱등). upsert 는 `on conflict (vuln_id) do update`, 자식 테이블은 `on conflict do nothing` 또는 구간 삭제 후 삽입 | WBS 1.4 |
| NVD 요청 구간 | 120일 상한 자동 분할, 페이지네이션(`startIndex`), 키 있을 때 30초 50회 한도, 429/5xx 지수 백오프 재시도 | 기획서 3장 NVD 주의사항 |
| `daily_stat` | 최근 14일 행 **전부** 재계산. 당일만 갱신하지 않는다 | NVD 가 과거 날짜로 소급 발행 |
| NVD `vulnStatus=Rejected` | 신규 건은 적재하지 않고, 기존 건은 `rejected=true` 로 바꾸고 per-CVE JSON 파일을 지운다 | 기획서 3장 수집 규칙 |
| CVSS 선택 | NVD v3.1 > CNA v3.1 > v4, 없으면 `UNRATED`. 선택 순서는 **도메인 객체 하나**(`CvssSelection`)가 맡고 테스트로 못 박는다 | 기획서 S2 |
| JSON 내보내기 | 내용이 바뀐 파일만 쓴다(기존 파일과 바이트 비교). `meta.json` 은 마지막에 쓴다 | push 시간이 파일 수에 비례(리스크 R3) |
| git push | `ProcessBuilder` 로 `git add -A && git commit && git push`. 종료 코드로 성공을 판정하고 출력 문구를 믿지 않는다. 실패하면 `PublishException` | 기획서 6장 9번 |
| 한국어 요약 입력 | NVD description + 영향 제품·버전 범위만 넘긴다. 출력은 `{what, who, action}` 각 1문장. 명령어·스크립트·설정값이 들어오면 **버리고** 재생성하지 않는다(일 상한 보존) | 기획서 6장 요약 가드 |
| `null`일 수 있는 숫자·시각 입력(EPSS 없음, `published_at` 없음) | `Optional`/명시적 부재 값으로 받고, 0 으로 대체하지 않는다 | `Number(null)===0` 계열 사고 |
| "로그·JSON 에 값이 없다"를 문서에 쓸 때 | OutputCapture 로 찍어 본 **테스트 이름을 문서에 함께** 적는다. 역패치로 그 테스트가 실패하는지 본다 | 추론으로 "나오지 않는다"고 썼다가 실측에서 비밀 값이 로그에 남은 일 |

## 7. 작업 순서 (TDD, WBS 작업 묶음(D2) 하나)

1. 설계 문서(`vuln-trend-plan.md` 5장 스키마·6장 흐름·7장 JSON, `vuln-trend-wbs.md` 해당 묶음)에서 이번 범위를 확인한다. 없거나 다르면 먼저 WBS 를 고친다.
2. **Red:** `domain-test-writing` 스킬로 테스트를 먼저 쓰고, 실패를 직접 확인한다. 컴파일 실패도 실패로 친다.
3. **Green:** 최소 구현. Flyway 마이그레이션은 기획서 5장과 같은 이름·타입·제약으로 쓴다.
4. **Refactor:** 중복 제거와 규칙 점검을 한다.
5. `bash scripts/check-all.sh`가 **종료 코드 0**이어야 끝난다. 출력 문구가 아니라 종료 코드와 테스트 개수로 판단한다.
6. 바뀐 컬럼·제약·JSON 필드는 같은 작업 안에서 기획서(5장·7장)에 반영하고 WBS §13 변경 이력에 남긴다.

## 8. 하지 말 것 (이전 프로젝트에서 실제로 일어난 사고)

- **확인하지 않은 가정:** "Boot BOM이 관리하겠지", "`numeric` 도 JDBC 매핑이 되겠지", "NVD 가 UTC 로 주겠지". 라이브러리와 외부 API 의 동작은 문서·실제 응답으로 먼저 확인한다.
- **파일 삭제:** `rm`은 거부된다. 파일 이동은 `git mv`로 한다. 지워야 할 파일이 생기면 사용자에게 명령(`! rm …`)을 바로 알린다. 시험용 파일은 저장소 밖(스크래치패드)에 만든다.
- 실행 명령 하나에 여러 작업과 `rm`을 섞지 않는다(통째로 거부된다).
- 테스트 하나에 규칙 두 개를 넣지 않는다.
- **규칙과 부딪혀 다른 방식을 택했으면, 사용자 결정 전까지 문서에 확정처럼 쓰지 않는다.** 주석·문서에는 "결정 대기"로 적고 보고에 올린다.
- setter 검사는 외부 API가 강제하는 `@Override` 재정의를 잡지 않는다. 이걸 피하려고 설계를 우회하지 않는다.

## 9. web/ (Vite + React + TS) — 결정 대기

가져온 하네스에는 Java 규칙만 있다. 프론트 규칙(ESLint·Prettier·Tailwind 클래스 순서·컴포넌트 파일 구조)은 **Phase 5 착수 전**에 정하고 이 절을 채운다. 그때까지 공통으로 적용하는 것은 §5 의 언어 무관 항목만이다: early return, 중첩 조건 분리, 바뀌지 않는 값은 `const`, 주석 한국어·로그 영어, 쓰지 않는 import 삭제.
