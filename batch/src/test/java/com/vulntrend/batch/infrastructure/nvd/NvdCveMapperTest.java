package com.vulntrend.batch.infrastructure.nvd;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.vulnerability.AffectedProduct;
import com.vulntrend.batch.domain.vulnerability.CollectedCve;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.domain.vulnerability.CvssVersion;
import com.vulntrend.batch.domain.vulnerability.ReferenceLink;
import com.vulntrend.batch.domain.vulnerability.Severity;
import com.vulntrend.batch.domain.vulnerability.Vulnerability;
import com.vulntrend.batch.infrastructure.nvd.dto.NvdVulnerability;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("NVD 응답 읽기")
class NvdCveMapperTest {

    private static final Instant SEEN = Instant.parse("2026-10-31T00:00:00Z");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NvdCveMapper mapper = new NvdCveMapper(objectMapper);

    @Nested
    @DisplayName("실제 NVD 응답(Log4Shell, CVE-2021-44228)을 읽으면")
    class Log4Shell {

        private final CollectedCve collected = mapper.map(log4shell(), SEEN);
        private final Vulnerability vulnerability = collected.getVulnerability().orElseThrow();

        @Test
        @DisplayName("살아 있는 CVE 로 읽고 원본을 그대로 남긴다")
        void acceptedWithRaw() throws IOException {
            assertThat(collected.isRejected()).isFalse();
            assertThat(vulnerability.getId()).isEqualTo(CveId.from("CVE-2021-44228"));
            assertThat(objectMapper.readTree(collected.getRawPayload())).isEqualTo(log4shell().cve());
        }

        @Test
        @DisplayName("영어 설명을 쓰고 첫 문장으로 80자 임시 제목을 만든다")
        void englishDescriptionAndTitle() {
            assertThat(vulnerability.getDescription()).hasValueSatisfying(d -> assertThat(d).startsWith("Apache Log4j2"));
            assertThat(vulnerability.getTitle())
                    .contains("Apache Log4j2 2.0-beta9 through 2.15.0 (excluding security releases 2.12.2…");
        }

        @Test
        @DisplayName("NVD 와 제조사가 모두 3.1 점수 10.0 을 냈으면 NVD 점수로 심각도 CRITICAL 이 된다")
        void nvdCvss() {
            assertThat(vulnerability.getCvss().getScore())
                    .hasValueSatisfying(score -> assertThat(score).isEqualByComparingTo("10.0"));
            assertThat(vulnerability.getCvss().getVersion()).contains(CvssVersion.V3_1);
            assertThat(vulnerability.getCvss().getSeverity()).isEqualTo(Severity.CRITICAL);
        }

        @Test
        @DisplayName("시간대 표시가 없는 NVD 시각을 세계 표준시(UTC)로 읽는다")
        void utcTimes() {
            assertThat(vulnerability.getPublishedAt()).contains(Instant.parse("2021-12-10T10:15:09.143Z"));
            assertThat(vulnerability.getLastModifiedAt()).contains(Instant.parse("2026-08-11T19:33:44.513Z"));
        }

        @Test
        @DisplayName("영향받는다고 표시된 CPE 만 제품 143개로 모으고 같은 제품의 버전 범위는 한 줄로 합친다")
        void affectedProducts() {
            assertThat(vulnerability.getAffectedProducts()).hasSize(143);
            AffectedProduct log4j = vulnerability.getAffectedProducts().stream()
                    .filter(p -> p.getVendor().equals("apache") && p.getProduct().equals("log4j"))
                    .findFirst().orElseThrow();
            assertThat(log4j.getVulnerableRange()).contains(
                    ">=2.0.1, <2.3.1 || >=2.4.0, <2.12.2 || >=2.13.0, <2.15.0 || =2.0 || =2.0-beta9 || =2.0-rc1 || =2.0-rc2");
        }

        @Test
        @DisplayName("제조사 안내문·패치 표시가 붙은 링크만 겹치지 않게 4개 남긴다")
        void vendorReferences() {
            assertThat(vulnerability.getReferences()).hasSize(4);
            assertThat(vulnerability.getReferences()).extracting(ReferenceLink::getUrl)
                    .contains("https://logging.apache.org/log4j/2.x/security.html");
            assertThat(vulnerability.getReferences()).allSatisfy(link -> assertThat(link.getSeenAt()).isEqualTo(SEEN));
        }
    }

    @Test
    @DisplayName("NVD 가 철회한 CVE 는 철회로 읽고 취약점으로 만들지 않는다")
    void rejected() {
        CollectedCve collected = mapper.map(item("""
                {"id":"CVE-2026-20001","vulnStatus":"Rejected","published":"2026-10-01T10:00:00.000",
                 "descriptions":[{"lang":"en","value":"Rejected reason: duplicate of CVE-2026-20000."}]}
                """), SEEN);

        assertThat(collected.isRejected()).isTrue();
        assertThat(collected.getId()).isEqualTo(CveId.from("CVE-2026-20001"));
        assertThat(collected.getVulnerability()).isEmpty();
    }

    @Test
    @DisplayName("아직 분석 전이라 점수가 없는 CVE 는 심각도 미평가로 읽는다")
    void awaitingAnalysisIsUnrated() {
        Vulnerability vulnerability = accepted("""
                {"id":"CVE-2026-20002","vulnStatus":"Awaiting Analysis","published":"2026-10-30T08:15:00.000",
                 "lastModified":"2026-10-30T08:15:00.000",
                 "descriptions":[{"lang":"en","value":"A flaw in WordPress plugin Foo allows XSS."}],
                 "metrics":{},"references":[]}
                """);

        assertThat(vulnerability.getCvss().isUnrated()).isTrue();
        assertThat(vulnerability.getAffectedProducts()).isEmpty();
        assertThat(vulnerability.getReferences()).isEmpty();
    }

