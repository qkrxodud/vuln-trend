package com.vulntrend.batch.runner;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * 이번 실행의 방식. 인자가 없으면 하루 한 번 실행, --backfill-days=N 이면 지난 N일 NVD 한 번 가져오기(WBS 1.5).
 * 예: (cd batch && ./gradlew bootRun --args='--backfill-days=365')
 */
public class RunMode {

    private static final String BACKFILL_DAYS = "--backfill-days=";

    private final Duration backfillSpan;

    @Builder(access = AccessLevel.PRIVATE)
    private RunMode(Duration backfillSpan) {
        this.backfillSpan = backfillSpan;
    }

    public static RunMode from(String... args) {
        Optional<String> days = Arrays.stream(args)
                .filter(arg -> arg.startsWith(BACKFILL_DAYS))
                .map(arg -> arg.substring(BACKFILL_DAYS.length()))
                .findFirst();
        return RunMode.builder().backfillSpan(days.map(RunMode::positiveDays).orElse(null)).build();
    }

    public boolean isBackfill() {
        return Objects.nonNull(backfillSpan);
    }

    /** 백필일 때만 쓴다(isBackfill 먼저 확인). 하루 한 번 실행이면 값이 없다 */
    public Duration getBackfillSpan() {
        return backfillSpan;
    }

    private static Duration positiveDays(String text) {
        try {
            long days = Long.parseLong(text);
            if (days < 1) {
                throw new DomainValidationException("--backfill-days must be 1 or more.");
            }
            return Duration.ofDays(days);
        } catch (NumberFormatException notNumber) {
            throw new DomainValidationException("--backfill-days must be a whole number of days.");
        }
    }
}
