package com.vulntrend.batch.infrastructure.persistence.stat;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.stat.KpiWindow;
import com.vulntrend.batch.domain.stat.NewArrivals;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
@Import({PostgresTestConfig.class, JdbcKpiRepository.class})
@DisplayName("KPI 집계 — 실제 저장 구조에서")
class JdbcKpiRepositoryTest {

    /** 한국 시각 2026-10-31 09:00 실행. 지금 창은 10-30 00:00Z ~ 10-31 00:00Z */
    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final KpiWindow TODAY = KpiWindow.today(NOW);

    @Autowired
    private JdbcKpiRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("한 창의 신규는")
    class NewCount {

        @Test
        @DisplayName("창 시작 시각은 넣고 끝 시각은 빼서 이웃 창과 겹치지 않는다")
        void halfOpen() {
            vulnerability("CVE-2026-40001", "2026-10-30T00:00:00Z", "HIGH");
            vulnerability("CVE-2026-40002", "2026-10-30T23:59:59Z", "HIGH");
            vulnerability("CVE-2026-40003", "2026-10-31T00:00:00Z", "HIGH");
            vulnerability("CVE-2026-40004", "2026-10-29T23:59:59Z", "HIGH");

            assertThat(repository.newArrivals(TODAY).getNewCount()).isEqualTo(2);
            assertThat(repository.newArrivals(KpiWindow.yesterday(NOW)).getNewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("철회된 CVE 는 세지 않는다")
        void skipsRejected() {
            vulnerability("CVE-2026-40001", "2026-10-30T10:00:00Z", "HIGH");
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-40001'");

            assertThat(repository.newArrivals(TODAY).getNewCount()).isZero();
        }

        @Test
        @DisplayName("Critical 은 그 창의 신규 중 심각도 Critical 만 센다")
        void critical() {
            vulnerability("CVE-2026-40001", "2026-10-30T10:00:00Z", "CRITICAL");
            vulnerability("CVE-2026-40002", "2026-10-30T11:00:00Z", "HIGH");
            vulnerability("CVE-2026-40003", "2026-10-28T11:00:00Z", "CRITICAL");

            NewArrivals arrivals = repository.newArrivals(TODAY);

            assertThat(arrivals.getNewCount()).isEqualTo(2);
            assertThat(arrivals.getCriticalCount()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("KEV 신규는")
    class KevNew {

        @Test
        @DisplayName("등재일을 세계 표준시 자정으로 보고 발행일과 상관없이 창 안에 든 것을 센다")
        void byAddedDate() {
            vulnerability("CVE-2021-44228", "2021-12-10T00:00:00Z", "CRITICAL");
            kev("CVE-2021-44228", "2026-10-30");
            vulnerability("CVE-2026-40001", "2026-10-30T10:00:00Z", "HIGH");
            kev("CVE-2026-40001", "2026-10-31");

            assertThat(repository.newArrivals(TODAY).getKevNewCount()).isEqualTo(1);
            assertThat(repository.newArrivals(TODAY).getNewCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("철회된 CVE 는 세지 않는다")
        void skipsRejected() {
            vulnerability("CVE-2026-40001", "2026-10-20T10:00:00Z", "HIGH");
            kev("CVE-2026-40001", "2026-10-30");
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-40001'");

            assertThat(repository.newArrivals(TODAY).getKevNewCount()).isZero();
        }
    }

    @Nested
    @DisplayName("고위험은")
    class HighRisk {

        @Test
        @DisplayName("지금 수는 발행일과 상관없이 악용 확률 0.5 이상인 CVE 전체다")
        void now() {
            vulnerability("CVE-2021-44228", "2021-12-10T00:00:00Z", "CRITICAL");
            epss("CVE-2021-44228", "0.99");
            vulnerability("CVE-2026-40001", "2026-10-30T10:00:00Z", "HIGH");
            epss("CVE-2026-40001", "0.5");
            vulnerability("CVE-2026-40002", "2026-10-30T11:00:00Z", "HIGH");
            epss("CVE-2026-40002", "0.49999");
            vulnerability("CVE-2026-40003", "2026-10-30T12:00:00Z", "HIGH");
            epss("CVE-2026-40003", "0.9");
            jdbc.update("update vulnerability set rejected = true where vuln_id = 'CVE-2026-40003'");

            assertThat(repository.highRiskNow()).isEqualTo(2);
        }

        @Test
        @DisplayName("그 날 일별 통계에 남은 수를 읽는다")
        void recorded() {
            jdbc.update("insert into daily_stat (stat_date, new_count, high_risk_count) values ('2026-10-30', 0, 313)");

            assertThat(repository.highRiskRecordedOn(LocalDate.parse("2026-10-30"))).contains(313);
        }

        @Test
        @DisplayName("그 날 통계가 없거나 고위험 수가 비어 있으면 모른다고 알린다")
        void unknown() {
            jdbc.update("insert into daily_stat (stat_date, new_count) values ('2026-10-29', 0)");

            assertThat(repository.highRiskRecordedOn(LocalDate.parse("2026-10-29"))).isEmpty();
            assertThat(repository.highRiskRecordedOn(LocalDate.parse("2026-10-28"))).isEmpty();
        }
    }

    private void vulnerability(String id, String published, String severity) {
        jdbc.update("insert into vulnerability (vuln_id, published_at, cvss_severity) values (?, ?, ?)", id,
                Instant.parse(published).atOffset(ZoneOffset.UTC), severity);
    }

    private void kev(String id, String added) {
        jdbc.update("update vulnerability set is_kev = true, kev_added_at = cast(? as date) where vuln_id = ?", added,
                id);
    }

    private void epss(String id, String score) {
        jdbc.update("update vulnerability set epss_score = ? where vuln_id = ?", new BigDecimal(score), id);
    }
}
