package com.vulntrend.batch.infrastructure.persistence.stat;

import com.vulntrend.batch.domain.stat.DailyStat;
import com.vulntrend.batch.domain.stat.DailyStatRepository;
import com.vulntrend.batch.domain.stat.StatDays;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 일별 통계(기획서 5장 daily_stat·6장 6번, WBS 3.3). 한 문장으로 14일을 모두 다시 쓴다.
 * - 날짜는 published_at 의 한국 달력일. at time zone 'Asia/Seoul' 로 바꿔서 저장소 세션 시간대를 타지 않는다
 * - CVE 가 없는 날도 0 으로 쓴다(막대 차트의 빈 날). 철회된 CVE 는 세지 않는다
 * - 심각도는 cvss_severity 다섯 갈래로 나눈다. 비어 있으면 미평가로 센다. 다섯 갈래의 합 = 신규 수
 * - KEV 신규는 KEV 에 올라 있는 CVE 를 kev_added_at 날짜로 센다(발행일 아님)
 * - 고위험(EPSS 0.5 이상, 발행일 무관 전체)은 과거 값을 다시 알 수 없는 스냅숏이라 오늘 날짜에만 새로 쓰고,
 *   지난 날은 그 날 실행이 남긴 값을 둔다. 그 날 실행이 없었으면 비어 있다
 */
@Repository
@RequiredArgsConstructor
public class JdbcDailyStatRepository implements DailyStatRepository {

    private static final String RECALCULATE = """
            with days as (
                select cast(day as date) as stat_date
                from generate_series(cast(:first as date), cast(:today as date), interval '1 day') as day
            ),
            published as (
                select cast(published_at at time zone 'Asia/Seoul' as date) as stat_date,
                       count(*) as new_count,
                       count(*) filter (where cvss_severity = 'CRITICAL') as critical_count,
                       count(*) filter (where cvss_severity = 'HIGH') as high_count,
                       count(*) filter (where cvss_severity = 'MEDIUM') as medium_count,
                       count(*) filter (where cvss_severity = 'LOW') as low_count,
                       count(*) filter (where coalesce(cvss_severity, 'UNRATED') = 'UNRATED') as unrated_count
                from vulnerability
                where coalesce(rejected, false) = false
                  and published_at >= (cast(:first as date)::timestamp at time zone 'Asia/Seoul')
                  and published_at < ((cast(:today as date) + 1)::timestamp at time zone 'Asia/Seoul')
                group by 1
            ),
            kev as (
                select kev_added_at as stat_date, count(*) as kev_new_count
                from vulnerability
                where coalesce(rejected, false) = false
                  and coalesce(is_kev, false)
                  and kev_added_at between cast(:first as date) and cast(:today as date)
                group by 1
            ),
            high_risk as (
                select count(*) as high_risk_count
                from vulnerability
                where coalesce(rejected, false) = false and epss_score >= 0.5
            )
            insert into daily_stat (stat_date, new_count, kev_new_count, critical_count, high_risk_count,
                                    high_count, medium_count, low_count, unrated_count)
            select days.stat_date,
                   coalesce(published.new_count, 0),
                   coalesce(kev.kev_new_count, 0),
                   coalesce(published.critical_count, 0),
                   case when days.stat_date = cast(:today as date)
                        then (select high_risk_count from high_risk) end,
                   coalesce(published.high_count, 0),
                   coalesce(published.medium_count, 0),
                   coalesce(published.low_count, 0),
                   coalesce(published.unrated_count, 0)
            from days
            left join published using (stat_date)
            left join kev using (stat_date)
            on conflict (stat_date) do update set
                new_count = excluded.new_count,
                kev_new_count = excluded.kev_new_count,
                critical_count = excluded.critical_count,
                high_risk_count = coalesce(excluded.high_risk_count, daily_stat.high_risk_count),
                high_count = excluded.high_count,
                medium_count = excluded.medium_count,
                low_count = excluded.low_count,
                unrated_count = excluded.unrated_count
            """;

    /** 비어 있는 칸은 getInt 가 0 으로 읽는다 */
    private static final String FIND_BETWEEN = """
            select stat_date, new_count, critical_count, high_count, medium_count, low_count, unrated_count
            from daily_stat
            where stat_date between :first and :last
            order by stat_date
            """;

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    public int recalculate(StatDays days) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("first", days.getFirst())
                .addValue("today", days.getToday());
        return jdbc.update(RECALCULATE, params);
    }

    @Override
    public List<DailyStat> findBetween(LocalDate first, LocalDate last) {
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("first", first).addValue("last", last);
        return jdbc.query(FIND_BETWEEN, params, (rs, rowNum) -> DailyStat.of(rs.getObject("stat_date", LocalDate.class),
                rs.getInt("new_count"), rs.getInt("critical_count"), rs.getInt("high_count"), rs.getInt("medium_count"),
                rs.getInt("low_count"), rs.getInt("unrated_count")));
    }
}
