package com.vulntrend.batch.infrastructure.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.okhttp.OpenAIOkHttpClient;
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

/** 실제 OpenAI SDK 로 흉내 서버를 부른다. 실제 OpenAI 는 부르지 않는다 */
@DisplayName("OpenAI 로 한국어 요약 받기")
@ExtendWith(OutputCaptureExtension.class)
class OpenAiSummarizerTest {

    private static final String KEY = "summary-key-41c9e7";
    private static final String MODEL = "gpt-6-luna";
    private static final String ANSWERING_MODEL = "gpt-6-luna-2026-05-18";
    private static final SummarySource LOG4SHELL = SummarySource.of(CveId.from("CVE-2021-44228"),
            "Apache Log4j2 2.0-beta9 through 2.15.0 JNDI features do not protect against attacker controlled LDAP.",
            List.of(SummaryProduct.of("apache", "log4j", ">= 2.0-beta9, < 2.15.0", null)), new BigDecimal("10.0"));
    private static final String GOOD_SUMMARY = """
            {"what": "로그 문자열로 원격 코드를 실행할 수 있는 취약점이다.", \
            "who": "Log4j 2.0-beta9 이상 2.15.0 미만 사용자가 영향받는다.", \
            "action": "원문 참고"}""";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private OpenAiStubServer openAi;

    @BeforeEach
    void start() throws IOException {
        openAi = new OpenAiStubServer();
    }

    @AfterEach
    void stop() {
        openAi.close();
    }

    @Nested
    @DisplayName("OpenAI 에 보내는 요청")
    class Request {

