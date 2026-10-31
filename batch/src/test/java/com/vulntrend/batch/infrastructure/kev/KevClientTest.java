package com.vulntrend.batch.infrastructure.kev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vulntrend.batch.global.config.KevProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("KEV 내려받기")
class KevClientTest {

    private static final String CATALOG = "{\"vulnerabilities\":[]}";

    private final KevStubServer cisa = new KevStubServer();
    private final List<Duration> pauses = new ArrayList<>();
    private final KevClient client = new KevClient(
            new KevProperties(cisa.url(), 3, Duration.ofSeconds(2), Duration.ofSeconds(5)), pauses::add);

    KevClientTest() throws IOException {
    }

    @AfterEach
    void stop() {
        cisa.close();
    }

    @Test
    @DisplayName("CISA 가 목록을 주면 그대로 받아 온다")
    void returnsCatalog() {
        cisa.answer(200, CATALOG);

        assertThat(client.fetch()).isEqualTo(CATALOG);
        assertThat(pauses).isEmpty();
    }

    @Test
    @DisplayName("CISA 서버가 잠시 답하지 못하면 2초부터 두 배씩 쉬며 다시 부른다")
    void retriesServerTrouble() {
        cisa.answer(503, "busy").answer(500, "busy").answer(200, CATALOG);

        assertThat(client.fetch()).isEqualTo(CATALOG);
        assertThat(pauses).containsExactly(Duration.ofSeconds(2), Duration.ofSeconds(4));
    }

    @Test
    @DisplayName("3번 불러도 답하지 못하면 KEV 수집이 실패한다")
    void givesUpAfterThreeTries() {
        cisa.answer(503, "busy").answer(503, "busy").answer(503, "busy");

        assertThatThrownBy(client::fetch).isInstanceOf(SourceFetchException.class).hasMessageContaining("kev");
        assertThat(cisa.requests()).isEqualTo(3);
    }

    @Test
    @DisplayName("주소가 없다는 답이면 다시 부르지 않고 바로 실패한다")
    void notFoundFailsAtOnce() {
        cisa.answer(404, "gone");

        assertThatThrownBy(client::fetch).isInstanceOf(SourceFetchException.class).hasMessageContaining("404");
        assertThat(cisa.requests()).isEqualTo(1);
    }

    @Test
    @DisplayName("CISA 에 닿지 못하면 정해진 횟수만큼 다시 불러 본 뒤 실패한다")
    void unreachableFails() {
        KevClient offline = new KevClient(new KevProperties("http://127.0.0.1:9/kev.json", 2,
                Duration.ofSeconds(2), Duration.ofSeconds(1)), pauses::add);

        assertThatThrownBy(offline::fetch).isInstanceOf(SourceFetchException.class);
        assertThat(pauses).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("기다리는 중에 이번 실행이 중단되면 KEV 수집이 실패한다")
    void interruptedFails() {
        cisa.answer(503, "busy");
        KevClient interrupted = new KevClient(new KevProperties(cisa.url(), 3, Duration.ofSeconds(2),
                Duration.ofSeconds(5)), duration -> {
                    throw new InterruptedException("stop");
                });

        assertThatThrownBy(interrupted::fetch).isInstanceOf(SourceFetchException.class);
        assertThat(Thread.interrupted()).isTrue();
    }
}
