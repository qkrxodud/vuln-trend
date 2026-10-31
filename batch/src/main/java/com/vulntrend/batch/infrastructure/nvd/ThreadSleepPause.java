package com.vulntrend.batch.infrastructure.nvd;

import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class ThreadSleepPause implements RequestPause {

    @Override
    public void pause(Duration duration) throws InterruptedException {
        Thread.sleep(duration.toMillis());
    }
}
