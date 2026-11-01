package com.vulntrend.batch.infrastructure.persistence.score;

import com.vulntrend.batch.domain.export.CategoryRepository;
import com.vulntrend.batch.domain.export.dto.CategoryCount;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 분류별 건수(categories.json, 사용자 결정 2026-11-01). 철회 건은 빼고, 창은 [시작, to). KEV 수는 기간과 상관없는 전체 수.
 * 공격 유형은 배열을 펼쳐 유형마다 센다(한 CVE 가 여러 유형에 든다). 분류 전(제품군 없음·유형 없음)은 어느 분류에도 들지 않는다
 */
@Repository
@RequiredArgsConstructor
public class JdbcCategoryRepository implements CategoryRepository {

    private static final String COUNTS = """
            count(*) filter (where published_at >= :weekFrom and published_at < :to) as count_7d,
            count(*) filter (where published_at >= :monthFrom and published_at < :to) as count_30d,
            count(*) filter (where coalesce(is_kev, false)) as kev_count
            """;
    private static final String FAMILY_COUNTS = "select product_family as key, " + COUNTS
            + " from vulnerability where " + TrendOrder.LISTED + " and product_family is not null group by product_family";
    private static final String ATTACK_TYPE_COUNTS = "select attack_type as key, " + COUNTS
            + " from vulnerability cross join lateral unnest(attack_types) as attack_type where " + TrendOrder.LISTED
            + " group by attack_type";

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public Map<String, CategoryCount> familyCounts(Instant weekFrom, Instant monthFrom, Instant to) {
        return counts(FAMILY_COUNTS, weekFrom, monthFrom, to);
    }

    @Override
    public Map<String, CategoryCount> attackTypeCounts(Instant weekFrom, Instant monthFrom, Instant to) {
        return counts(ATTACK_TYPE_COUNTS, weekFrom, monthFrom, to);
    }

    private Map<String, CategoryCount> counts(String sql, Instant weekFrom, Instant monthFrom, Instant to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("weekFrom", weekFrom.atOffset(ZoneOffset.UTC))
                .addValue("monthFrom", monthFrom.atOffset(ZoneOffset.UTC))
                .addValue("to", to.atOffset(ZoneOffset.UTC));
        Map<String, CategoryCount> counts = new LinkedHashMap<>();
        jdbc.query(sql, params, rs -> {
            counts.put(rs.getString("key"),
                    new CategoryCount(rs.getInt("count_7d"), rs.getInt("count_30d"), rs.getInt("kev_count")));
        });
        return Map.copyOf(counts);
    }
}
