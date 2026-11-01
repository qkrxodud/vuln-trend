package com.vulntrend.batch.domain.summary;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
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

    /** 버려야 하는 이유. 명령어를 먼저 보고(따라 하면 위험), 그다음 원문에 없는 버전을 본다 */
    public Optional<DiscardReason> discardReason(SummarySource source) {
        Optional<DiscardReason> command = firstIn(SummaryGuard::commandIn)
                .map(fragment -> DiscardReason.of(DiscardRule.COMMAND, fragment));
        if (command.isPresent()) {
            return command;
        }
        return firstIn(sentence -> SummaryGuard.unknownVersionIn(sentence, source))
                .map(version -> DiscardReason.of(DiscardRule.UNKNOWN_VERSION, version));
    }

    public boolean isIncomplete() {
        return sentences().anyMatch(sentence -> Objects.isNull(sentence) || sentence.isBlank());
    }

    private Optional<String> firstIn(Function<String, Optional<String>> finder) {
        return sentences().filter(Objects::nonNull).map(finder).flatMap(Optional::stream).findFirst();
    }

    private Stream<String> sentences() {
        return Stream.of(what, who, action);
    }
}
