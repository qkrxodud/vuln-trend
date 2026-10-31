package com.vulntrend.batch.infrastructure.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.SummaryProduct;
import com.vulntrend.batch.domain.summary.SummaryResult;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.domain.vulnerability.CveId;
import com.vulntrend.batch.global.config.SummaryProperties;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/** 실제 SDK 로 흉내 서버를 부른다. 실제 Claude 는 부르지 않는다 */
@DisplayName("Claude 로 한국어 요약 받기")
@ExtendWith(OutputCaptureExtension.class)
class AnthropicSummarizerTest {

    private static final String KEY = "summary-key-41c9e7";
    private static final String MODEL = "claude-opus-5-5";
    private static final String FALLBACK_MODEL = "claude-opus-4-8";
    private static final SummarySource LOG4SHELL = SummarySource.of(CveId.from("CVE-2021-44228"),
            "Apache Log4j2 2.0-beta9 through 2.15.0 JNDI features do not protect against attacker controlled LDAP.",
            List.of(SummaryProduct.of("apache", "log4j", ">= 2.0-beta9, < 2.15.0", null)), new BigDecimal("10.0"));
    private static final String GOOD_SUMMARY = """
            {"what": "로그 문자열로 원격 코드를 실행할 수 있는 취약점이다.", \
            "who": "Log4j 2.0-beta9 이상 2.15.0 미만 사용자가 영향받는다.", \
            "action": "원문 참고"}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AnthropicStubServer claude;

    @BeforeEach
    void start() throws IOException {
        claude = new AnthropicStubServer();
    }

    @AfterEach
    void stop() {
        claude.close();
    }

    @Nested
    @DisplayName("Claude 에 보내는 요청")
    class Request {

        @Test
        @DisplayName("설정한 열쇠와 모델 이름으로 요청한다")
        void usesConfiguredKeyAndModel() throws IOException {
            claude.answer(200, message("end_turn", GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            assertThat(claude.lastPath()).isEqualTo("/v1/messages");
            assertThat(claude.lastCredential()).isEqualTo(KEY);
            assertThat(request().path("model").asText()).isEqualTo(MODEL);
        }

        @Test
        @DisplayName("요약은 가벼운 작업이라 생각의 깊이를 낮게 둔다")
        void lowEffort() throws IOException {
            claude.answer(200, message("end_turn", GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            assertThat(request().path("output_config").path("effort").asText()).isEqualTo("low");
            assertThat(request().has("thinking")).isFalse();
        }

        @Test
        @DisplayName("무엇이·누가·무엇을 세 칸이 모두 있는 모양으로만 답하게 한다")
        void structuredOutput() throws IOException {
            claude.answer(200, message("end_turn", GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            JsonNode format = request().path("output_config").path("format");
            assertThat(format.path("type").asText()).isEqualTo("json_schema");
            assertThat(texts(format.path("schema").path("required"))).containsExactly("what", "who", "action");
            assertThat(format.path("schema").path("additionalProperties").asBoolean(true)).isFalse();
        }

        @Test
        @DisplayName("거절되면 서버가 거절 분류에 맞는 다른 Claude 모델로 다시 답하게 한다")
        void serverSideFallback() throws IOException {
            claude.answer(200, message("end_turn", GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            assertThat(request().path("fallbacks").asText()).isEqualTo("default");
            assertThat(claude.lastBetaHeader()).contains("server-side-fallback-2026-07-01");
        }

        @Test
        @DisplayName("지시문은 따로 보내고 원문은 사용자 글 하나로만 보낸다")
        void instructionsAndSourceOnly() throws IOException {
            claude.answer(200, message("end_turn", GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            JsonNode messages = request().path("messages");
            assertThat(messages).hasSize(1);
            assertThat(messages.get(0).path("role").asText()).isEqualTo("user");
            assertThat(messages.toString()).contains("<description>");
            assertThat(request().path("system").toString()).contains("원문에 없는 버전·제품·수치를 만들지 않는다");
        }
    }

    @Nested
    @DisplayName("Claude 의 답")
    class Answer {

        @Test
        @DisplayName("세 문장을 받으면 설정한 모델 이름과 함께 요약으로 쓴다")
        void generated() throws IOException {
            claude.answer(200, message("end_turn", GOOD_SUMMARY));

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isGenerated()).isTrue();
            assertThat(result.getSummary()).map(KoreanSummary::getWho)
                    .contains("Log4j 2.0-beta9 이상 2.15.0 미만 사용자가 영향받는다.");
            assertThat(result.getModel()).contains(MODEL);
        }

        @Test
        @DisplayName("원문에 없는 버전이 나오면 요약을 버린다")
        void discardsInventedVersions() throws IOException {
            claude.answer(200, message("end_turn", """
                    {"what": "원격 코드 실행 취약점이다.", "who": "Log4j 사용자가 영향받는다.", \
                    "action": "Log4j 2.17.1 이상으로 업데이트한다."}"""));

            assertThat(summarizer().summarize(LOG4SHELL).isDiscarded()).isTrue();
        }

        @Test
        @DisplayName("답에 명령어가 섞여 있으면 요약을 버린다")
        void discardsCommands() throws IOException {
            claude.answer(200, message("end_turn", """
                    {"what": "원격 코드 실행 취약점이다.", "who": "Log4j 사용자가 영향받는다.", \
                    "action": "sudo apt-get install --only-upgrade liblog4j2-java 를 실행한다."}"""));

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isDiscarded()).isTrue();
        }

        @Test
        @DisplayName("Claude 가 요약을 거절하면 이 CVE 는 요약 없이 넘어간다")
        void refused(CapturedOutput output) throws IOException {
            claude.answer(200, """
                    {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5", \
                    "content": [], "stop_reason": "refusal", "stop_sequence": null, \
                    "stop_details": {"type": "refusal", "category": "cyber", "explanation": "declined"}, \
                    "usage": {"input_tokens": 10, "output_tokens": 0}}""");

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isRefused()).isTrue();
            assertThat(output).contains("declined to summarize CVE-2021-44228");
        }

        @Test
        @DisplayName("답이 길이 한도에서 끊기면 요약하지 못한 것으로 본다")
        void cutOff() throws IOException {
            claude.answer(200, message("max_tokens", "{\"what\": \"원격"));

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
        }

        @Test
        @DisplayName("답이 약속한 모양이 아니면 요약하지 못한 것으로 본다")
        void notJson() throws IOException {
            claude.answer(200, message("end_turn", "요약할 수 없습니다"));

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("Claude Opus 5.5 가 거절해 다른 모델이 대신 답하면")
    class Fallback {

        @Test
        @DisplayName("실제로 답한 모델 이름을 요약과 함께 남긴다")
        void recordsAnsweringModel(CapturedOutput output) throws IOException {
            claude.answer(200, fallbackMessage("end_turn", GOOD_SUMMARY));

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isGenerated()).isTrue();
            assertThat(result.getModel()).contains(FALLBACK_MODEL);
            assertThat(output).contains("Claude claude-opus-5-5 declined CVE-2021-44228 (cyber); "
                    + "claude-opus-4-8 answered instead.");
        }

        @Test
        @DisplayName("대신 쓴 요약도 명령어가 섞이면 버린다")
        void fallbackStillGuarded() throws IOException {
            claude.answer(200, fallbackMessage("end_turn", """
                    {"what": "원격 코드 실행 취약점이다.", "who": "Log4j 사용자가 영향받는다.", \
                    "action": "pip install -U log4j 로 올린다."}"""));

            assertThat(summarizer().summarize(LOG4SHELL).isDiscarded()).isTrue();
        }

        @Test
        @DisplayName("대신 답하던 모델까지 거절하면 이 CVE 는 요약 없이 넘어간다")
        void fallbackAlsoRefused() throws IOException {
            claude.answer(200, """
                    {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-4-8", \
                    "content": [%s], "stop_reason": "refusal", "stop_sequence": null, \
                    "usage": {"input_tokens": 10, "output_tokens": 0}}""".formatted(FALLBACK_BLOCK));

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isRefused()).isTrue();
            assertThat(result.getModel()).contains(FALLBACK_MODEL);
        }
    }

    @Nested
    @DisplayName("Claude 를 부르지 못하면")
    class Failure {

        @Test
        @DisplayName("멈추지 않고 요약하지 못한 것으로 알린다")
        void rejectedRequest() {
            claude.answer(400, """
                    {"type": "error", "error": {"type": "invalid_request_error", "message": "bad request"}}""");

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
        }

        @Test
        @DisplayName("열쇠가 거부돼도 열쇠 값은 실행 기록에 남지 않는다")
        void keyNeverLogged(CapturedOutput output) {
            claude.answer(401, """
                    {"type": "error", "error": {"type": "authentication_error", "message": "invalid x-api-key"}}""");

            summarizer().summarize(LOG4SHELL);

            assertThat(output).contains("Korean summary request failed");
            assertThat(output).doesNotContain(KEY);
        }
    }

    private AnthropicSummarizer summarizer() {
        SummaryProperties properties = new SummaryProperties(20, MODEL, KEY);
        return new AnthropicSummarizer(
                AnthropicOkHttpClient.builder().apiKey(KEY).baseUrl(claude.baseUrl()).maxRetries(0).build(),
                properties, objectMapper);
    }

    private JsonNode request() throws IOException {
        return objectMapper.readTree(claude.lastRequestBody());
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private static final String FALLBACK_BLOCK = """
            {"type": "fallback", "from": {"model": "claude-opus-5-5"}, "to": {"model": "claude-opus-4-8"}, \
            "trigger": {"type": "refusal", "category": "cyber"}}""";

    private String fallbackMessage(String stopReason, String text) throws IOException {
        return """
                {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-4-8", \
                "content": [%s, {"type": "text", "text": %s}], "stop_reason": "%s", "stop_sequence": null, \
                "usage": {"input_tokens": 10, "output_tokens": 20}}""".formatted(FALLBACK_BLOCK,
                objectMapper.writeValueAsString(text), stopReason);
    }

    private String message(String stopReason, String text) throws IOException {
        return """
                {"id": "msg_1", "type": "message", "role": "assistant", "model": "claude-opus-5-5", \
                "content": [{"type": "text", "text": %s}], "stop_reason": "%s", "stop_sequence": null, \
                "usage": {"input_tokens": 10, "output_tokens": 20}}""".formatted(
                objectMapper.writeValueAsString(text), stopReason);
    }
}
