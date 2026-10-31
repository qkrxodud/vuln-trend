package com.vulntrend.batch.domain.stat;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 한 창 안에 새로 들어온 수: 신규(published_at)·KEV 신규(kev_added_at)·신규 중 Critical. */
@Getter
public class NewArrivals {

    private final int newCount;
    private final int kevNewCount;
    private final int criticalCount;

    @Builder(access = AccessLevel.PRIVATE)
    private NewArrivals(int newCount, int kevNewCount, int criticalCount) {
        this.newCount = newCount;
        this.kevNewCount = kevNewCount;
        this.criticalCount = criticalCount;
    }

    public static NewArrivals of(int newCount, int kevNewCount, int criticalCount) {
        if (Math.min(newCount, Math.min(kevNewCount, criticalCount)) < 0) {
            throw new DomainValidationException("KPI counts must not be negative.");
        }
        if (criticalCount > newCount) {
            throw new DomainValidationException("Critical count must not exceed the new count.");
        }
        return NewArrivals.builder().newCount(newCount).kevNewCount(kevNewCount).criticalCount(criticalCount).build();
    }
}
