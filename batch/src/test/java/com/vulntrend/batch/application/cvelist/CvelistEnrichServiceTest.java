package com.vulntrend.batch.application.cvelist;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaVersion;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.fake.FakeCnaAffectedRepository;
import com.vulntrend.batch.fake.FakeCvelistFetchRepository;
import com.vulntrend.batch.fake.FakeCvelistSource;
import com.vulntrend.batch.global.config.CvelistProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

@DisplayName("CVE.org 제품 보강")
@ExtendWith(OutputCaptureExtension.class)
class CvelistEnrichServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T14:00:00Z");
    private static final CveId FFMPEG_CVE = CveId.from("CVE-2026-120243");
    private static final CveId CORS_CVE = CveId.from("CVE-2026-120215");
    private static final CveId VAGUE_CVE = CveId.from("CVE-2026-120999");
    private static final List<CnaAffected> FFMPEG = List.of(CnaAffected.of("FFmpeg", "FFmpeg", null, "unaffected",
            List.of(CnaVersion.of("2.0", "affected", null, "9.0.2", "custom")), List.of(), null));
    private static final List<CnaAffected> CORS = List.of(CnaAffected.of("@fastify/cors", "@fastify/cors", null,
            "unaffected", List.of(CnaVersion.of("8.2.0", "affected", "11.4.0", null, "semver")), List.of(),
            "pkg:npm/@fastify/cors"));

    private final FakeCnaAffectedRepository stored = new FakeCnaAffectedRepository();
    private final FakeCvelistFetchRepository fetches = new FakeCvelistFetchRepository(stored);
    private final FakeCvelistSource cveOrg = new FakeCvelistSource();

    @Test
    @DisplayName("대표 제품 없는 CVE 를 최근 30일 안에서, 재시도 간격이 지난 것만, 한 실행 상한까지 찾는다")
    void looksForRecentTargets() {
        service(500, 10).enrichRecent();

        assertThat(fetches.publishedSince()).isEqualTo(NOW.minus(Duration.ofDays(30)));
        assertThat(fetches.retryBefore()).isEqualTo(NOW.minus(Duration.ofDays(1)));
        assertThat(fetches.limit()).isEqualTo(500);
    }

    @Test
    @DisplayName("CNA 가 적은 제품을 저장하고 답을 받은 것으로 남긴 뒤 받은 수·저장한 수·실패 수를 한 줄로 남긴다")
    void storesAffected(CapturedOutput output) {
        fetches.targeting(FFMPEG_CVE, CORS_CVE);
        cveOrg.answering(FFMPEG_CVE, FFMPEG).answering(CORS_CVE, CORS);

        int storedCount = service(500, 10).enrichRecent();

        assertThat(storedCount).isEqualTo(2);
        assertThat(fetches.replaced()).containsEntry(FFMPEG_CVE, FFMPEG).containsEntry(CORS_CVE, CORS);
        assertThat(fetches.answered()).containsEntry(FFMPEG_CVE, NOW).containsEntry(CORS_CVE, NOW);
        assertThat(output).contains("Cvelist enrichment: 2 fetched, 2 stored, 0 failed");
    }

    @Test
    @DisplayName("받은 제품 목록이 이미 저장된 것과 같으면 다시 쓰지 않는다")
    void sameContentIsNotRewritten(CapturedOutput output) {
        fetches.targeting(FFMPEG_CVE);
        stored.with(FFMPEG_CVE, FFMPEG);
        cveOrg.answering(FFMPEG_CVE, FFMPEG);

        service(500, 10).enrichRecent();

        assertThat(fetches.replaced()).isEmpty();
        assertThat(fetches.answered()).containsKey(FFMPEG_CVE);
        assertThat(output).contains("Cvelist enrichment: 1 fetched, 0 stored, 0 failed");
    }

    @Test
    @DisplayName("CNA 가 제품을 적지 않았으면 쓸 것 없이 답을 받은 것으로만 남겨 재시도 간격 뒤에 다시 본다")
    void emptyAnswerIsOnlyMarked() {
        fetches.targeting(VAGUE_CVE);
        cveOrg.answering(VAGUE_CVE, List.of());

        service(500, 10).enrichRecent();

        assertThat(fetches.replaced()).isEmpty();
        assertThat(fetches.answered()).containsKey(VAGUE_CVE);
    }

    @Test
    @DisplayName("한 CVE 를 받지 못하면 실패로 남기고 다음 CVE 로 넘어간다")
    void failureMovesOn(CapturedOutput output) {
        fetches.targeting(VAGUE_CVE, FFMPEG_CVE);
        cveOrg.answering(FFMPEG_CVE, FFMPEG);

        service(500, 10).enrichRecent();

        assertThat(fetches.failed()).containsOnlyKeys(VAGUE_CVE);
        assertThat(fetches.replaced()).containsOnlyKeys(FFMPEG_CVE);
        assertThat(output).contains("Cvelist enrichment: 1 fetched, 1 stored, 1 failed");
    }

    @Nested
    @DisplayName("CVE.org 가 계속 답하지 못하면")
    class SourceDown {

        @Test
        @DisplayName("연달아 정해진 횟수만큼 실패한 뒤 이번 실행의 보강을 멈추고 남은 것은 다음 실행으로 미룬다")
        void stopsAfterConsecutiveFailures(CapturedOutput output) {
            fetches.targeting(CveId.from("CVE-2026-0001"), CveId.from("CVE-2026-0002"), CveId.from("CVE-2026-0003"),
                    FFMPEG_CVE);
            cveOrg.answering(FFMPEG_CVE, FFMPEG);

            service(500, 3).enrichRecent();

            assertThat(cveOrg.asked()).hasSize(3);
            assertThat(output).contains("Cvelist enrichment stopped after 3 failures in a row");
            assertThat(output).contains("Cvelist enrichment: 0 fetched, 0 stored, 3 failed");
        }

        @Test
        @DisplayName("사이에 하나라도 받으면 연달은 실패 수를 처음부터 다시 센다")
        void successResetsTheCount() {
            CveId first = CveId.from("CVE-2026-0001");
            CveId third = CveId.from("CVE-2026-0003");
            CveId fourth = CveId.from("CVE-2026-0004");
            fetches.targeting(first, FFMPEG_CVE, third, fourth, CORS_CVE);
            cveOrg.answering(FFMPEG_CVE, FFMPEG).answering(CORS_CVE, CORS);

            service(500, 2).enrichRecent();

            assertThat(cveOrg.asked()).containsExactly(first, FFMPEG_CVE, third, fourth);
        }
    }

    @Test
    @DisplayName("보강 단계에서 예상 못 한 문제가 나도 실행은 계속되고 이전 값이 그대로 남는다")
    void unexpectedTroubleIsNotFatal(CapturedOutput output) {
        stored.with(FFMPEG_CVE, FFMPEG);
        FakeCvelistFetchRepository broken = new FakeCvelistFetchRepository(stored).broken();

        int storedCount = new CvelistEnrichService(properties(500, 10), cveOrg, broken, stored,
                Clock.fixed(NOW, ZoneOffset.UTC)).enrichRecent();

        assertThat(storedCount).isZero();
        assertThat(stored.findByCveIds(List.of(FFMPEG_CVE))).containsEntry(FFMPEG_CVE, FFMPEG);
        assertThat(output).contains("Cvelist enrichment failed (IllegalStateException); the run continues");
        assertThat(output).doesNotContain("storage is down");
    }

    @Test
    @DisplayName("채울 CVE 가 없으면 아무것도 부르지 않고 0 건으로 남긴다")
    void nothingToDo(CapturedOutput output) {
        service(500, 10).enrichRecent();

        assertThat(cveOrg.asked()).isEmpty();
        assertThat(output).contains("Cvelist enrichment: 0 fetched, 0 stored, 0 failed");
    }

    private CvelistEnrichService service(int maxPerRun, int maxConsecutiveFailures) {
        return new CvelistEnrichService(properties(maxPerRun, maxConsecutiveFailures), cveOrg, fetches, stored,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CvelistProperties properties(int maxPerRun, int maxConsecutiveFailures) {
        return new CvelistProperties("http://127.0.0.1:9/api/cve", Duration.ofDays(30), maxPerRun,
                Duration.ofDays(1), maxConsecutiveFailures, Duration.ZERO, 3, Duration.ZERO, Duration.ofSeconds(5));
    }
}
