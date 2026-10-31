package com.vulntrend.batch.infrastructure.nvd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("호출 사이 쉬기")
class ThreadSleepPauseTest {

    @Test
    @DisplayName("정한 시간만큼 실제로 쉰다")
    void sleepsForDuration() throws InterruptedException {
        long started = System.nanoTime();

        new ThreadSleepPause().pause(Duration.ofMillis(20));

        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(20));
    }
}
