---
name: domain-test-writing
description: vuln-trend 배치의 테스트 코드를 작성·수정·리뷰할 때 반드시 사용. 새 수집기·값 객체·서비스·리포지토리·러너 테스트 작성, 기존 테스트 수정, @DisplayName·@Nested 이름 짓기, "테스트 써줘", "테스트 추가", "TDD로 진행", "Red 단계" 요청에 모두 적용. 테스트 이름을 개발 용어가 아니라 도메인 언어로 써서 테스트 리포트가 기획 문서처럼 읽히게 한다.
---

# 도메인 언어로 테스트 쓰기

테스트 리포트(`cd batch && ./gradlew test` → `build/reports/tests/test/index.html`)는 **이 배치가 어떻게 동작하는지 설명하는 문서**다.
개발자가 아닌 사람이 이름만 위에서 아래로 읽어도 규칙을 이해할 수 있어야 한다. (clarity-api 하네스의 같은 스킬을 가져와 용어 사전만 vuln-trend 로 바꿨다.)

## 1. 구조 = 문서의 목차

| 테스트 요소 | 문서에서의 역할 | 예 |
|---|---|---|
| 클래스 `@DisplayName` | 문서 제목 (도메인 개념 이름) | `취약점`, `트렌드 점수`, `NVD 수집` |
| `@Nested` 클래스 `@DisplayName` | 장 제목 (상황·행위) | `NVD 가 철회한 CVE 를 받으면` |
| 메서드 `@DisplayName` | 본문 문장 (규칙 하나) | `이미 알고 있던 CVE 면 철회 표시만 남기고 목록에서 내린다` |
| 파라미터 테스트 케이스 이름 | 예시 목록 | `심각도 점수가 없을 때` |

- 행위가 두 개 이상이면 `@Nested`로 장을 나눈다. 장이 하나뿐이면 `@Nested` 없이 써도 된다.
- 메서드 이름(영어)은 개발자용이라 자유롭게 짓되, 화면에 보이는 건 `@DisplayName`이다.

## 2. 문장 쓰는 법

1. **주어는 도메인의 등장인물이다.** 취약점(CVE), 수집기, 트렌드 점수, 실제 악용 목록(KEV), 일별 통계, 한국어 요약, 사이트 데이터 파일 등이 주어가 된다. 클래스·메서드·DB는 주어가 되지 않는다.
2. **"~하면 ~한다" 꼴로 조건과 결과를 함께 쓴다.** 결과만 쓰지 않는다.
   - ✗ `CVSS 선택 검증`
   - ✓ `NVD 와 제조사가 모두 3.1 점수를 냈으면 NVD 점수를 쓴다`
3. **왜 그런 규칙인지 사용자가 느끼는 결과로 쓴다.**
   - ✗ `sync_state 커서 미전진`
   - ✓ `수집이 실패한 소스는 다음 실행에서 같은 구간을 다시 가져온다`
4. **한 문장에 규칙 하나.** "그리고"로 서로 다른 규칙 두 개를 잇지 않는다.
5. **경계값은 숫자로 분명히 쓴다.** `수집 구간이 120일을 넘으면 120일씩 나눠 가져온다`
6. 존댓말이 아니라 **문서체 평서문**(`~한다`, `~된다`, `~없다`)으로 끝낸다.
7. 오류 상황은 "예외가 발생한다"가 아니라 **"받지 않는다 / 할 수 없다 / 멈춘다"**로 쓴다.

## 3. 쓰지 않는 말 → 바꿔 쓰는 말

