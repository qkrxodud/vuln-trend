package com.vulntrend.batch.infrastructure.persistence.stat;

import com.vulntrend.batch.domain.stat.KpiRepository;
import com.vulntrend.batch.domain.stat.KpiWindow;
import com.vulntrend.batch.domain.stat.NewArrivals;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * KPI 집계(기획서 8장 KPI 표, WBS 3.4). 창은 [from, to). 철회된 CVE 는 세지 않는다.
 * - KEV 신규: KEV 에 올라 있는 CVE 의 kev_added_at 을 세계 표준시 자정으로 보고 창 안에 든 것(트렌드 점수와 같은 기준)
 * - Critical: 창 안 신규 중 cvss_severity = CRITICAL(= CVSS 9.0 이상)
 * - 고위험: 지금은 전체 중 EPSS 0.5 이상, 지난 날은 daily_stat 에 남은 수(일별 통계와 같은 기준)
 */
@Repository
@RequiredArgsConstructor
public class JdbcKpiRepository implements KpiRepository {

    private static final String NEW_ARRIVALS = """
            select count(*) filter (where published_at >= :from and published_at < :to) as new_count,
                   count(*) filter (where coalesce(is_kev, false)
                                      and (kev_added_at::timestamp at time zone 'UTC') >= :from
                                      and (kev_added_at::timestamp at time zone 'UTC') < :to) as kev_new_count,
                   count(*) filter (where published_at >= :from and published_at < :to
                                      and cvss_severity = 'CRITICAL') as critical_count
            from vulnerability
            where coalesce(rejected, false) = false
            """;
    private static final String HIGH_RISK_NOW = """
            select count(*) from vulnerability where coalesce(rejected, false) = false and epss_score >= 0.5
            """;
    private static final String HIGH_RISK_RECORDED = """
            select high_risk_count from daily_stat where stat_date = :day and high_risk_count is not null
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public NewArrivals newArrivals(KpiWindow window) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", window.getFrom().atOffset(ZoneOffset.UTC))
                .addValue("to", window.getTo().atOffset(ZoneOffset.UTC));
        return jdbc.queryForObject(NEW_ARRIVALS, params, (rs, rowNum) -> NewArrivals.of(
                rs.getInt("new_count"), rs.getInt("kev_new_count"), rs.getInt("critical_count")));
    }

    @Override
    public int highRiskNow() {
        return jdbc.queryForObject(HIGH_RISK_NOW, new MapSqlParameterSource(), Integer.class);
    }

    @Override
    public Optional<Integer> highRiskRecordedOn(LocalDate day) {
        return jdbc.queryForList(HIGH_RISK_RECORDED, new MapSqlParameterSource("day", day), Integer.class)
                .stream()
                .findFirst();
    }
}
