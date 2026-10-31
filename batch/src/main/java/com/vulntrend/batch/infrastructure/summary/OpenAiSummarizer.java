package com.vulntrend.batch.infrastructure.summary;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.core.JsonValue;
import com.openai.errors.OpenAIInvalidDataException;
import com.openai.errors.OpenAIIoException;
import com.openai.errors.OpenAIServiceException;
import com.openai.errors.PermissionDeniedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseOutputMessage;
import com.openai.models.responses.ResponseOutputText;
import com.openai.models.responses.ResponseStatus;
import com.openai.models.responses.ResponseTextConfig;
import com.vulntrend.batch.domain.summary.KoreanSummary;
import com.vulntrend.batch.domain.summary.Summarizer;
import com.vulntrend.batch.domain.summary.SummaryPrompt;
import com.vulntrend.batch.domain.summary.SummaryResult;
import com.vulntrend.batch.domain.summary.SummarySource;
import com.vulntrend.batch.global.config.SummaryProperties;
import com.vulntrend.batch.infrastructure.summary.dto.SummaryOutput;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * OpenAI Responses API 로 한국어 요약 한 건을 받는다(WBS 6.2·6.3, 사용자 결정 2026-11-01 OpenAI 전환). 실패해도 던지지 않고 결과로 알린다.
 * - 지시문은 instructions(system), 원문은 input(사용자 글)으로 보낸다. 답의 모양은 구조화 출력(json_schema, strict)으로 정한다
 *   (gpt-6-luna 모델 문서의 지원 기능에 structured_outputs 가 있음, 2026-11-01 확인).
 * - 세 문장이라 reasoning.effort 는 LOW, 출력 한도는 작게 둔다(생각 토큰도 이 한도에 들어간다). 응답은 OpenAI 쪽에 보관하지 않는다(store=false).
 * - 거절(refusal 내용)이면 REFUSED, 다 끝나지 않은 응답(incomplete 등)이나 약속과 다른 모양은 FAILED. 남기는 모델 이름은 응답의 실제 model.
 * - 로그에는 SDK 예외 문장을 넣지 않는다 — 예외 종류 이름과 상태 코드만.
 */
@Slf4j
@RequiredArgsConstructor
public class OpenAiSummarizer implements Summarizer {

    private static final long MAX_OUTPUT_TOKENS = 2_000L;
    private static final String SCHEMA_NAME = "korean_summary";
    private static final List<String> FIELDS = List.of("what", "who", "action");

    private final OpenAIClient client;
    private final SummaryProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public SummaryResult summarize(SummarySource source) {
        try {
            return read(client.responses().create(paramsFor(source)), source);
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.warn("Korean summary request failed: the summary API key was rejected ({}).",
                    e.getClass().getSimpleName());
        } catch (RateLimitException e) {
            log.warn("Korean summary request failed: rate limited after retries ({}).", e.getClass().getSimpleName());
        } catch (OpenAIServiceException e) {
            log.warn("Korean summary request failed with status {} ({}).", e.statusCode(),
                    e.getClass().getSimpleName());
        } catch (OpenAIIoException e) {
            log.warn("Korean summary request failed: could not reach the API ({}).", e.getClass().getSimpleName());
        } catch (OpenAIInvalidDataException e) {
            log.warn("Korean summary response could not be read ({}).", e.getClass().getSimpleName());
        }
        return SummaryResult.failed();
    }

    private ResponseCreateParams paramsFor(SummarySource source) {
        return ResponseCreateParams.builder()
                .model(properties.getModel())
                .instructions(SummaryPrompt.instructions())
                .input(SummaryPrompt.request(source))
                .reasoning(Reasoning.builder().effort(ReasoningEffort.LOW).build())
                .maxOutputTokens(MAX_OUTPUT_TOKENS)
                .store(false)
                .text(ResponseTextConfig.builder()
                        .format(ResponseFormatTextJsonSchemaConfig.builder()
                                .name(SCHEMA_NAME)
                                .strict(true)
                                .schema(schema())
                                .build())
                        .build())
                .build();
    }

    private static ResponseFormatTextJsonSchemaConfig.Schema schema() {
        Map<String, Object> fields = new LinkedHashMap<>();
        FIELDS.forEach(field -> fields.put(field, Map.of("type", "string")));
        Map<String, Object> schema = Map.of("type", "object", "properties", fields, "required", FIELDS,
                "additionalProperties", false);
        return JsonValue.from(schema).convert(ResponseFormatTextJsonSchemaConfig.Schema.class);
    }

    private SummaryResult read(Response response, SummarySource source) {
        String model = response.model().asString();
        List<ResponseOutputMessage.Content> contents = response.output().stream()
                .flatMap(item -> item.message().stream())
                .flatMap(message -> message.content().stream())
                .toList();
        if (contents.stream().anyMatch(ResponseOutputMessage.Content::isRefusal)) {
            log.info("{} declined to summarize {}; it stays without a summary.", model, source.getCveId());
            return SummaryResult.refused(model);
        }
        if (response.status().filter(ResponseStatus.COMPLETED::equals).isEmpty()) {
            log.warn("Korean summary of {} did not complete ({}); it stays without a summary.", source.getCveId(),
                    response.status().map(Object::toString).orElse("unknown"));
            return SummaryResult.failed();
        }
        String text = contents.stream()
                .flatMap(content -> content.outputText().stream())
                .map(ResponseOutputText::text)
                .collect(Collectors.joining());
        return parse(text, model, source);
    }

    private SummaryResult parse(String text, String model, SummarySource source) {
        try {
            SummaryOutput output = objectMapper.readValue(text, SummaryOutput.class);
            return SummaryResult.from(KoreanSummary.of(output.what(), output.who(), output.action()), model, source);
        } catch (JsonProcessingException e) {
            log.warn("Korean summary of {} was not in the expected shape ({}).", source.getCveId(),
                    e.getClass().getSimpleName());
            return SummaryResult.failed();
        }
    }
}
