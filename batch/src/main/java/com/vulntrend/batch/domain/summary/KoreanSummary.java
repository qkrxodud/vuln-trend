package com.vulntrend.batch.domain.summary;

import java.util.Objects;
import java.util.stream.Stream;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** 한국어 요약 세 문장(summary 의 what·who·action). what = 무엇이 문제인가, who = 누가 영향받나, action = 뭘 해야 하나 */
@Getter
@EqualsAndHashCode
public class KoreanSummary {

    private final String what;
    private final String who;
    private final String action;

    @Builder(access = AccessLevel.PRIVATE)
    private KoreanSummary(String what, String who, String action) {
        this.what = what;
        this.who = who;
        this.action = action;
    }

    public static KoreanSummary of(String what, String who, String action) {
        return KoreanSummary.builder().what(what).who(who).action(action).build();
    }

    public boolean hasCommand() {
        return sentences().filter(Objects::nonNull).anyMatch(SummaryGuard::hasCommand);
    }

    public boolean hasUnknownVersion(SummarySource source) {
        return sentences().filter(Objects::nonNull).anyMatch(sentence -> SummaryGuard.hasUnknownVersion(sentence, source));
    }

    public boolean isIncomplete() {
        return sentences().anyMatch(sentence -> Objects.isNull(sentence) || sentence.isBlank());
    }

    private Stream<String> sentences() {
        return Stream.of(what, who, action);
    }
}
