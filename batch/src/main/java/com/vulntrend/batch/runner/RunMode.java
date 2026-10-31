package com.vulntrend.batch.runner;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;

/**
 * 이번 실행의 방식. 인자가 없으면 하루 한 번 실행, --backfill-days=N 이면 지난 N일 NVD 한 번 가져오기(WBS 1.5),
 * --gate-check 면 Gate G 배치 점검만(읽기만, 백엔드 B 의 GateCheckService). 둘을 함께 주면 시작하지 않는다.
 * 예: (cd batch && ./gradlew bootRun --args='--backfill-days=365'), (cd batch && ./gradlew gateCheck)
 */
public class RunMode {

    private static final String BACKFILL_DAYS = "--backfill-days=";
    private static final String GATE_CHECK = "--gate-check";

    private final Duration backfillSpan;
    private final boolean gateCheck;

    @Builder(access = AccessLevel.PRIVATE)
    private RunMode(Duration backfillSpan, boolean gateCheck) {
        this.backfillSpan = backfillSpan;
        this.gateCheck = gateCheck;
    }

    public static RunMode from(String... args) {
        Optional<String> days = Arrays.stream(args)
                .filter(arg -> arg.startsWith(BACKFILL_DAYS))
                .map(arg -> arg.substring(BACKFILL_DAYS.length()))
                .findFirst();
        boolean gateCheck = Arrays.asList(args).contains(GATE_CHECK);
        if (gateCheck && days.isPresent()) {
            throw new DomainValidationException("--gate-check cannot be combined with --backfill-days.");
        }
        return RunMode.builder().backfillSpan(days.map(RunMode::positiveDays).orElse(null)).gateCheck(gateCheck)
                .build();
    }

    public boolean isGateCheck() {
        return gateCheck;
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
