package com.vulntrend.batch.application.export;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 상세 파일(cve/*.json)을 맞춘 결과: 새로 쓴 수·그대로 둔 수·지운 수 */
@Getter
public class DetailFiles {

    private final int written;
    private final int unchanged;
    private final int removed;

    @Builder(access = AccessLevel.PRIVATE)
    private DetailFiles(int written, int unchanged, int removed) {
        this.written = written;
        this.unchanged = unchanged;
        this.removed = removed;
    }

    public static DetailFiles of(int written, int unchanged, int removed) {
        return DetailFiles.builder().written(written).unchanged(unchanged).removed(removed).build();
    }
}