        @Test
        @DisplayName("설정한 열쇠와 모델 이름으로 응답 생성 창구에 요청한다")
        void usesConfiguredKeyAndModel() throws IOException {
            openAi.answer(200, completed(GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            assertThat(openAi.lastPath()).isEqualTo("/v1/responses");
            assertThat(openAi.lastCredential()).isEqualTo(KEY);
            assertThat(request().path("model").asText()).isEqualTo(MODEL);
        }

        @Test
        @DisplayName("요약은 가벼운 작업이라 생각의 깊이를 낮게, 출력 한도를 작게 두고 응답을 보관하지 않게 한다")
        void lightRequest() throws IOException {
            openAi.answer(200, completed(GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            assertThat(request().path("reasoning").path("effort").asText()).isEqualTo("low");
            assertThat(request().path("max_output_tokens").asLong()).isBetween(1L, 4_000L);
            assertThat(request().path("store").asBoolean(true)).isFalse();
        }

        @Test
        @DisplayName("무엇이·누가·무엇을 세 칸이 모두 있는 모양으로만 답하게 한다")
        void structuredOutput() throws IOException {
            openAi.answer(200, completed(GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            JsonNode format = request().path("text").path("format");
            assertThat(format.path("type").asText()).isEqualTo("json_schema");
            assertThat(format.path("strict").asBoolean()).isTrue();
            assertThat(texts(format.path("schema").path("required"))).containsExactly("what", "who", "action");
            assertThat(format.path("schema").path("additionalProperties").asBoolean(true)).isFalse();
        }

        @Test
        @DisplayName("지시문은 따로 보내고 원문은 사용자 글로만 보낸다")
        void instructionsAndSourceOnly() throws IOException {
            openAi.answer(200, completed(GOOD_SUMMARY));

            summarizer().summarize(LOG4SHELL);

            assertThat(request().path("instructions").asText()).contains("원문에 없는 버전·제품·수치를 만들지 않는다");
            assertThat(request().path("input").asText()).startsWith("<description>");
        }
    }

    @Nested
    @DisplayName("OpenAI 의 답")
    class Answer {

        @Test
        @DisplayName("세 문장을 받으면 실제로 답한 모델 이름과 함께 요약으로 쓴다")
        void generated() throws IOException {
            openAi.answer(200, completed(GOOD_SUMMARY));

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isGenerated()).isTrue();
            assertThat(result.getSummary()).map(KoreanSummary::getWho)
                    .contains("Log4j 2.0-beta9 이상 2.15.0 미만 사용자가 영향받는다.");
            assertThat(result.getModel()).contains(ANSWERING_MODEL);
        }

        @Test
        @DisplayName("원문에 없는 버전이 나오면 요약을 버린다")
        void discardsInventedVersions() throws IOException {
            openAi.answer(200, completed("""
                    {"what": "원격 코드 실행 취약점이다.", "who": "Log4j 사용자가 영향받는다.", \
                    "action": "Log4j 2.17.1 이상으로 업데이트한다."}"""));

            assertThat(summarizer().summarize(LOG4SHELL).isDiscarded()).isTrue();
        }

        @Test
        @DisplayName("답에 명령어가 섞여 있으면 요약을 버린다")
        void discardsCommands() throws IOException {
            openAi.answer(200, completed("""
                    {"what": "원격 코드 실행 취약점이다.", "who": "Log4j 사용자가 영향받는다.", \
                    "action": "sudo apt-get install --only-upgrade liblog4j2-java 를 실행한다."}"""));

            assertThat(summarizer().summarize(LOG4SHELL).isDiscarded()).isTrue();
        }

        @Test
        @DisplayName("요약을 거절하면 이 CVE 는 요약 없이 넘어간다")
        void refused(CapturedOutput output) {
            openAi.answer(200, response("completed", """
                    {"type": "refusal", "refusal": "I can't help with that."}"""));

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isRefused()).isTrue();
            assertThat(result.getModel()).contains(ANSWERING_MODEL);
            assertThat(output).contains("declined to summarize CVE-2021-44228");
        }

        @Test
        @DisplayName("답이 출력 한도에서 끊기면 요약하지 못한 것으로 본다")
        void incomplete() throws IOException {
            openAi.answer(200, response("incomplete", outputText("{\"what\": \"원격")));

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
        }

        @Test
        @DisplayName("응답 자체를 읽을 수 없으면 멈추지 않고 요약하지 못한 것으로 본다")
        void unreadableResponse(CapturedOutput output) {
            openAi.answer(200, "{\"id\": \"resp_1\", \"object\": \"response\", \"model\": 42, \"output\": \"oops\"}");

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
            assertThat(output).contains("response could not be read");
        }

        @Test
        @DisplayName("답이 약속한 모양이 아니면 요약하지 못한 것으로 본다")
        void notJson() throws IOException {
            openAi.answer(200, completed("요약할 수 없습니다"));

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
        }
    }

    @Nested
    @DisplayName("OpenAI 를 부르지 못하면")
    class Failure {

        @Test
        @DisplayName("요청이 거부돼도 멈추지 않고 요약하지 못한 것으로 알린다")
        void rejectedRequest() {
            openAi.answer(400, error("invalid_request_error", "bad request"));

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
        }

        @Test
        @DisplayName("호출 한도에 걸려도 멈추지 않고 요약하지 못한 것으로 알린다")
        void rateLimited(CapturedOutput output) {
            openAi.answer(429, error("rate_limit_exceeded", "slow down"));

            assertThat(summarizer().summarize(LOG4SHELL).isFailed()).isTrue();
            assertThat(output).contains("rate limited");
        }

        @Test
        @DisplayName("열쇠가 거부돼도 열쇠 값은 실행 기록에 남지 않는다")
        void keyNeverLogged(CapturedOutput output) {
            openAi.answer(401, error("invalid_api_key", "Incorrect API key provided: " + KEY));

            SummaryResult result = summarizer().summarize(LOG4SHELL);

            assertThat(result.isFailed()).isTrue();
            assertThat(output).contains("API key was rejected");
            assertThat(output).doesNotContain(KEY);
        }
    }

    private OpenAiSummarizer summarizer() {
        SummaryProperties properties = new SummaryProperties(20, MODEL, KEY);
        return new OpenAiSummarizer(
                OpenAIOkHttpClient.builder().apiKey(KEY).baseUrl(openAi.baseUrl()).maxRetries(0).build(),
                properties, objectMapper);
    }

    private JsonNode request() throws IOException {
        return objectMapper.readTree(openAi.lastRequestBody());
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }

    private String completed(String text) throws IOException {
        return response("completed", outputText(text));
    }

    private String outputText(String text) throws IOException {
        return "{\"type\": \"output_text\", \"text\": " + objectMapper.writeValueAsString(text)
                + ", \"annotations\": []}";
    }

    private static String response(String status, String content) {
        return """
                {"id": "resp_1", "object": "response", "created_at": 1761955200, "status": "%s", \
                "model": "%s", "output": [{"type": "message", "id": "msg_1", "status": "%s", \
                "role": "assistant", "content": [%s]}], "error": null, "incomplete_details": %s, \
                "instructions": null, "metadata": {}, "parallel_tool_calls": true, "tool_choice": "auto", \
                "tools": [], "temperature": 1.0, "top_p": 1.0}""".formatted(status, ANSWERING_MODEL,
                "completed".equals(status) ? "completed" : "incomplete", content,
                "incomplete".equals(status) ? "{\"reason\": \"max_output_tokens\"}" : "null");
    }

    private static String error(String code, String message) {
        return """
                {"error": {"message": "%s", "type": "invalid_request_error", "param": null, "code": "%s"}}"""
                .formatted(message, code);
    }
}
