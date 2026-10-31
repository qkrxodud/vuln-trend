package com.vulntrend.batch.infrastructure.persistence.stat;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.stat.StatDays;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcDailyStatRepository.class})
@DisplayName("일별 통계 — 실제 저장 구조에서")
class JdbcDailyStatRepositoryTest {

    /** 한국 시각 2026-10-31 09:00 실행. 다시 셀 날은 10-18 ~ 10-31 */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final StatDays DAYS = StatDays.recent(NOW);

    @Autowired
    private JdbcDailyStatRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("최근 14일을 다시 세면")
    class Recalculating {

        @Test
        @DisplayName("CVE 가 없는 날까지 14일을 모두 0 으로 채워 쓴다")
        void fillsEveryDay() {
            int written = repository.recalculate(DAYS);

            assertThat(written).isEqualTo(14);
            assertThat(jdbc.queryForObject("select count(*) from daily_stat", Integer.class)).isEqualTo(14);
            assertThat(stat("2026-10-18")).containsEntry("new_count", 0).containsEntry("kev_new_count", 0)
                    .containsEntry("critical_count", 0).containsEntry("unrated_count", 0);
        }

        @Test
        @DisplayName("발행 시각을 한국 달력일로 나눠 센다")
        void byKoreanCalendarDay() {
            vulnerability("CVE-2026-30001", "2026-10-30T15:00:00Z", "CRITICAL");
            vulnerability("CVE-2026-30002", "2026-10-30T14:59:59Z", "CRITICAL");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-31")).containsEntry("new_count", 1);
            assertThat(stat("2026-10-30")).containsEntry("new_count", 1);
        }

        @Test
        @DisplayName("저장소 세션 시간대가 달라도 한국 달력일로 센다")
        void sessionTimeZoneDoesNotMatter() {
            jdbc.execute("set local time zone 'America/New_York'");
            vulnerability("CVE-2026-30001", "2026-10-30T15:00:00Z", "CRITICAL");
            vulnerability("CVE-2026-30002", "2026-10-17T15:00:00Z", "HIGH");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-31")).containsEntry("new_count", 1);
            assertThat(stat("2026-10-18")).containsEntry("new_count", 1);
        }

        @Test
        @DisplayName("그 날 신규를 심각도별로 나누고 다섯 갈래를 더하면 신규 수가 된다")
        void bySeverity() {
            vulnerability("CVE-2026-30001", "2026-10-29T01:00:00Z", "CRITICAL");
            vulnerability("CVE-2026-30002", "2026-10-29T02:00:00Z", "CRITICAL");
            vulnerability("CVE-2026-30003", "2026-10-29T03:00:00Z", "HIGH");
            vulnerability("CVE-2026-30004", "2026-10-29T04:00:00Z", "MEDIUM");
            vulnerability("CVE-2026-30005", "2026-10-29T05:00:00Z", "LOW");
            vulnerability("CVE-2026-30006", "2026-10-29T06:00:00Z", "UNRATED");
            vulnerability("CVE-2026-30007", "2026-10-29T07:00:00Z", null);

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-29")).containsEntry("new_count", 7).containsEntry("critical_count", 2)
                    .containsEntry("high_count", 1).containsEntry("medium_count", 1).containsEntry("low_count", 1)
                    .containsEntry("unrated_count", 2);
        }

        @Test
        @DisplayName("철회된 CVE 와 아직 오지 않은 날에 발행된 CVE 는 세지 않는다")
        void skipsRejectedAndFuture() {
            vulnerability("CVE-2026-30001", "2026-10-29T01:00:00Z", "HIGH");
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-30001'");
            vulnerability("CVE-2026-30002", "2026-10-31T15:00:00Z", "HIGH");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-29")).containsEntry("new_count", 0);
            assertThat(jdbc.queryForObject("select sum(new_count) from daily_stat", Integer.class)).isZero();
        }

        @Test
        @DisplayName("KEV 신규는 발행일이 아니라 KEV 등재일로 센다")
        void kevByAddedDate() {
            vulnerability("CVE-2021-44228", "2021-12-10T00:00:00Z", "CRITICAL");
            kev("CVE-2021-44228", "2026-10-20");
            vulnerability("CVE-2026-30001", "2026-10-20T01:00:00Z", "HIGH");
            kev("CVE-2026-30001", "2026-10-20");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-20")).containsEntry("kev_new_count", 2).containsEntry("new_count", 1);
        }

