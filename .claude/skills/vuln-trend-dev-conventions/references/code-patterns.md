# 코드 모양 템플릿

필요한 종류만 골라 본다. clarity-api 의 실제 코드에서 가져온 모양을 vuln-trend 의 개념 이름으로 바꿨다. 아직 구현 전이라 **모양만** 참고하고, 필드·메서드 이름은 기획서 5장 스키마를 따른다.

## 목차
1. 값 객체
2. 도메인 모델(배치용 — JPA 아님)
3. 리포지토리(도메인 인터페이스 + JdbcTemplate 어댑터, upsert)
4. 수집기(`Collector` 창구 + 소스별 구현)
5. 서비스
6. 설정 값이 필요한 빈(`@ConfigurationProperties`)
7. 시작 시 검사가 필요한 빈(`@PostConstruct`)
8. 빈이 아닌 결과 객체(정적 팩터리)
9. DTO(record)
10. 예외
11. 러너(`CommandLineRunner`)
12. JPA 엔티티(조회에 JPA 를 쓸 때만)

---

## 1. 값 객체

```java
@Getter
@EqualsAndHashCode
public class CveId {

    private static final Pattern FORMAT = Pattern.compile("CVE-\\d{4}-\\d{4,}");

    private final String value;

    @Builder(access = AccessLevel.PRIVATE)
    private CveId(String value) {
        validate(value);                  // 검사는 private 생성자 안에서
        this.value = value;
    }

    public static CveId from(String raw) {
        return CveId.builder().value(raw.trim().toUpperCase(Locale.ROOT)).build();
    }

    private static void validate(String value) {
        if (isNotCveFormat(value)) {      // if (!…) 대신 '아니다' 도우미
            throw new DomainValidationException("A CVE id must look like CVE-YYYY-NNNN.");
        }
    }

    private static boolean isNotCveFormat(String value) {
        return Objects.isNull(value) || Predicate.not(FORMAT.asMatchPredicate()).test(value.trim());
    }
}
```

`!` 를 `== false` 로 바꾸는 것은 규칙을 피하는 꼼수다. `Predicate.not` 이나 `isNot…` 이름의 도우미로 "아니다"를 이름에 드러낸다.

## 2. 도메인 모델(배치용)

JdbcTemplate 으로 읽고 쓰므로 JPA 애너테이션이 없다. 상태 변화는 행위 이름 메서드로.

```java
@Getter
public class Vulnerability {

    private final CveId id;
    private final Instant publishedAt;
    private final CvssSelection cvss;         // 버전·점수·UNRATED 여부를 아는 값 객체
    private boolean rejected;
    private TrendScore trendScore;

    @Builder(access = AccessLevel.PRIVATE)
    private Vulnerability(CveId id, Instant publishedAt, CvssSelection cvss, boolean rejected, TrendScore trendScore) { … }

    public static Vulnerability fromNvd(NvdItem item, Instant now) {   // 생성 규칙은 팩터리 안에서
        if (item.isRejected()) {
            throw new DomainValidationException("A rejected CVE is not registered as new.");
        }
        return Vulnerability.builder()…build();
    }

    public void markRejected() { this.rejected = true; }                 // setter 대신 행위 이름
    public void rescore(TrendScore score) { this.trendScore = score; }
}
```

## 3. 리포지토리

