package com.vulntrend.batch.domain.gate;

import com.vulntrend.batch.domain.gate.dto.SizeEstimate;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Gate G "search-index.json 크기 4MB 이하"(WBS 9장, R8). 판정은 웹 점검과 같다: 파일이 없거나 4MB(4×1024×1024 바이트)를
 * 넘으면 실패. 줄이는 방법(줄바꿈 없이 쓰기·최근 N개월만)의 예상 크기를 근거로 남긴다 — 고르는 것은 사용자 몫이다.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SearchIndexSizeCheck {

    public static final String ITEM = "search-index 크기";
    private static final long LIMIT = 4L * 1024L * 1024L;

    public static GateItem judge(Optional<Long> actualBytes, List<SizeEstimate> estimates) {
        List<String> evidence = estimates.stream()
                .map(e -> e.scope() + ": " + e.entries() + "건, 지금 모양 " + RepoGrowthCheck.megabytes(e.prettyBytes(), 2)
                        + ", 줄바꿈 없이 " + RepoGrowthCheck.megabytes(e.compactBytes(), 2))
                .toList();
        return actualBytes
                .map(bytes -> bytes > LIMIT
                        ? GateItem.of(ITEM, GateStatus.FAIL, RepoGrowthCheck.megabytes(bytes, 2) + " > 4MB 한도", evidence)
                        : GateItem.of(ITEM, GateStatus.PASS, RepoGrowthCheck.megabytes(bytes, 2) + " ≤ 4MB", evidence))
                .orElseGet(() -> GateItem.of(ITEM, GateStatus.FAIL, "파일 없음", evidence));
    }
}
