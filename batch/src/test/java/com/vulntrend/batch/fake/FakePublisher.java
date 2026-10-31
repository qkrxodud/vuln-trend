package com.vulntrend.batch.fake;

import com.vulntrend.batch.domain.publish.PublishResult;
import com.vulntrend.batch.domain.publish.Publisher;
import com.vulntrend.batch.global.error.exception.PublishException;

/** 게시 흉내. 게시한 횟수를 세고, 실패하도록 만들 수 있다 */
public class FakePublisher implements Publisher {

    private final boolean failing;
    private int published;

    private FakePublisher(boolean failing) {
        this.failing = failing;
    }

    public static FakePublisher working() {
        return new FakePublisher(false);
    }

    public static FakePublisher failing() {
        return new FakePublisher(true);
    }

    @Override
    public PublishResult publish() {
        published++;
        if (failing) {
            throw new PublishException("git push failed with exit code 128.");
        }
        return PublishResult.PUBLISHED;
    }

    public int published() {
        return published;
    }
}
