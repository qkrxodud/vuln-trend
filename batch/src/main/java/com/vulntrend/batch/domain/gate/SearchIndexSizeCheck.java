package com.vulntrend.batch.domain.gate;

import com.vulntrend.batch.domain.gate.dto.SizeEstimate;
import com.vulntrend.batch.domain.gate.dto.StoredFileSize;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Gate G "search-index.json 크기"(WBS 9장, R8 사용자 결정 2026-11-01): gzip(레벨 6) 크기 4MB(4×1024×1024 바이트) 이하면 통과.
 * 사이트가 실제로 보내는 양에 가까운 값이다. 파일이 없으면 실패. 근거에 파일 크기와 범위별 예상(지금 모양·줄바꿈 없이·gzip)을 남긴다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SearchIndexSizeCheck {

    public static final String ITEM = "search-index 크기";
    private static final long LIMIT = 4L * 1024L * 1024L;

    public static GateItem judge(Optional<StoredFileSize> stored, List<SizeEstimate> estimates) {
        List<String> evidence = estimates.stream()
                .map(e -> e.scope() + ": " + e.entries() + "건, 지금 모양 " + RepoGrowthCheck.megabytes(e.prettyBytes(), 2)
                        + ", 줄바꿈 없이 " + RepoGrowthCheck.megabytes(e.compactBytes(), 2) + ", gzip "
                        + RepoGrowthCheck.megabytes(e.gzipBytes(), 2))
                .toList();
        return stored.map(size -> judgeStored(size, evidence))
                .orElseGet(() -> GateItem.of(ITEM, GateStatus.FAIL, "파일 없음", evidence));
    }

    private static GateItem judgeStored(StoredFileSize size, List<String> evidence) {
        String gzip = "gzip " + RepoGrowthCheck.megabytes(size.gzipBytes(), 2);
        String file = " (파일 " + RepoGrowthCheck.megabytes(size.bytes(), 2) + ")";
        return size.gzipBytes() > LIMIT
                ? GateItem.of(ITEM, GateStatus.FAIL, gzip + " > 4MB 한도" + file, evidence)
                : GateItem.of(ITEM, GateStatus.PASS, gzip + " ≤ 4MB" + file, evidence);
    }
}
