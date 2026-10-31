package com.vulntrend.batch.infrastructure.nvd;

import java.time.Duration;

/** 호출 사이 쉬기. 테스트는 쉬지 않고 기록만 하는 것으로 바꿔 끼운다 */
@FunctionalInterface
public interface RequestPause {

    void pause(Duration duration) throws InterruptedException;
}
