package com.vulntrend.batch.infrastructure.epss;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.fake.FakeEpssRepository;
import com.vulntrend.batch.global.config.EpssProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.feed.FeedStubServer;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("EPSS 수집")
class EpssCollectorTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final SyncWindow ANY_WINDOW = SyncWindow.of(NOW.minus(Duration.ofDays(1)), NOW);

    private final FeedStubServer first = new FeedStubServer();
    private final FakeEpssRepository repository = new FakeEpssRepository(Set.of(
            CveId.from("CVE-2021-44228"), CveId.from("CVE-2024-3400"), CveId.from("CVE-2026-11111")));
    private final EpssCollector collector = new EpssCollector(
            new EpssClient(new EpssProperties(first.url(), 1, Duration.ofSeconds(2), Duration.ofSeconds(5)), d -> { }),
            repository);

    @AfterEach
    void stop() {
        first.close();
    }

    @Test
    @DisplayName("EPSS 수집은 악용 확률이라는 소스로 기록된다")
    void sourceIsEpss() {
        assertThat(collector.source()).isEqualTo(SourceName.EPSS);
    }

    @Test
    @DisplayName("파일 전체에서 취약점 목록에 있는 CVE 만 골라 반영하고 반영한 수를 알린다")
    void appliesKnownCvesOnly() {
        first.answer(200, EpssFiles.gzip("#model_version:v2026.06.15,score_date:2026-10-30T12:03:08Z",
                "cve,epss,percentile",
                "CVE-1999-0001,0.04753,0.91871",
                "CVE-2021-44228,0.99999,1.0",
                "CVE-2024-3400,0.94310,0.99912"));

        CollectResult result = collector.collect(ANY_WINDOW);

        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.getLoadedCount()).isEqualTo(2);
        assertThat(repository.applied()).extracting(score -> score.getCveId().getValue())
                .containsExactly("CVE-2021-44228", "CVE-2024-3400");
    }

    @Test
    @DisplayName("FIRST 가 답하지 않으면 아무것도 반영하지 않고 EPSS 수집이 실패한다")
    void unreachableChangesNothing() {
        first.answer(503, "busy");

        assertThatThrownBy(() -> collector.collect(ANY_WINDOW)).isInstanceOf(SourceFetchException.class);
        assertThat(repository.applied()).isEmpty();
    }

    @Test
    @DisplayName("점수 줄이 없는 파일이 오면 아무것도 반영하지 않고 EPSS 수집이 실패한다")
    void emptyFileChangesNothing() {
        first.answer(200, EpssFiles.gzip("cve,epss,percentile"));

        assertThatThrownBy(() -> collector.collect(ANY_WINDOW)).isInstanceOf(SourceFetchException.class);
        assertThat(repository.applied()).isEmpty();
    }

    @Test
    @DisplayName("취약점 목록이 비어 있으면 반영할 것 없이 수집을 마친다")
    void emptyVulnerabilityList() {
        FakeEpssRepository empty = new FakeEpssRepository(Set.of());
        EpssCollector fresh = new EpssCollector(new EpssClient(
                new EpssProperties(first.url(), 1, Duration.ofSeconds(2), Duration.ofSeconds(5)), d -> { }), empty);
        first.answer(200, EpssFiles.gzip("cve,epss,percentile", "CVE-2021-44228,0.99999,1.0"));

        assertThat(fresh.collect(ANY_WINDOW).getLoadedCount()).isZero();
    }
}
