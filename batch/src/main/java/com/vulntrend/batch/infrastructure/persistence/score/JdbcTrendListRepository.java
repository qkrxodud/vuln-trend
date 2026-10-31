package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.score.TrendListRepository;
import com.vulntrend.batch.domain.score.dto.TrendListing;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.Severity;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 트렌드 목록 조회(WBS 4.2 index·recent). 순서·대상은 TrendOrder, 점수 구성은 TrendScoreSql 조각을 같은 실행 시각으로 계산한다
 * — 정렬에 쓰는 저장된 trend_score 와 같은 식이다. 요약(summary.what)은 있으면 붙인다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcTrendListRepository implements TrendListRepository {

    /** 목록 줄 하나의 열들(listing(rs) 와 짝). 상세 조회도 같은 열을 읽는다 */
    static final String COLUMNS = "v.vuln_id, v.title, v.is_kev, v.kev_ransomware, v.kev_due_date, "
            + "v.poc_count, v.mention_count, v.epss_score, v.epss_percentile, v.cvss_score, v.cvss_version, "
            + "v.cvss_severity, v.primary_product, v.published_at, s.what as summary_what, "
            + TrendScoreSql.BREAKDOWN_COLUMNS;
    static final String FROM = " from vulnerability v left join summary s on s.vuln_id = v.vuln_id where "
            + TrendOrder.LISTED;
    private static final String SELECT = "select " + COLUMNS + FROM;
    private static final String TOP = SELECT + " " + TrendOrder.ORDER_BY + " limit :limit";
    private static final String PUBLISHED_BETWEEN = SELECT + " and published_at >= :from and published_at < :to "
            + TrendOrder.ORDER_BY + " limit :limit";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public List<TrendListing> top(int limit, Instant now) {
        return jdbc.query(TOP, params(limit, now), JdbcTrendListRepository::listing);
    }

    @Override
    public List<TrendListing> publishedBetween(Instant from, Instant to, int limit, Instant now) {
        MapSqlParameterSource params = params(limit, now)
                .addValue("from", from.atOffset(ZoneOffset.UTC))
                .addValue("to", to.atOffset(ZoneOffset.UTC));
        return jdbc.query(PUBLISHED_BETWEEN, params, JdbcTrendListRepository::listing);
    }

    private static MapSqlParameterSource params(int limit, Instant now) {
        return new MapSqlParameterSource().addValue("limit", limit).addValue("now", now.atOffset(ZoneOffset.UTC));
    }

    static TrendListing listing(ResultSet rs, int rowNum) throws SQLException {
        return new TrendListing(CveId.from(rs.getString("vuln_id")), rs.getString("title"),
                TrendScoreSql.breakdown(rs), rs.getBoolean("is_kev"), rs.getBoolean("kev_ransomware"),
                rs.getObject("kev_due_date", LocalDate.class), rs.getInt("poc_count"), rs.getInt("mention_count"),
                rs.getBigDecimal("epss_score"), rs.getBigDecimal("epss_percentile"), rs.getBigDecimal("cvss_score"),
                rs.getString("cvss_version"), severity(rs.getString("cvss_severity")), rs.getString("primary_product"),
                rs.getString("summary_what"),
                Optional.ofNullable(rs.getObject("published_at", OffsetDateTime.class))
                        .map(OffsetDateTime::toInstant).orElse(null));
    }

    private static Severity severity(String stored) {
        return Optional.ofNullable(stored).map(Severity::valueOf).orElse(Severity.UNRATED);
    }
}
