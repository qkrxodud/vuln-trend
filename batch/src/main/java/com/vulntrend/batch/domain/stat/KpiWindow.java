package com.vulntrend.batch.domain.stat;

import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * KPI 를 세는 24시간 롤링 창(기획서 8장 KPI 표, WBS 3.4, P2·D11). 지금 창은 [실행 −24시간, 실행),
 * 어제 창은 [실행 −48시간, 실행 −24시간). 시작은 넣고 끝은 빼서 이웃 창과 겹치지 않는다.
 */
@Getter
public class KpiWindow {

    private static final Duration LENGTH = Duration.ofHours(24);

    private final Instant from;
    private final Instant to;

    @Builder(access = AccessLevel.PRIVATE)
    private KpiWindow(Instant from, Instant to) {
        this.from = from;
        this.to = to;
    }

    public static KpiWindow today(Instant now) {
        return endingAt(now);
    }

    public static KpiWindow yesterday(Instant now) {
        return endingAt(now.minus(LENGTH));
    }

    private static KpiWindow endingAt(Instant to) {
        return KpiWindow.builder().from(to.minus(LENGTH)).to(to).build();
    }
}
