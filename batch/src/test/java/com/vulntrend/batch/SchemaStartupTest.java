package com.vulntrend.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.collect.Collector;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfig.class)
@DisplayName("배치 시작 — 실제 저장 구조에서")
class SchemaStartupTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private List<Collector> collectors;

    @Test
    @DisplayName("빈 저장소에서 처음 실행하면 기획서 5장의 저장 구조 8개와 요약 호출 기록·EPSS 점수 파일 날짜·마지막 게시 기록을 먼저 만들고 시작한다")
    void createsStores() {
        List<String> names = jdbc.queryForList(
                "select table_name from information_schema.tables where table_schema = 'public' "
                        + "and table_name <> 'flyway_schema_history' order by table_name", String.class);

        assertThat(names).containsExactly(
                "affected_product", "daily_stat", "epss_file", "product_alias", "publish_state", "reference_link",
                "summary", "summary_call", "sync_state", "vulnerability", "vulnerability_raw");
    }

    @Test
    @DisplayName("점수순·발행순 목록과 제품·생태계 찾기를 빠르게 하는 장치 4개가 함께 만들어진다")
    void createsFourLookups() {
        List<String> definitions = jdbc.queryForList(
                "select indexdef from pg_indexes where schemaname = 'public' and indexname not like '%pkey' "
                        + "and tablename <> 'flyway_schema_history' "
                        + "order by indexname", String.class);

        assertThat(definitions).hasSize(4);
        assertThat(definitions).anyMatch(d -> d.contains("vulnerability USING btree (trend_score DESC)"));
        assertThat(definitions).anyMatch(d -> d.contains("vulnerability USING btree (published_at DESC)"));
        assertThat(definitions).anyMatch(d -> d.contains("affected_product USING btree (slug)"));
        assertThat(definitions).anyMatch(d -> d.contains("affected_product USING btree (ecosystem)"));
    }

    @Test
    @DisplayName("한국어 요약은 무엇이·누가 영향·무엇을 해야 세 칸으로 나뉘어 담긴다")
    void summaryHasThreeParts() {
        List<String> columns = jdbc.queryForList(
                "select column_name from information_schema.columns where table_name = 'summary' "
                        + "order by ordinal_position", String.class);

        assertThat(columns).containsExactly("vuln_id", "what", "who", "action", "model", "generated_at");
    }

    @Test
    @DisplayName("NVD 수집기가 하루 한 번 실행의 수집 대상에 들어 있다")
    void nvdCollectorIsRegistered() {
        assertThat(collectors).extracting(Collector::source).contains(SourceName.NVD);
    }

    @Test
    @DisplayName("지금 시각은 세계 표준시(UTC)로 잰다")
    void clockIsUtc() {
        assertThat(clock.getZone().getId()).isEqualTo("Z");
    }
}
