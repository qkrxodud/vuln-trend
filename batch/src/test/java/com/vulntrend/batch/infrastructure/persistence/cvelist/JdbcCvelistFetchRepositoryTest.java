package com.vulntrend.batch.infrastructure.persistence.cvelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaVersion;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
@Import({PostgresTestConfig.class, JdbcCvelistFetchRepository.class, JdbcCnaAffectedRepository.class})
@DisplayName("CVE.org 보강 상태와 저장 — 실제 저장 구조에서")
class JdbcCvelistFetchRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-11-02T14:00:00Z");
    private static final Instant SINCE = NOW.minus(Duration.ofDays(30));
    private static final Instant RETRY_BEFORE = NOW.minus(Duration.ofDays(1));
    private static final CveId FFMPEG_CVE = CveId.from("CVE-2026-120243");
    private static final CveId CORS_CVE = CveId.from("CVE-2026-120215");
    private static final CnaAffected FFMPEG = CnaAffected.of("FFmpeg", "FFmpeg", null, "unaffected",
            List.of(CnaVersion.of("2.0", "affected", null, "9.0.2", "custom")), List.of(), "pkg:github/ffmpeg/ffmpeg");
    private static final CnaAffected YZNCMS = CnaAffected.of("ken678", "YznCMS", null, null,
            List.of(CnaVersion.of("2.0", "affected", null, null, null), CnaVersion.of("2.2.1", "unaffected", null,
                    null, null)), List.of("cpe:2.3:a:yzncms:yzncms:*:*:*:*:*:*:*:*"), null);

    @Autowired
    private JdbcCvelistFetchRepository fetches;

    @Autowired
    private JdbcCnaAffectedRepository stored;

    @Autowired
    private JdbcTemplate jdbc;

    @Nested
    @DisplayName("채울 대상은")
    class Targets {

        @Test
        @DisplayName("대표 제품이 없고 거부되지 않은 최근 CVE 를 최근 발행 순으로 상한까지 고른다")
        void recentWithoutProduct() {
            cve("CVE-2026-0001", NOW.minus(Duration.ofDays(1)), null, false);
            cve("CVE-2026-0002", NOW.minus(Duration.ofDays(2)), null, false);
            cve("CVE-2026-0003", NOW.minus(Duration.ofHours(1)), null, false);
            cve("CVE-2026-0004", NOW.minus(Duration.ofDays(3)), null, false);

            assertThat(fetches.findTargets(SINCE, RETRY_BEFORE, 3)).containsExactly(CveId.from("CVE-2026-0003"),
                    CveId.from("CVE-2026-0001"), CveId.from("CVE-2026-0002"));
        }

        @Test
        @DisplayName("대표 제품이 이미 있거나, 거부됐거나, 기간 밖이거나, 발행 시각이 없으면 고르지 않는다")
        void skipsOthers() {
            cve("CVE-2026-0001", NOW.minus(Duration.ofDays(1)), "Chrome", false);
            cve("CVE-2026-0002", NOW.minus(Duration.ofDays(1)), null, true);
            cve("CVE-2026-0003", NOW.minus(Duration.ofDays(31)), null, false);
            cve("CVE-2026-0004", null, null, false);

            assertThat(fetches.findTargets(SINCE, RETRY_BEFORE, 10)).isEmpty();
        }

        @Test
        @DisplayName("이미 제품을 채운 CVE 는 다시 부르지 않는다")
        void filledIsNotAskedAgain() {
            cve(FFMPEG_CVE.getValue(), NOW.minus(Duration.ofDays(1)), null, false);
            fetches.replaceAffected(FFMPEG_CVE, List.of(FFMPEG));
            fetches.markAnswered(FFMPEG_CVE, NOW.minus(Duration.ofDays(5)));

            assertThat(fetches.findTargets(SINCE, RETRY_BEFORE, 10)).isEmpty();
        }

        @Test
        @DisplayName("못 채운 CVE 는 마지막 시도가 재시도 간격보다 오래됐을 때만 다시 고른다")
        void unfilledWaitsForRetry() {
            cve("CVE-2026-0001", NOW.minus(Duration.ofDays(1)), null, false);
            cve("CVE-2026-0002", NOW.minus(Duration.ofDays(1)), null, false);
            cve("CVE-2026-0003", NOW.minus(Duration.ofDays(1)), null, false);
            fetches.markAnswered(CveId.from("CVE-2026-0001"), NOW.minus(Duration.ofHours(2)));
            fetches.markFailed(CveId.from("CVE-2026-0002"), NOW.minus(Duration.ofHours(2)));
            fetches.markFailed(CveId.from("CVE-2026-0003"), NOW.minus(Duration.ofDays(2)));

            assertThat(fetches.findTargets(SINCE, RETRY_BEFORE, 10)).containsExactly(CveId.from("CVE-2026-0003"));
        }
    }

    @Test
    @DisplayName("받은 제품 목록을 받은 순서대로 칸마다 그대로 남기고 다시 읽는다")
    void storesAndReadsBack() {
        cve(FFMPEG_CVE.getValue(), NOW, null, false);

        fetches.replaceAffected(FFMPEG_CVE, List.of(YZNCMS, FFMPEG));
        fetches.markAnswered(FFMPEG_CVE, NOW);

        assertThat(stored.findByCveIds(List.of(FFMPEG_CVE))).containsExactly(
                Map.entry(FFMPEG_CVE, List.of(YZNCMS, FFMPEG)));
    }

    @Test
    @DisplayName("다시 받은 목록으로 바꾸면 이전 목록의 남는 칸은 없어진다")
    void replaceDropsOldEntries() {
        cve(FFMPEG_CVE.getValue(), NOW, null, false);
        fetches.replaceAffected(FFMPEG_CVE, List.of(YZNCMS, FFMPEG));

        fetches.replaceAffected(FFMPEG_CVE, List.of(FFMPEG));
        fetches.markAnswered(FFMPEG_CVE, NOW);

        assertThat(stored.findByCveIds(List.of(FFMPEG_CVE)).get(FFMPEG_CVE)).containsExactly(FFMPEG);
    }

    @Test
    @DisplayName("답을 받았지만 제품이 없던 CVE 는 빈 목록으로, 답을 받은 적 없는 CVE 는 아예 나오지 않는다")
    void answeredEmptyVersusNeverAnswered() {
        cve(FFMPEG_CVE.getValue(), NOW, null, false);
        cve(CORS_CVE.getValue(), NOW, null, false);
        fetches.markAnswered(FFMPEG_CVE, NOW);
        fetches.markFailed(CORS_CVE, NOW);

        Map<CveId, List<CnaAffected>> found = stored.findByCveIds(List.of(FFMPEG_CVE, CORS_CVE));

        assertThat(found).containsOnlyKeys(FFMPEG_CVE);
        assertThat(found.get(FFMPEG_CVE)).isEmpty();
    }

    @Test
    @DisplayName("실패할 때마다 실패 수가 늘고, 나중에 답을 받아도 마지막 시도 시각이 앞으로 간다")
    void failuresAreCounted() {
        cve(FFMPEG_CVE.getValue(), NOW, null, false);

        fetches.markFailed(FFMPEG_CVE, NOW.minus(Duration.ofDays(3)));
        fetches.markFailed(FFMPEG_CVE, NOW.minus(Duration.ofDays(2)));
        fetches.markAnswered(FFMPEG_CVE, NOW);

        assertThat(jdbc.queryForObject("select failures from cvelist_fetch where vuln_id = ?", Integer.class,
                FFMPEG_CVE.getValue())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select attempted_at from cvelist_fetch where vuln_id = ?", Timestamp.class,
                FFMPEG_CVE.getValue()).toInstant()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("아무 CVE 도 묻지 않으면 빈 결과다")
    void noQuestion() {
        assertThat(stored.findByCveIds(List.of())).isEmpty();
    }

    private void cve(String id, Instant publishedAt, String primaryProduct, boolean rejected) {
        jdbc.update("insert into vulnerability (vuln_id, published_at, primary_product, rejected) values (?, ?, ?, ?)",
                id, publishedAt == null ? null : Timestamp.from(publishedAt), primaryProduct, rejected);
    }
}
