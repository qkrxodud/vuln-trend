package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.score.TrendScoreRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 트렌드 점수(WBS 3.1). 식 조각과 그 규칙은 TrendScoreSql 에 있다. */
@Repository
@RequiredArgsConstructor
public class JdbcTrendScoreRepository implements TrendScoreRepository {

    private static final String RESCORE_ALL = "update vulnerability set trend_score = " + TrendScoreSql.TREND_TOTAL
            + " where " + TrendOrder.LISTED;
    private static final String BREAKDOWNS = "select vuln_id, " + TrendScoreSql.BREAKDOWN_COLUMNS
            + " from vulnerability where vuln_id in (:ids)";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public int rescoreAll(Instant now) {
        return jdbc.update(RESCORE_ALL, new MapSqlParameterSource("now", now.atOffset(ZoneOffset.UTC)));
    }

    @Override
    public Map<CveId, ScoreBreakdown> breakdownsOf(Collection<CveId> ids, Instant now) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("now", now.atOffset(ZoneOffset.UTC))
                .addValue("ids", ids.stream().map(CveId::getValue).toList());
        Map<CveId, ScoreBreakdown> found = new LinkedHashMap<>();
        jdbc.query(BREAKDOWNS, params, (ResultSet rs) -> {
            found.put(CveId.from(rs.getString("vuln_id")), TrendScoreSql.breakdown(rs));
        });
        return Map.copyOf(found);
    }
}
