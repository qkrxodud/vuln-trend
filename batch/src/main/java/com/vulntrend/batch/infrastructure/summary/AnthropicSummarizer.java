package com.vulntrend.batch.infrastructure.summary;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.TextBlock;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Claude 로 한국어 요약 한 건을 받는다(WBS 6.2). 실패해도 던지지 않고 결과로 알린다 — 요약은 비치명 단계다.
 * Opus 5.5: thinking 설정은 보내지 않는다(끄면 400). 깊이는 effort LOW 로 낮춘다. assistant 미리 채우기도 400 이라
 * 답의 모양은 구조화 출력(output_config.format)으로 정한다. 거절(stop_reason=refusal)은 그 CVE 만 요약 없이 넘긴다.
 * 로그에는 SDK 예외 문장을 넣지 않는다(요청 정보가 들어 있을 수 있음) — 예외 종류 이름과 상태 코드만.
 */
@Slf4j
@RequiredArgsConstructor
public class AnthropicSummarizer implements Summarizer {

    // 생각 토큰도 이 한도에 들어간다. 세 문장이라 실제 사용은 훨씬 적다
    private static final long MAX_TOKENS = 16_000L;
    private static final List<String> FIELDS = List.of("what", "who", "action");

    private final AnthropicClient client;
    private final SummaryProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public SummaryResult summarize(SummarySource source) {
        try {
            return read(client.messages().create(paramsFor(source)));
        } catch (UnauthorizedException | PermissionDeniedException e) {
            log.warn("Korean summary request failed: the summary API key was rejected ({}).",
                    e.getClass().getSimpleName());
        } catch (RateLimitException e) {
            log.warn("Korean summary request failed: rate limited after retries ({}).", e.getClass().getSimpleName());
        } catch (AnthropicServiceException e) {
            log.warn("Korean summary request failed with status {} ({}).", e.statusCode(),
                    e.getClass().getSimpleName());
        } catch (AnthropicIoException e) {
            log.warn("Korean summary request failed: could not reach the API ({}).", e.getClass().getSimpleName());
        }
        return SummaryResult.failed();
    }

    private MessageCreateParams paramsFor(SummarySource source) {
        return MessageCreateParams.builder()
                .model(properties.getModel())
                .maxTokens(MAX_TOKENS)
                .system(SummaryPrompt.instructions())
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.LOW)
                        .format(JsonOutputFormat.builder().schema(schema()).build())
                        .build())
                .addUserMessage(SummaryPrompt.request(source))
                .build();
    }

    private static JsonOutputFormat.Schema schema() {
        Map<String, Object> fields = new LinkedHashMap<>();
        FIELDS.forEach(field -> fields.put(field, Map.of("type", "string")));
        return JsonOutputFormat.Schema.builder()
                .putAdditionalProperty("type", JsonValue.from("object"))
                .putAdditionalProperty("properties", JsonValue.from(fields))
                .putAdditionalProperty("required", JsonValue.from(FIELDS))
                .putAdditionalProperty("additionalProperties", JsonValue.from(false))
                .build();
    }

    private SummaryResult read(Message message) {
        Optional<StopReason> stopReason = message.stopReason();
        if (stopReason.filter(StopReason.REFUSAL::equals).isPresent()) {
            log.info("Claude declined to summarize this CVE; it stays without a summary.");
            return SummaryResult.refused();
        }
        if (stopReason.filter(StopReason.END_TURN::equals).isEmpty()) {
            log.warn("Korean summary ended early ({}); it stays without a summary.", stopReason.orElse(null));
            return SummaryResult.failed();
        }
        String text = message.content().stream()
                .flatMap(block -> block.text().stream())
                .map(TextBlock::text)
                .collect(Collectors.joining());
        return parse(text);
    }

    private SummaryResult parse(String text) {
        try {
            SummaryOutput output = objectMapper.readValue(text, SummaryOutput.class);
            return SummaryResult.from(KoreanSummary.of(output.what(), output.who(), output.action()),
                    properties.getModel());
        } catch (JsonProcessingException e) {
            log.warn("Korean summary was not in the expected shape ({}).", e.getClass().getSimpleName());
            return SummaryResult.failed();
        }
    }
}
