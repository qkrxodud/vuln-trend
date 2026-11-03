package com.vulntrend.batch.infrastructure.cvelist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.cvelist.CnaAffected;
import com.vulntrend.batch.domain.cvelist.CnaRecord;
import com.vulntrend.batch.domain.cvelist.CnaVersion;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.config.CvelistProperties;
import com.vulntrend.batch.global.error.exception.SourceFetchException;
import com.vulntrend.batch.infrastructure.feed.FeedStubServer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CVE.org 레코드 한 건 받기")
class CvelistClientTest {

    private static final CveId CORS_CVE = CveId.from("CVE-2026-120215");
    // 2026-11-02 실제 응답(cveawg.mitre.org/api/cve/CVE-2026-120215)에서 쓰는 부분만 줄인 것
    private static final String CORS_RECORD = """
            {"dataType": "CVE_RECORD", "dataVersion": "5.1",
             "cveMetadata": {"cveId": "CVE-2026-120215", "state": "PUBLISHED", "assignerShortName": "openjs"},
             "containers": {"cna": {
               "title": "CORS origin bypass",
               "affected": [
                 {"vendor": "@fastify/cors", "product": "@fastify/cors", "defaultStatus": "unaffected",
                  "versions": [
                    {"versionType": "semver", "status": "affected", "version": "8.2.0", "lessThan": "11.4.0"},
                    {"versionType": "semver", "status": "unaffected", "version": "11.4.0"}],
                  "packageURL": "pkg:npm/@fastify/cors", "repo": "https://github.com/fastify/fastify-cors"},
                 {"vendor": "ken678", "product": "YznCMS", "versions": [{"version": "2.0", "status": "affected"}],
                  "cpes": ["cpe:2.3:a:yzncms:yzncms:*:*:*:*:*:*:*:*"], "modules": ["Auth Group Handler"]}]},
               "adp": [{"title": "CISA ADP Vulnrichment"}]}}
            """;

    private final FeedStubServer cveOrg = new FeedStubServer();
    private final List<Duration> pauses = new ArrayList<>();
    private final CvelistClient client = new CvelistClient(properties(cveOrg.url()), new ObjectMapper(), pauses::add);

    @AfterEach
    void stop() {
        cveOrg.close();
    }

    @Test
    @DisplayName("CNA 가 적은 제품마다 vendor·product·기본 상태·버전 줄·CPE·패키지 주소를 그대로 받아 온다")
    void readsCnaAffected() {
        cveOrg.answer(200, CORS_RECORD);

        CnaRecord record = client.fetch(CORS_CVE);

        assertThat(record.getCveId()).isEqualTo(CORS_CVE);
        assertThat(record.getAffected()).containsExactly(
                CnaAffected.of("@fastify/cors", "@fastify/cors", null, "unaffected",
                        List.of(CnaVersion.of("8.2.0", "affected", "11.4.0", null, "semver"),
                                CnaVersion.of("11.4.0", "unaffected", null, null, "semver")),
                        List.of(), "pkg:npm/@fastify/cors"),
                CnaAffected.of("ken678", "YznCMS", null, null, List.of(CnaVersion.of("2.0", "affected", null, null,
                        null)), List.of("cpe:2.3:a:yzncms:yzncms:*:*:*:*:*:*:*:*"), null));
    }

    @Test
    @DisplayName("CVE 번호를 주소 끝에 붙여 부르고, 부르기 전마다 정해진 만큼 쉰다")
    void asksByCveNumberAfterPause() {
        cveOrg.answer(200, CORS_RECORD).answer(200, CORS_RECORD);

        client.fetch(CORS_CVE);
        client.fetch(CORS_CVE);

        assertThat(cveOrg.paths()).containsExactly("/feed/CVE-2026-120215", "/feed/CVE-2026-120215");
        assertThat(pauses).containsExactly(Duration.ofMillis(100), Duration.ofMillis(100));
    }

    @Test
    @DisplayName("CNA 가 제품을 적지 않은 레코드(거부된 CVE 등)는 빈 목록으로 받는다")
    void noAffectedIsEmpty() {
        cveOrg.answer(200, """
                {"cveMetadata": {"cveId": "CVE-2026-120215", "state": "REJECTED"},
                 "containers": {"cna": {"rejectedReasons": [{"lang": "en", "value": "duplicate"}]}}}""");

        assertThat(client.fetch(CORS_CVE).getAffected()).isEmpty();
    }

    @Test
    @DisplayName("이름이 하나도 없는 제품 칸은 건너뛰고, 버전 줄이 없으면 빈 목록으로 받는다")
    void namelessEntriesAreSkipped() {
        cveOrg.answer(200, """
                {"containers": {"cna": {"affected": [
                  {"collectionURL": "https://registry.npmjs.org", "defaultStatus": "affected"},
                  {"packageName": "left-pad", "collectionURL": "https://registry.npmjs.org"}]}}}""");

        assertThat(client.fetch(CORS_CVE).getAffected()).containsExactly(
                CnaAffected.of(null, null, "left-pad", null, List.of(), List.of(), null));
    }

    @Test
    @DisplayName("레코드가 없다는 답이면 다시 부르지 않고 그 CVE 만 실패한다")
    void missingRecordFails() {
        cveOrg.answer(404, "{\"error\":\"CVE_RECORD_DNE\"}");

        assertThatThrownBy(() -> client.fetch(CORS_CVE)).isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("cvelist").hasMessageContaining("404");
        assertThat(cveOrg.requests()).isEqualTo(1);
    }

    @Test
    @DisplayName("CVE.org 가 잠시 답하지 못하면 쉬었다가 다시 부른다")
    void retriesServerTrouble() {
        cveOrg.answer(503, "busy").answer(200, CORS_RECORD);

        assertThat(client.fetch(CORS_CVE).getAffected()).hasSize(2);
        assertThat(pauses).containsExactly(Duration.ofMillis(100), Duration.ofSeconds(2));
    }

    @Test
    @DisplayName("읽을 수 없는 답이면 그 CVE 만 실패하고 답의 내용은 실패 사유에 넣지 않는다")
    void unreadableAnswerFails() {
        cveOrg.answer(200, "<html>secret-looking page</html>");

        assertThatThrownBy(() -> client.fetch(CORS_CVE)).isInstanceOf(SourceFetchException.class)
                .hasMessageContaining("cvelist").hasMessageNotContaining("secret-looking");
    }

    @Test
    @DisplayName("쉬는 중에 이번 실행이 중단되면 그 CVE 는 실패한다")
    void interruptedFails() {
        CvelistClient interrupted = new CvelistClient(properties(cveOrg.url()), new ObjectMapper(), duration -> {
            throw new InterruptedException();
        });

        assertThatThrownBy(() -> interrupted.fetch(CORS_CVE)).isInstanceOf(SourceFetchException.class);
        assertThat(Thread.interrupted()).isTrue();
    }

    private static CvelistProperties properties(String baseUrl) {
        return new CvelistProperties(baseUrl, Duration.ofDays(30), 500, Duration.ofDays(1), 10,
                Duration.ofMillis(100), 3, Duration.ofSeconds(2), Duration.ofSeconds(5));
    }
}
