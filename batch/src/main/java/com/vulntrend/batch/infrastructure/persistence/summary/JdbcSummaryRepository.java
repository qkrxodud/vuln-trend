package com.vulntrend.batch.infrastructure.persistence.summary;

import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.SummaryOutcome;
import com.vulntrend.batch.domain.summary.SummaryProduct;
import com.vulntrend.batch.domain.summary.SummaryRepository;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.score.TrendOrder;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 요약 저장소(WBS 6.3).
 * - 대상: 트렌드 점수 상위 top 건을 먼저 정하고(철회된 CVE 제외), 그중 요약이 없고 다시 부르지 않을 만큼의 기록도 없는 것만.
 *   몇 번이면 그만 부르는지는 SummaryOutcome.finalAfter(만든 것·거절 1번, 버림 2번, 실패는 제한 없음).
 *   "요약 없는 것 중 상위 20건"이 아니다 — 사이트 상위 목록에 요약이 붙는 것이 목적이다(기획서 6장).
 * - 오늘 호출은 답을 받은 것(하루 상한)과 실패한 것(실패 안전 상한)을 따로 센다.
 * - 요약은 처음 한 번만 남긴다(on conflict do nothing). 제목은 아직 설명에서 만든 임시 제목일 때만 요약 제목으로 바꾼다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcSummaryRepository implements SummaryRepository {

    // 사이트 상위 목록(index.json)과 같은 순서·같은 철회 제외 조건을 쓴다(백엔드 B 의 TrendOrder)
    private static final String TOP_WITHOUT_SUMMARY = """
            with top as (
                select vuln_id, description, cvss_score, trend_score, published_at
                from vulnerability
                where %s
                %s
                limit :top
            )
            select vuln_id, description, cvss_score
            from top
            where description is not null
              and not exists (select 1 from summary s where s.vuln_id = top.vuln_id)
              and not exists (
                  select 1 from summary_call c where c.vuln_id = top.vuln_id
                  group by c.outcome
                  having count(*) >= %s
              )
            %s
            """.formatted(TrendOrder.LISTED, TrendOrder.ORDER_BY, finalAfterByOutcome(), TrendOrder.ORDER_BY);
    private static final String PRODUCTS = """
            select vuln_id, vendor, product, vulnerable_range, fixed_version
            from affected_product where vuln_id in (:ids) order by vuln_id, vendor, product
            """;
    private static final String COUNT_CALLS = """
            select count(*) from summary_call where called_at >= :since and outcome in (:outcomes)
            """;
    private static final String RECORD_CALL = """
            insert into summary_call (vuln_id, called_at, outcome, model) values (:id, :calledAt, :outcome, :model)
            on conflict do nothing
            """;
    private static final String INSERT_SUMMARY = """
            insert into summary (vuln_id, what, who, action, model, generated_at)
            values (:id, :what, :who, :action, :model, :generatedAt)
            on conflict (vuln_id) do nothing
            """;
    private static final String RAISE_TITLE = """
            update vulnerability set title = :summaryTitle
            where vuln_id = :id and title is not distinct from :descriptionTitle
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<SummarySource> findTopWithoutSummary(int top) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("top", top);
        List<Row> rows = jdbc.query(TOP_WITHOUT_SUMMARY, params, (rs, i) -> Row.of(rs));
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<SummaryProduct>> products = productsOf(rows);
        return rows.stream()
                .map(row -> SummarySource.of(CveId.from(row.id), row.description,
                        products.getOrDefault(row.id, List.of()), row.cvssScore))
                .toList();
    }

    @Override
    public int countAnsweredSince(Instant since) {
        return countCallsSince(since, answeredOutcomes());
    }

    @Override
    public int countFailedSince(Instant since) {
        return countCallsSince(since, failedOutcomes());
    }

    @Override
    public void recordCall(CveId cveId, SummaryOutcome outcome, String model, Instant calledAt) {
        jdbc.update(RECORD_CALL, new MapSqlParameterSource()
                .addValue("id", cveId.getValue())
                .addValue("calledAt", toTimestamp(calledAt))
                .addValue("outcome", outcome.name())
                .addValue("model", model));
    }

    @Override
    @Transactional
    public void saveSummary(CveId cveId, KoreanSummary summary, String model, Instant generatedAt,
            String descriptionTitle, String summaryTitle) {
        int inserted = jdbc.update(INSERT_SUMMARY, new MapSqlParameterSource()
                .addValue("id", cveId.getValue())
                .addValue("what", summary.getWhat())
                .addValue("who", summary.getWho())
                .addValue("action", summary.getAction())
                .addValue("model", model)
                .addValue("generatedAt", toTimestamp(generatedAt)));
        if (inserted == 0) {
            return;
        }
        jdbc.update(RAISE_TITLE, new MapSqlParameterSource()
                .addValue("id", cveId.getValue())
                .addValue("summaryTitle", summaryTitle)
                .addValue("descriptionTitle", descriptionTitle));
    }

    private int countCallsSince(Instant since, List<String> outcomes) {
        return jdbc.queryForObject(COUNT_CALLS, Map.of("since", toTimestamp(since), "outcomes", outcomes),
                Integer.class);
    }

    private Map<String, List<SummaryProduct>> productsOf(List<Row> rows) {
        List<String> ids = rows.stream().map(row -> row.id).toList();
        return jdbc.query(PRODUCTS, Map.of("ids", ids), (rs, i) -> Map.entry(rs.getString("vuln_id"),
                        SummaryProduct.of(rs.getString("vendor"), rs.getString("product"),
                                rs.getString("vulnerable_range"), rs.getString("fixed_version"))))
                .stream()
                .collect(Collectors.groupingBy(Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static List<String> answeredOutcomes() {
        return Arrays.stream(SummaryOutcome.values())
                .filter(Predicate.not(SummaryOutcome::isFailed))
                .map(SummaryOutcome::name)
                .toList();
    }

    private static List<String> failedOutcomes() {
        return Arrays.stream(SummaryOutcome.values())
                .filter(SummaryOutcome::isFailed)
                .map(SummaryOutcome::name)
                .toList();
    }

    /**
     * 결과별로 몇 번이면 그만 부르나를 SQL case 로(예: case c.outcome when 'GENERATED' then 1 … end).
     * 값은 enum 이름과 숫자뿐이라 바깥 글이 SQL 에 섞이지 않는다. 제한 없는 결과(실패)는 case 에 없어 null 이 되고, 걸리지 않는다
     */
    private static String finalAfterByOutcome() {
        return Arrays.stream(SummaryOutcome.values())
                .filter(outcome -> outcome.finalAfter().isPresent())
                .map(outcome -> "when '%s' then %d".formatted(outcome.name(), outcome.finalAfter().getAsInt()))
                .collect(Collectors.joining(" ", "case c.outcome ", " end"));
    }

    // timestamptz 는 OffsetDateTime 으로 주고받는다(pgjdbc 는 Instant 를 직접 받지 않는다)
    private static OffsetDateTime toTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    /** 대상 CVE 한 줄. 제품은 따로 모아 붙인다 */
    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static final class Row {

        private final String id;
        private final String description;
        private final BigDecimal cvssScore;

        private static Row of(ResultSet rs) throws SQLException {
            return new Row(rs.getString("vuln_id"), rs.getString("description"), rs.getBigDecimal("cvss_score"));
        }
    }
}
