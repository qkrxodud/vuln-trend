package com.vulntrend.batch.application.export;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/** 사이트 데이터 내보내기 한 번의 결과(러너 로그·판정용). 파일 수는 목록 파일 5개 + 상세 파일 */
@Getter
public class ExportResult {

    private final int written;
    private final int unchanged;
    private final int removed;
    private final int detailFiles;

    @Builder(access = AccessLevel.PRIVATE)
    private ExportResult(int written, int unchanged, int removed, int detailFiles) {
        this.written = written;
        this.unchanged = unchanged;
        this.removed = removed;
        this.detailFiles = detailFiles;
    }

    public static ExportResult of(int written, int unchanged, int removed, int detailFiles) {
        return ExportResult.builder().written(written).unchanged(unchanged).removed(removed).detailFiles(detailFiles)
                .build();
    }

    /** 바뀐 파일이 하나라도 있는가(없으면 push 할 것이 없다) */
    public boolean hasChanges() {
        return written + removed > 0;
    }
}
