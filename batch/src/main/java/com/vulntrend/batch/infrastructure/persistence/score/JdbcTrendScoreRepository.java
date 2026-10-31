package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.score.ScoreBreakdown;
import com.vulntrend.batch.domain.score.TrendScoreRepository;
import com.vulntrend.batch.domain.vulnerability.CveId;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 트렌드 점수(기획서 6장 MVP 점수식, WBS 3.1). 점수 = (KEV 50 + 랜섬웨어 10 + EPSS×30 + CVSS) × 0.5^(경과일/30).
 * - 경과일 기준은 greatest(published_at, kev_added_at, last_mention_at)(P1). greatest 는 비어 있는 값을 건너뛴다 —
 *   기획서 SQL 의 coalesce 와 같은 뜻이다. 셋 다 없으면 감쇠하지 않는다(비율 1).
 * - kev_added_at 은 date 라서 세계 표준시 자정으로 바꿔 센다. ::timestamptz 로 바꾸면 저장소 세션 시간대(KST 등)를 타서 9시간 어긋난다.
 * - 기준 시각이 이번 실행보다 늦으면 경과일을 0 으로 둔다(점수가 감쇠 전보다 커지지 않는다).
 * - 랜섬웨어 10점은 KEV 에 올라 있을 때만 센다. now 는 SQL now() 가 아니라 이번 실행 시각(Clock)이다.
 * 점수 갱신과 항별 기여도는 같은 식 조각을 써서 서로 어긋나지 않는다.
 */
@Repository
@RequiredArgsConstructor
public class JdbcTrendScoreRepository implements TrendScoreRepository {

    private static final String KEV_TERM = "(case when coalesce(is_kev, false) then 50 else 0 end)";
    private static final String RANSOMWARE_TERM =
            "(case when coalesce(is_kev, false) and coalesce(kev_ransomware, false) then 10 else 0 end)";
    private static final String EPSS_TERM = "(coalesce(epss_score, 0) * 30)";
    private static final String CVSS_TERM = "coalesce(cvss_score, 0)";
    private static final String REFERENCE_TIME =
            "greatest(published_at, (kev_added_at::timestamp at time zone 'UTC'), last_mention_at)";
    private static final String AGE_DAYS =
            "greatest(0, extract(epoch from (cast(:now as timestamptz) - " + REFERENCE_TIME + ")) / 86400)";
    private static final String DECAY_FACTOR = "coalesce(power(0.5::numeric, " + AGE_DAYS + " / 30.0), 1)";

    private static final String RESCORE_ALL = "update vulnerability set trend_score = ("
            + KEV_TERM + " + " + RANSOMWARE_TERM + " + " + EPSS_TERM + " + " + CVSS_TERM + ") * " + DECAY_FACTOR
            + " where coalesce(rejected, false) = false";

    private static final String BREAKDOWNS = "select vuln_id, "
            + KEV_TERM + " as kev, " + RANSOMWARE_TERM + " as ransomware, " + EPSS_TERM + " as epss, "
            + CVSS_TERM + " as cvss, " + DECAY_FACTOR + " as decay_factor"
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
            found.put(CveId.from(rs.getString("vuln_id")), toBreakdown(rs));
        });
        return Map.copyOf(found);
    }

    private static ScoreBreakdown toBreakdown(ResultSet rs) throws SQLException {
        return ScoreBreakdown.of(rs.getBigDecimal("kev"), rs.getBigDecimal("ransomware"), rs.getBigDecimal("epss"),
                rs.getBigDecimal("cvss"), rs.getBigDecimal("decay_factor"));
    }
}
