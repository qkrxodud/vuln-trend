package com.vulntrend.batch.domain.summary;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.vulnerability.CveId;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("한국어 요약 결과")
class SummaryResultTest {

    private static final String MODEL = "gpt-6-luna";
    private static final SummarySource LOG4SHELL = SummarySource.of(CveId.from("CVE-2021-44228"),
            "Apache Log4j2 2.0-beta9 through 2.15.0 JNDI features do not protect against attacker controlled LDAP.",
            List.of(SummaryProduct.of("apache", "log4j", ">= 2.0-beta9, < 2.15.0", "2.15.0")), new BigDecimal("10.0"));

    @Test
    @DisplayName("세 문장이 모두 있고 명령어와 지어낸 버전이 없으면 요약으로 쓴다")
    void generated() {
        KoreanSummary summary = KoreanSummary.of("원격 코드 실행 취약점이다.", "Log4j 2.15.0 미만 사용자가 영향받는다.",
                "Log4j 2.15.0 이상으로 업데이트한다.");

        SummaryResult result = SummaryResult.from(summary, MODEL, LOG4SHELL);

        assertThat(result.isGenerated()).isTrue();
        assertThat(result.getSummary()).contains(summary);
        assertThat(result.getModel()).contains(MODEL);
    }

    @Test
    @DisplayName("한 문장이라도 명령어가 섞이면 요약을 버린다")
    void discardsCommands() {
        KoreanSummary summary = KoreanSummary.of("원격 코드 실행 취약점이다.", "Log4j 사용자가 영향받는다.",
                "log4j2.formatMsgNoLookups=true 로 설정한다.");

        SummaryResult result = SummaryResult.from(summary, MODEL, LOG4SHELL);

        assertThat(result.isDiscarded()).isTrue();
        assertThat(result.getSummary()).isEmpty();
        assertThat(result.getDiscardReason()).hasValueSatisfying(reason -> {
            assertThat(reason.getRule()).isEqualTo(DiscardRule.COMMAND);
            assertThat(reason.getFragment()).startsWith("log4j2.formatMsgNoLookups=true");
        });
    }

    @Test
    @DisplayName("원문에 없는 버전이 나오면 요약을 버린다")
    void discardsInventedVersions() {
        KoreanSummary summary = KoreanSummary.of("원격 코드 실행 취약점이다.", "Log4j 2.15.0 미만 사용자가 영향받는다.",
                "Log4j 2.17.1 이상으로 업데이트한다.");

        SummaryResult result = SummaryResult.from(summary, MODEL, LOG4SHELL);

        assertThat(result.isDiscarded()).isTrue();
        assertThat(result.getDiscardReason()).hasValueSatisfying(reason -> {
            assertThat(reason.getRule()).isEqualTo(DiscardRule.UNKNOWN_VERSION);
            assertThat(reason.getFragment()).isEqualTo("2.17.1");
        });
    }

    @Test
    @DisplayName("명령어와 지어낸 버전이 함께 있으면 명령어를 이유로 남긴다")
    void commandComesFirst() {
        KoreanSummary summary = KoreanSummary.of("원격 코드 실행 취약점이다.", "Log4j 2.99.9 사용자가 영향받는다.",
                "sudo 로 다시 설치한다.");

        SummaryResult result = SummaryResult.from(summary, MODEL, LOG4SHELL);

        assertThat(result.getDiscardReason()).map(DiscardReason::getRule).contains(DiscardRule.COMMAND);
    }

    @Test
    @DisplayName("쓸 수 있는 요약에는 버린 이유가 없다")
    void generatedHasNoReason() {
        KoreanSummary summary = KoreanSummary.of("원격 코드 실행 취약점이다.", "Log4j 사용자가 영향받는다.", "원문 참고");

        assertThat(SummaryResult.from(summary, MODEL, LOG4SHELL).getDiscardReason()).isEmpty();
        assertThat(SummaryResult.refused(MODEL).getDiscardReason()).isEmpty();
    }

    @Test
    @DisplayName("비어 있는 문장이 있으면 요약하지 못한 것으로 본다")
    void incomplete() {
        SummaryResult result = SummaryResult.from(KoreanSummary.of("원격 코드 실행 취약점이다.", " ", "원문 참고"), MODEL,
                LOG4SHELL);

        assertThat(result.isFailed()).isTrue();
        assertThat(result.getSummary()).isEmpty();
    }

    @Test
    @DisplayName("요약 모델이 요약을 거절하면 요약 없이 넘어간다")
    void refused() {
        SummaryResult result = SummaryResult.refused(MODEL);

        assertThat(result.isRefused()).isTrue();
        assertThat(result.getSummary()).isEmpty();
    }

    @Test
    @DisplayName("만든 것·거절된 것은 한 번, 버린 것은 두 번 받으면 다시 부르지 않고 실패한 것은 몇 번이든 다시 부른다")
    void whenToStopCalling() {
        assertThat(SummaryOutcome.GENERATED.finalAfter()).hasValue(1);
        assertThat(SummaryOutcome.REFUSED.finalAfter()).hasValue(1);
        assertThat(SummaryOutcome.DISCARDED.finalAfter()).hasValue(2);
        assertThat(SummaryOutcome.FAILED.finalAfter()).isEmpty();
    }

    @Test
    @DisplayName("답을 받지 못한 호출은 실패뿐이다")
    void onlyFailuresAreUnanswered() {
        assertThat(SummaryOutcome.FAILED.isFailed()).isTrue();
        assertThat(List.of(SummaryOutcome.GENERATED, SummaryOutcome.DISCARDED, SummaryOutcome.REFUSED))
                .noneMatch(SummaryOutcome::isFailed);
    }
}