```java
// domain/vulnerability/VulnerabilityRepository.java
public interface VulnerabilityRepository {
    int upsertAll(List<Vulnerability> vulnerabilities);     // 적재 건수를 돌려준다(로그·DoD 확인용)
    Optional<Vulnerability> findById(CveId id);
}

// infrastructure/persistence/vulnerability/JdbcVulnerabilityRepository.java
@Repository
@RequiredArgsConstructor
public class JdbcVulnerabilityRepository implements VulnerabilityRepository {

    private static final String UPSERT = """
            insert into vulnerability (vuln_id, published_at, cvss_score, cvss_version, rejected)
            values (:id, :publishedAt, :cvssScore, :cvssVersion, :rejected)
            on conflict (vuln_id) do update set
                published_at = excluded.published_at,
                cvss_score   = excluded.cvss_score,
                cvss_version = excluded.cvss_version,
                rejected     = excluded.rejected
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public int upsertAll(List<Vulnerability> vulnerabilities) {
        SqlParameterSource[] rows = vulnerabilities.stream().map(this::toRow).toArray(SqlParameterSource[]::new);
        return Arrays.stream(jdbc.batchUpdate(UPSERT, rows)).sum();
    }

    private SqlParameterSource toRow(Vulnerability v) { … BigDecimal 은 그대로 numeric 으로 … }
}
```

점수 갱신처럼 "SQL 한 방"인 작업도 리포지토리 메서드(`rescoreAll(Instant now)`)로 두고, SQL 문자열은 그 클래스의 `static final` 상수로 둔다. 도메인·애플리케이션은 SQL 을 모른다.

## 4. 수집기

```java
// domain/collect/Collector.java — 창구. 소스 이름과 "구간을 수집한다"만 안다
public interface Collector {
    SourceName source();
    CollectResult collect(SyncWindow window);
}

// infrastructure/nvd/NvdCollector.java
@Component
@RequiredArgsConstructor
public class NvdCollector implements Collector {

    private final NvdClient client;                       // HTTP 호출만 하는 얇은 클래스
    private final VulnerabilityRepository vulnerabilities;
    private final NvdProperties properties;

    @Override
    public SourceName source() { return SourceName.NVD; }

    @Override
    public CollectResult collect(SyncWindow window) {
        List<Vulnerability> loaded = new ArrayList<>();
        for (SyncWindow slice : window.splitBy(properties.getMaxWindow())) {   // 120일 상한 분할은 값 객체가
            loaded.addAll(client.fetch(slice).stream().filter(Predicate.not(NvdItem::isRejected)).map(…).toList());
        }
        return CollectResult.loaded(source(), vulnerabilities.upsertAll(loaded));
    }
}
```

애플리케이션 서비스는 `List<Collector>` 를 주입받아 순회한다. 소스 추가 = 구현체 하나 추가.

## 5. 서비스

```java
@Service
@RequiredArgsConstructor
public class CollectService {

    private final List<Collector> collectors;
    private final SyncStateRepository syncStates;
    private final Clock clock;                       // 지금 시각은 Clock 에서

    @Transactional
    public CollectOutcome collectAll() {
        Instant now = clock.instant();
        List<CollectResult> results = new ArrayList<>();
        for (Collector collector : collectors) {
            results.add(collectOne(collector, now));           // 중첩 if 는 메서드로
        }
        return CollectOutcome.of(results);
    }

    private CollectResult collectOne(Collector collector, Instant now) {
        SyncState state = syncStates.findBySource(collector.source());
        try {
            CollectResult result = collector.collect(state.windowUntil(now));
            syncStates.save(state.advancedTo(now));            // 성공한 실행에서만 커서 전진
            return result;
        } catch (SourceFetchException failed) {
            syncStates.save(state.failed(now));                // 커서는 그대로 → 다음 실행이 재시도
            log.warn("Source {} did not complete this run: {}", collector.source(), failed.getMessage()); // 우리 예외라 허용
            return CollectResult.failed(collector.source());
        }
    }
}
```

## 6. 설정 값이 필요한 빈

```java
// global/config/NvdProperties.java — 생성자 바인딩은 Lombok 생성자 하나로 충분하다
@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "nvd")
public class NvdProperties {
    private final String apiKey;
    private final Duration maxWindow;        // 120d
    private final int resultsPerPage;
}
```

`@ConfigurationPropertiesScan` 으로 등록한다. 기본값은 `application.properties` 에 둔다. `nvd.api-key=${NVD_API_KEY:}` 처럼 **기본값을 비워 두고** §7 에서 거부한다.