    @Test
    @DisplayName("제조사가 낸 4.0 점수만 있으면 4.0 점수를 쓴다")
    void cnaV40Only() {
        Vulnerability vulnerability = accepted("""
                {"id":"CVE-2026-20003","vulnStatus":"Received","published":"2026-10-30T08:15:00.000",
                 "descriptions":[{"lang":"en","value":"Buffer overflow."}],
                 "metrics":{"cvssMetricV40":[{"source":"psirt@example.com","type":"Secondary",
                   "cvssData":{"version":"4.0","baseScore":8.7,"baseSeverity":"HIGH"}}]}}
                """);

        assertThat(vulnerability.getCvss().getVersion()).contains(CvssVersion.V4_0);
        assertThat(vulnerability.getCvss().getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("NVD 3.1 점수가 없으면 4.0 점수가 있어도 제조사 3.1 점수를 쓴다")
    void cnaV31OverNvdV40() {
        Vulnerability vulnerability = accepted("""
                {"id":"CVE-2026-20004","vulnStatus":"Analyzed","published":"2026-10-30T08:15:00.000",
                 "descriptions":[{"lang":"en","value":"Path traversal."}],
                 "metrics":{
                   "cvssMetricV40":[{"source":"nvd@nist.gov","type":"Primary","cvssData":{"version":"4.0","baseScore":9.3}}],
                   "cvssMetricV31":[{"source":"cna@vendor.example","type":"Secondary","cvssData":{"version":"3.1","baseScore":6.5}}]}}
                """);

        assertThat(vulnerability.getCvss().getScore())
                .hasValueSatisfying(score -> assertThat(score).isEqualByComparingTo("6.5"));
    }

    @Test
    @DisplayName("3.1 점수 없이 3.0 과 4.0 점수만 있으면 3.0 점수를 쓴다")
    void v30OverV40() {
        Vulnerability vulnerability = accepted("""
                {"id":"CVE-2026-20008","vulnStatus":"Analyzed","descriptions":[{"lang":"en","value":"x."}],
                 "metrics":{
                   "cvssMetricV40":[{"source":"nvd@nist.gov","type":"Primary","cvssData":{"version":"4.0","baseScore":9.3}}],
                   "cvssMetricV30":[{"source":"nvd@nist.gov","type":"Primary","cvssData":{"version":"3.0","baseScore":7.8}}]}}
                """);

        assertThat(vulnerability.getCvss().getVersion()).contains(CvssVersion.V3_0);
        assertThat(vulnerability.getCvss().getSeverity()).isEqualTo(Severity.HIGH);
    }

    @Test
    @DisplayName("영어 설명이 없으면 첫 설명을 쓴다")
    void fallsBackToFirstDescription() {
        Vulnerability vulnerability = accepted("""
                {"id":"CVE-2026-20005","vulnStatus":"Received",
                 "descriptions":[{"lang":"es","value":"Desbordamiento de búfer."}]}
                """);

        assertThat(vulnerability.getDescription()).contains("Desbordamiento de búfer.");
        assertThat(vulnerability.getPublishedAt()).isEmpty();
    }

    @Test
    @DisplayName("시간대 표시가 붙은 시각도 그 시간대를 따라 세계 표준시로 읽는다")
    void timeWithOffset() {
        Vulnerability vulnerability = accepted("""
                {"id":"CVE-2026-20006","vulnStatus":"Received","published":"2026-10-30T17:15:00.000+09:00",
                 "descriptions":[]}
                """);

        assertThat(vulnerability.getPublishedAt()).contains(Instant.parse("2026-10-30T08:15:00Z"));
        assertThat(vulnerability.getTitle()).isEmpty();
    }

    @Test
    @DisplayName("CPE 모양이 아닌 항목은 건너뛰고 나머지 제품은 읽는다")
    void skipsUnreadableCpe() {
        Vulnerability vulnerability = accepted("""
                {"id":"CVE-2026-20007","vulnStatus":"Analyzed","descriptions":[{"lang":"en","value":"x."}],
                 "configurations":[{"nodes":[{"cpeMatch":[
                   {"vulnerable":true,"criteria":"not-a-cpe"},
                   {"vulnerable":true,"criteria":"cpe:2.3:a:google:chrome:*:*:*:*:*:*:*:*","versionEndExcluding":"130.0.6723.69"},
                   {"vulnerable":true,"criteria":"cpe:2.3:a:acme:widget:*:*:*:*:*:*:*:*","versionStartExcluding":"1.0","versionEndIncluding":"1.4"},
                   {"vulnerable":false,"criteria":"cpe:2.3:o:microsoft:windows:-:*:*:*:*:*:*:*"}]}]}]}
                """);

        assertThat(vulnerability.getAffectedProducts()).extracting(AffectedProduct::getProduct)
                .containsExactly("chrome", "widget");
        assertThat(vulnerability.getAffectedProducts().get(0).getVulnerableRange()).contains("<130.0.6723.69");
        assertThat(vulnerability.getAffectedProducts().get(1).getVulnerableRange()).contains(">1.0, <=1.4");
    }

    private Vulnerability accepted(String cveJson) {
        return mapper.map(item(cveJson), SEEN).getVulnerability().orElseThrow();
    }

    private NvdVulnerability item(String cveJson) {
        try {
            return new NvdVulnerability(objectMapper.readTree(cveJson));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private NvdVulnerability log4shell() {
        try (InputStream in = getClass().getResourceAsStream("/nvd/CVE-2021-44228.json")) {
            JsonNode page = objectMapper.readTree(in);
            return new NvdVulnerability(page.get("vulnerabilities").get(0).get("cve"));
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
