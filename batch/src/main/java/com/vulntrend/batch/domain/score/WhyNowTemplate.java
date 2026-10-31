package com.vulntrend.batch.domain.score;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * "왜 지금?" 규칙 템플릿(LLM 아님, 기획서 6장·D10). 위에서 아래 순서로 처음 맞는 줄 하나를 쓴다.
 * DB 에 저장하지 않고 JSON 을 쓸 때 만든다(기획서 5장 메모). PoC·뉴스 줄은 2단계(B2·B3)에서 수가 들어오면 살아난다.
 */
@RequiredArgsConstructor
public enum WhyNowTemplate {

    KEV_WITH_POC(facts -> facts.isKev() && facts.hasPoc(), facts -> "실제 악용 확인, 공개 PoC 존재"),
    KEV_RANSOMWARE(WhyNowFacts::isRansomware, facts -> "랜섬웨어 캠페인에 사용 중"),
    KEV(WhyNowFacts::isKev, facts -> "CISA 악용 목록 등재"),
    EPSS_TOP(facts -> facts.isEpssAtLeast(Thresholds.EPSS_PERCENTILE),
            facts -> "30일 내 악용 확률 상위 " + facts.epssTopPercent() + "%"),
    POC_ONLY(WhyNowFacts::hasPoc, facts -> "공개 PoC 존재, 스캔 증가 가능"),
    NEWS(facts -> facts.getMentionCount() >= Thresholds.NEWS_MENTIONS,
            facts -> "최근 7일 보안 뉴스 " + facts.getMentionCount() + "건 언급"),
    OTHERWISE(facts -> true, facts -> "CVSS " + cvssText(facts) + ", 최근 공개");

    private final Predicate<WhyNowFacts> condition;
    private final Function<WhyNowFacts, String> phrase;

    public static String phraseOf(WhyNowFacts facts) {
        return Arrays.stream(values())
                .filter(rule -> rule.condition.test(facts))
                .findFirst()
                .orElse(OTHERWISE)
                .phrase.apply(facts);
    }

    private static String cvssText(WhyNowFacts facts) {
        return facts.getCvss().map(score -> score.setScale(1, RoundingMode.HALF_UP).toPlainString()).orElse("미평가");
    }

    private static final class Thresholds {
        private static final BigDecimal EPSS_PERCENTILE = new BigDecimal("0.95");
        private static final int NEWS_MENTIONS = 3;
    }
}