| 쓰지 않음 (개발 용어) | 바꿔 쓰기 (도메인 언어) |
|---|---|
| 예외 발생, throw, Exception | 받지 않는다, 할 수 없다, 멈춘다 |
| null, 빈 문자열 | 값이 없을 때, 비어 있을 때 |
| DB, 테이블, 컬럼, 행, upsert | 취약점 목록, 수집 기록 (또는 생략) — "적재된다", "갱신된다" |
| 저장(save), 조회(find) | 목록에 오른다, 다시 찾는다 |
| ID, PK, vuln_id | CVE 번호 |
| 유일 제약, unique, 인덱스 | 두 번 올라갈 수 없다 |
| 엔티티, 객체, 인스턴스, 값 객체, DTO | 취약점, 점수 등 실제 개념 이름 |
| 마이그레이션, Flyway, JPA, JdbcTemplate, SQL, 매핑, 스키마 | 저장 구조, 설계 |
| 반환, 리턴, true/false | 나온다, 맞다/아니다 |
| 트랜잭션, 롤백 | 한꺼번에 반영된다, 하나도 반영되지 않는다 |
| 타임스탬프, timestamp | 발행 시각, 마지막 수정 시각, 수집 시각 |
| 배치 러너, CommandLineRunner | 하루 한 번 실행, 이번 실행 |

단, **고유명사인 소스·제품 이름**(NVD, CISA KEV, EPSS, OSV, GitHub, Chrome)과 **업계 표준 약어**(CVE, CVSS, JSON)는 그대로 쓴다. `check-display-names.sh` 가 금지 용어를 잡는다 — 짧은 영어 단어는 스크립트의 `WORDS` 목록에 넣는다(macOS grep 은 `\b` 를 모른다).

## 4. 용어 사전 (Ubiquitous Language)

코드 이름과 테스트 문장에서 부르는 이름을 맞춘다. 새 개념이 생기면 이 표에 먼저 추가한다. 아래는 기획서 5·6장에서 뽑은 초기 사전이고, 코드 이름은 구현하면서 채운다.

| 코드 (예정) | 테스트·문서에서 부르는 이름 |
|---|---|
| `Vulnerability` / `vulnerability` | 취약점 (문장 안에서는 "CVE") |
| `CveId` / `vuln_id` | CVE 번호 |
| `vulnerability_raw` | 원본 그대로 보관한 기록 |
| `rejected` / `vulnStatus=Rejected` | NVD 가 철회한 CVE |
| `CvssSelection` / `cvss_version` / `UNRATED` | 심각도 점수 선택 / 점수 출처 / 심각도 미평가 |
| `is_kev` / `kev_due_date` / `kev_ransomware` | 실제 악용 목록(KEV)에 오름 / 조치 기한 / 랜섬웨어에 쓰임 |
| `epss_score` / `epss_percentile` | 악용 확률(EPSS) / 악용 확률 순위 |
| `TrendScore` / `trend_score` | 트렌드 점수 (30일 반감 감쇠는 "시간이 지나면 점수가 줄어든다") |
| `WhyNowTemplate` / `why_now` | "왜 지금?" 설명 |
| `AffectedProduct` / `affected_product` / `vulnerable_range` / `fixed_version` | 영향받는 제품 / 영향받는 버전 범위 / 고친 버전 |
| `ProductAlias` / `product_alias` | 제품 이름 표준화 표 |
| `ecosystem` | 패키지 생태계 (npm, PyPI, Packagist …) |
| `ReferenceLink` / `reference_link(source=vendor|news)` | 참고 링크 / 제조사 안내문 / 뉴스 |
| `mention_count` | 최근 7일 뉴스 언급 수 |
| `Summary` / `summary(what, who, action)` | 한국어 요약 (무엇이 / 누가 영향 / 무엇을 해야) |
| `summary.daily-limit` | 하루 요약 상한(20건) |
| `DailyStat` / `daily_stat` | 일별 통계 (최근 14일을 다시 센다) |
| `SyncState` / `sync_state` / `last_synced_at` / `status=FAIL` | 수집 진행 기록 / 마지막으로 가져온 시각 / 실패한 실행 |
| `SyncWindow` | 수집 구간 (120일 상한) |
| `Collector` / `NvdCollector` / `KevCollector` / `EpssCollector` / `OsvCollector` / `NewsRssCollector` | 수집기 / NVD 수집 / KEV 수집 / EPSS 수집 / OSV 수집 / 뉴스 수집 |
| `SourceName` | 소스 (NVD, KEV, EPSS, OSV, 뉴스) |
| `NvdClient` / rate limit / 429 | NVD 호출 / 호출 한도(30초에 50번) / 너무 자주 불렀다는 응답 |
| 백필 | 지난 1년치 한 번 가져오기 |
| `CollectOutcome` / `hasFailure()` | 이번 수집 결과 / 실패한 소스가 있다 |
| `ScoreService` / `rescoreAll` | 점수 다시 매기기 |
| `SummaryService` | 상위 20건 요약 발급 |
| `JsonExporter` / `web/public/data/*.json` | 사이트 데이터 파일 (변경분만 다시 쓴다) |
| `meta.json` / `version` | 갱신 시각 정보 / 데이터 판 번호 |
| stale 배너 | 오래된 데이터 알림 |
| `GitPusher` / `PublishException` | 결과 게시 / 게시 실패 |
| `ExportSink` | 내보내는 곳 |
| `NvdProperties` / `nvd.api-key` | NVD 설정 / NVD 열쇠(값은 어디에도 찍지 않는다) |
| `BatchRunner` | 하루 한 번 실행 |
| `*JdbcTest` / `*TransactionTest` | "— 실제 저장 구조에서" (가짜 저장소로는 확인할 수 없는 동작) |
| OutputCapture 로그 단언 | 실행 기록에 남기지 않는다 |

