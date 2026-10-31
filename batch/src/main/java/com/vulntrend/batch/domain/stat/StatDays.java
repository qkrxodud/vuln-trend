package com.vulntrend.batch.domain.stat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * 일별 통계를 다시 셀 날들(기획서 6장 6번, WBS 3.3, R2): 이번 실행 시각의 한국 달력일까지 최근 14일.
 * NVD 가 지난 날짜로 늦게 발행하므로 오늘 하루만 고치면 틀린다. 그래서 매 실행 14일을 모두 다시 센다.
 */
@Getter
public class StatDays {

    /** 화면이 쓰는 달력(published_at 의 한국 달력일). 저장 시각은 모두 UTC 다 */
    public static final ZoneId CALENDAR = ZoneId.of("Asia/Seoul");
    private static final int DAYS = 14;

    private final LocalDate first;
    private final LocalDate today;

    @Builder(access = AccessLevel.PRIVATE)
    private StatDays(LocalDate first, LocalDate today) {
        this.first = first;
        this.today = today;
    }

    public static StatDays recent(Instant now) {
        LocalDate today = LocalDate.ofInstant(now, CALENDAR);
        return StatDays.builder().first(today.minusDays(DAYS - 1)).today(today).build();
    }

    public int count() {
        return DAYS;
    }
}
