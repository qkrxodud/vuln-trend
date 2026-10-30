package com.vulntrend.batch.domain.collect;

import com.vulntrend.batch.global.error.exception.DomainValidationException;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 소스 하나의 이번 수집 결과 */
public class CollectResult {

    @Getter
    private final SourceName source;
    @Getter
    private final int loadedCount;
    private final Status status;

    @Builder(access = AccessLevel.PRIVATE)
    private CollectResult(SourceName source, int loadedCount, Status status) {
        this.source = source;
        this.loadedCount = loadedCount;
        this.status = status;
    }

    public static CollectResult loaded(SourceName source, int loadedCount) {
        if (loadedCount < 0) {
            throw new DomainValidationException("A loaded count cannot be negative.");
        }
        return CollectResult.builder().source(source).loadedCount(loadedCount).status(Status.LOADED).build();
    }

    public static CollectResult failed(SourceName source) {
        return CollectResult.builder().source(source).loadedCount(0).status(Status.FAILED).build();
    }

    public boolean isFailed() {
        return status == Status.FAILED;
    }

    public boolean isSucceeded() {
        return status == Status.LOADED;
    }

    private enum Status {
        LOADED,
        FAILED
    }
}
