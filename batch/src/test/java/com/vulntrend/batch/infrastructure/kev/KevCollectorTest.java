package com.vulntrend.batch.infrastructure.kev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.collect.CollectResult;
import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.fake.FakeKevRepository;
import com.vulntrend.batch.global.config.KevProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("KEV 수집")
class KevCollectorTest {

    private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");
    private static final SyncWindow ANY_WINDOW = SyncWindow.of(NOW.minus(Duration.ofDays(1)), NOW);

    private final KevStubServer cisa = new KevStubServer();
    private final FakeKevRepository repository = new FakeKevRepository(
            Set.of(CveId.from("CVE-2021-44228"), CveId.from("CVE-2024-3400")));
    private final KevCollector collector = new KevCollector(
            new KevClient(new KevProperties(cisa.url(), 1, Duration.ofSeconds(2), Duration.ofSeconds(5)), d -> { }),
            new KevCatalogParser(new ObjectMapper()), repository, Clock.fixed(NOW, ZoneOffset.UTC));

    KevCollectorTest() throws IOException {
    }

    @AfterEach
    void stop() {
        cisa.close();
    }

    @Test
    @DisplayName("KEV 수집은 실제 악용 목록이라는 소스로 기록된다")
    void sourceIsKev() {
        assertThat(collector.source()).isEqualTo(SourceName.KEV);
    }

    @Test
    @DisplayName("목록 전체를 받아 취약점 목록에 반영하고 표시를 붙인 CVE 수를 알린다")
    void marksWholeCatalog() throws IOException {
        cisa.answer(200, sample());

        CollectResult result = collector.collect(ANY_WINDOW);

        assertThat(result.isSucceeded()).isTrue();
        assertThat(result.getLoadedCount()).isEqualTo(2);
        assertThat(repository.received()).hasSize(3);
        assertThat(repository.fetchedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("CISA 가 답하지 않으면 아무것도 반영하지 않고 KEV 수집이 실패한다")
    void unreachableChangesNothing() {
        cisa.answer(503, "busy");

        assertThatThrownBy(() -> collector.collect(ANY_WINDOW)).isInstanceOf(SourceFetchException.class);
        assertThat(repository.received()).isEmpty();
    }

    @Test
    @DisplayName("빈 목록이 오면 실제 악용 표시를 지우지 않고 KEV 수집이 실패한다")
    void emptyCatalogChangesNothing() {
        cisa.answer(200, "{\"vulnerabilities\":[]}");

        assertThatThrownBy(() -> collector.collect(ANY_WINDOW)).isInstanceOf(SourceFetchException.class);
        assertThat(repository.received()).isEmpty();
    }

    private static String sample() throws IOException {
        return new ClassPathResource("kev/catalog-sample.json").getContentAsString(StandardCharsets.UTF_8);
    }
}