        @Test
        @DisplayName("NVD 가 지난 날짜로 늦게 발행한 CVE 도 다음 실행에서 그 날에 더해진다")
        void lateBackdatedPublication() {
            vulnerability("CVE-2026-30001", "2026-10-20T01:00:00Z", "HIGH");
            repository.recalculate(DAYS);
            vulnerability("CVE-2026-30002", "2026-10-20T02:00:00Z", "CRITICAL");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-20")).containsEntry("new_count", 2).containsEntry("critical_count", 1);
        }

        @Test
        @DisplayName("14일보다 오래된 날의 통계는 건드리지 않는다")
        void olderDaysKept() {
            jdbc.update("insert into daily_stat (stat_date, new_count, high_risk_count) values ('2026-10-17', 42, 7)");
            vulnerability("CVE-2026-30001", "2026-10-17T01:00:00Z", "HIGH");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-17")).containsEntry("new_count", 42).containsEntry("high_risk_count", 7);
        }
    }

    @Nested
    @DisplayName("고위험 수는")
    class HighRisk {

        @Test
        @DisplayName("오늘 날짜에 발행일과 상관없이 악용 확률 0.5 이상인 CVE 전체를 센다")
        void todaySnapshotOfAll() {
            vulnerability("CVE-2021-44228", "2021-12-10T00:00:00Z", "CRITICAL");
            epss("CVE-2021-44228", "0.99");
            vulnerability("CVE-2026-30001", "2026-10-30T01:00:00Z", "HIGH");
            epss("CVE-2026-30001", "0.5");
            vulnerability("CVE-2026-30002", "2026-10-30T02:00:00Z", "HIGH");
            epss("CVE-2026-30002", "0.49999");
            vulnerability("CVE-2026-30003", "2026-10-30T03:00:00Z", "HIGH");
            epss("CVE-2026-30003", "0.9");
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-30003'");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-31")).containsEntry("high_risk_count", 2);
        }

        @Test
        @DisplayName("지난 날은 그 날 실행에서 남긴 수를 그대로 둔다")
        void pastDaysKeepTheirSnapshot() {
            jdbc.update("insert into daily_stat (stat_date, new_count, high_risk_count) values ('2026-10-30', 0, 5)");
            vulnerability("CVE-2021-44228", "2021-12-10T00:00:00Z", "CRITICAL");
            epss("CVE-2021-44228", "0.99");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-30")).containsEntry("high_risk_count", 5);
            assertThat(stat("2026-10-31")).containsEntry("high_risk_count", 1);
        }

        @Test
        @DisplayName("그 날 실행한 적이 없는 지난 날은 알 수 없어 비워 둔다")
        void unknownPastSnapshot() {
            vulnerability("CVE-2021-44228", "2021-12-10T00:00:00Z", "CRITICAL");
            epss("CVE-2021-44228", "0.99");

            repository.recalculate(DAYS);

            assertThat(stat("2026-10-25").get("high_risk_count")).isNull();
        }

        @Test
        @DisplayName("같은 날 다시 실행하면 오늘 수를 새로 센 값으로 바꾼다")
        void sameDayRerun() {
            vulnerability("CVE-2021-44228", "2021-12-10T00:00:00Z", "CRITICAL");
            epss("CVE-2021-44228", "0.99");
            repository.recalculate(DAYS);
            epss("CVE-2021-44228", "0.1");

            repository.recalculate(StatDays.recent(NOW.plusSeconds(3600 * 9)));

            assertThat(stat("2026-10-31")).containsEntry("high_risk_count", 0);
        }
    }

    private Map<String, Object> stat(String date) {
        return jdbc.queryForMap("select * from daily_stat where stat_date = cast(? as date)", date);
    }

    private void vulnerability(String id, String published, String severity) {
        jdbc.update("insert into vulnerability (vuln_id, published_at, cvss_severity) values (?, ?, ?)", id,
                time(published), severity);
    }

    private void kev(String id, String added) {
        jdbc.update("update vulnerability set is_kev = true, kev_added_at = cast(? as date) where vuln_id = ?", added,
                id);
    }

    private void epss(String id, String score) {
        jdbc.update("update vulnerability set epss_score = ? where vuln_id = ?", new BigDecimal(score), id);
    }

    private static OffsetDateTime time(String text) {
        return Objects.isNull(text) ? null : Instant.parse(text).atOffset(ZoneOffset.UTC);
    }
}
