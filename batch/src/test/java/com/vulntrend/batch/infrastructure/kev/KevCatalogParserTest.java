package com.vulntrend.batch.infrastructure.kev;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.enrichment.KevEntry;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

@DisplayName("KEV 목록 읽기")
class KevCatalogParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final KevCatalogParser parser = new KevCatalogParser(objectMapper);

    @Nested
    @DisplayName("CISA 가 내려준 목록을 읽으면")
    class ReadingCatalog {

        private final List<KevEntry> entries = parser.parse(sample());

        @Test
        @DisplayName("모양이 맞는 건만 남기고 CVE 번호나 등재일이 잘못된 건은 건너뛴다")
        void skipsBrokenEntries() {
            assertThat(entries).extracting(entry -> entry.getCveId().getValue())
                    .containsExactly("CVE-2021-44228", "CVE-2024-3400", "CVE-2026-97229");
        }

        @Test
        @DisplayName("등재일·조치 기한·랜섬웨어 사용 여부를 CISA 표기대로 옮긴다")
        void copiesKevFields() {
            KevEntry log4shell = entries.get(0);
            KevEntry gitlab = entries.get(2);

            assertThat(log4shell.getDateAdded()).isEqualTo(LocalDate.of(2021, 12, 10));
            assertThat(log4shell.getDueDate()).contains(LocalDate.of(2021, 12, 24));
            assertThat(log4shell.isRansomware()).isTrue();
            assertThat(gitlab.isRansomware()).isFalse();
        }

        @Test
        @DisplayName("한 건의 원본을 제조사·제품 표기까지 그대로 보관한다")
        void keepsOriginalPayload() throws IOException {
            KevEntry panOs = entries.get(1);

            assertThat(objectMapper.readTree(panOs.getPayload()).get("vendorProject").asText())
                    .isEqualTo("Palo Alto Networks");
            assertThat(objectMapper.readTree(panOs.getPayload()).get("product").asText()).isEqualTo("PAN-OS");
        }
    }

    @Nested
    @DisplayName("목록이 잘못 왔으면")
    class BrokenCatalog {

        @Test
        @DisplayName("JSON 이 아니면 KEV 수집이 실패한다")
        void notJsonFails() {
            assertThatThrownBy(() -> parser.parse("<html>maintenance</html>"))
                    .isInstanceOf(SourceFetchException.class)
                    .hasMessageContaining("kev");
        }

        @Test
        @DisplayName("취약점 목록 칸이 없으면 KEV 수집이 실패한다")
        void missingListFails() {
            assertThatThrownBy(() -> parser.parse("{\"catalogVersion\":\"2026.10.29\"}"))
                    .isInstanceOf(SourceFetchException.class);
        }

        @Test
        @DisplayName("읽을 수 있는 건이 하나도 없으면 KEV 수집이 실패한다 — 빈 목록으로 모든 표시를 지우지 않기 위해")
        void nothingUsableFails() {
            assertThatThrownBy(() -> parser.parse("{\"vulnerabilities\":[{\"cveID\":\"CVE-21-1\"}]}"))
                    .isInstanceOf(SourceFetchException.class);
            assertThatThrownBy(() -> parser.parse("{\"vulnerabilities\":[]}"))
                    .isInstanceOf(SourceFetchException.class);
        }
    }

    private static String sample() {
        try {
            return new ClassPathResource("kev/catalog-sample.json").getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException missing) {
            throw new IllegalStateException("kev/catalog-sample.json is missing from test resources.", missing);
        }
    }
}