## 5. 본문(given/when/then)

- 주석 `// given`, `// when`, `// then` 뒤에 **한 줄 도메인 설명**을 붙여도 좋다.
  `// given: 어제 KEV 목록에 오른 CVE`
- 테스트 데이터도 도메인처럼 보이게 쓴다: `"CVE-2026-12345"`, `"Chrome"`, `"Packagist"`. 의미 없는 `"a"`, `"test"`는 길이 경계 테스트처럼 값 자체가 중요하지 않을 때만 쓴다.
- 검증은 AssertJ `assertThat`으로 **값까지** 확인한다. 점수는 `BigDecimal` 이므로 `isEqualByComparingTo` 로 비교한다.

## 6. 파라미터 테스트

케이스마다 이름을 붙여 예시 목록처럼 읽히게 한다. 원시 값(`null`, `""`)이 화면에 찍히지 않게 `Named`를 쓴다.

```java
@ParameterizedTest(name = "{0}")
@MethodSource("invalidIds")
@DisplayName("CVE 번호 모양이 아니면 취약점으로 받지 않는다")
void rejectsInvalidId(String raw) { ... }

static Stream<Named<String>> invalidIds() {
    return Stream.of(
            named("아무 값도 없을 때", null),
            named("연도가 빠졌을 때", "CVE-12345"));
}
```

## 7. 인프라·기동 테스트

저장소나 기동처럼 기술적인 테스트도 **무엇이 보장되는지**를 운영 관점에서 쓴다.

- ✗ `Flyway V1 마이그레이션이 성공한다`
- ✓ `빈 저장소에서 처음 실행하면 저장 구조를 먼저 만들고 시작한다`
- ✗ `NVD_API_KEY 없으면 @PostConstruct 예외`
- ✓ `NVD 열쇠가 없으면 배치가 시작하지 않는다`

## 8. 완료 전 확인

```bash
bash scripts/check-display-names.sh                       # 금지 용어가 들어간 테스트 이름 검출
CHECK_TEST_DIR=/path/to/probe bash scripts/check-display-names.sh   # 스크립트 자체를 시험할 때(저장소에 시험 파일을 만들지 않는다)
```

- 0건이어야 한다. 정말 필요한 경우만 스크립트 허용 목록에 이유와 함께 추가한다.
- 테스트 리포트를 열어 클래스 → 장 → 문장 순서로 읽었을 때 문서처럼 이어지는지 확인한다.