## 7. 시작 시 검사가 필요한 빈

```java
@Component
@RequiredArgsConstructor
public class NvdClient {

    private final NvdProperties properties;

    @PostConstruct
    void requireApiKey() {
        if (isBlank(properties.getApiKey())) {
            throw new IllegalStateException("NVD collection needs NVD_API_KEY. Set it in .env before running the batch.");
        }
    }
}
```

테스트: 실제 `application.properties` 를 올린 컨텍스트에서 `NVD_API_KEY` 를 빼고 시작이 거부되는지 본다. 검사 메서드만 직접 부르는 테스트는 `@PostConstruct` 를 빼도 통과하므로 부족하다.

## 8. 빈이 아닌 결과 객체

```java
@Getter
public class CollectOutcome {

    private final List<CollectResult> results;       // 불변 List

    @Builder(access = AccessLevel.PRIVATE)
    private CollectOutcome(List<CollectResult> results) {
        this.results = List.copyOf(results);
    }

    public static CollectOutcome of(Collection<CollectResult> results) {
        return CollectOutcome.builder().results(List.copyOf(results)).build();
    }

    public boolean hasFailure() { return results.stream().anyMatch(CollectResult::isFailed); }   // 러너는 이 질문만 한다
}
```

## 9. DTO

```java
// infrastructure/nvd/dto/NvdResponse.java — record 는 dto 패키지에서만. Jackson 이 채우는 외부 응답 모양
public record NvdResponse(int totalResults, int startIndex, List<NvdVulnerabilityEnvelope> vulnerabilities) {
}

// application/export/dto/ExportCommand.java
public record ExportCommand(Path outputDir, Instant generatedAt) {
}
```

## 10. 예외

```java
// global/error/ErrorCode.java 에 먼저 추가
SOURCE_FETCH_FAILED(ErrorType.EXTERNAL, "A data source did not answer or answered badly."),

// global/error/exception/SourceFetchException.java
public class SourceFetchException extends VulnTrendException {
    public SourceFetchException(SourceName source, SyncWindow window) {
        super(ErrorCode.SOURCE_FETCH_FAILED, "Source " + source + " failed for window " + window + ".");
    }
}
```

상세 문장에 응답 본문·URL·API 키를 넣지 않는다(로그 규칙 11 과 같은 이유).

## 11. 러너

```java
@Component
@RequiredArgsConstructor
public class BatchRunner implements CommandLineRunner {

    private final CollectService collect;
    private final ScoreService score;
    private final SummaryService summary;
    private final ExportService export;

    @Override
    public void run(String... args) {
        CollectOutcome collected = collect.collectAll();
        if (collected.hasFailure()) {                    // 1~4 실패 → 5 이후 실행하지 않음(early return)
            log.warn("Collection did not complete; scoring and publishing are skipped this run.");
            return;
        }
        score.rescoreAll();
        summary.summarizeTopIfPossible();                // 실패해도 다음 단계로(치명적이지 않음)
        export.exportAndPublish();
    }
}
```

백필 같은 1회성 실행은 CLI 인자를 **값 객체**(`RunMode.from(args)`)로 바꾸고 러너는 그 값에게 묻는다.

## 12. JPA 엔티티(조회에 JPA 를 쓸 때만)

```java
@Entity
@Table(name = "daily_stat")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyStat {

    @Id
    @Column(name = "stat_date", nullable = false, updatable = false)
    private LocalDate statDate;

    @Builder(access = AccessLevel.PRIVATE)
    private DailyStat(LocalDate statDate, int newCount, int kevCount) { … }

    public static DailyStat of(LocalDate statDate, int newCount, int kevCount) { … }
}
```

식별자를 직접 정하는 엔티티는 `Persistable` + `isNew`(`@PostPersist`/`@PostLoad`에서 false)를 함께 둔다. 없으면 `save` 가 덮어쓰기(merge)로 동작한다.
