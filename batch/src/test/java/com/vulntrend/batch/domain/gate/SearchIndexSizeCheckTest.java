package com.vulntrend.batch.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.gate.dto.SizeEstimate;
import com.vulntrend.batch.domain.gate.dto.StoredFileSize;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gate G — search-index 크기(R8, gzip 기준)")
class SearchIndexSizeCheckTest {

    private static final long MB = 1024L * 1024L;
    private static final List<SizeEstimate> ESTIMATES = List.of(
            new SizeEstimate("전체", 200_000, 28 * MB, 15 * MB, 4 * MB),
            new SizeEstimate("최근 6개월", 100_000, 14 * MB, 7 * MB + MB / 2, 2 * MB));

    @Test
    @DisplayName("파일이 4MB 를 넘어도 gzip 크기가 4MB 이하면 통과다(R8 결정)")
    void gzipWithinLimit() {
        GateItem item = SearchIndexSizeCheck.judge(Optional.of(new StoredFileSize(14 * MB, 4 * MB)), ESTIMATES);

        assertThat(item.getStatus()).isEqualTo(GateStatus.PASS);
        assertThat(item.getDetail()).isEqualTo("gzip 4.00MB ≤ 4MB (파일 14.00MB)");
    }

    @Test
    @DisplayName("gzip 크기가 4MB 를 넘으면 실패다")
    void gzipOverLimit() {
        GateItem item = SearchIndexSizeCheck.judge(Optional.of(new StoredFileSize(60 * MB, 4 * MB + 1)), ESTIMATES);

        assertThat(item.getStatus()).isEqualTo(GateStatus.FAIL);
        assertThat(item.getDetail()).isEqualTo("gzip 4.00MB > 4MB 한도 (파일 60.00MB)");
    }

    @Test
    @DisplayName("파일이 없으면 실패다(웹 점검과 같은 판정)")
    void missing() {
        assertThat(SearchIndexSizeCheck.judge(Optional.empty(), ESTIMATES).getStatus()).isEqualTo(GateStatus.FAIL);
    }

    @Test
    @DisplayName("줄바꿈 없이 쓰기·기간 줄이기·gzip 의 예상 크기를 범위마다 근거로 남긴다")
    void estimates() {
        GateItem item = SearchIndexSizeCheck.judge(Optional.of(new StoredFileSize(28 * MB, 4 * MB)), ESTIMATES);

        assertThat(item.getEvidence()).containsExactly(
                "전체: 200000건, 지금 모양 28.00MB, 줄바꿈 없이 15.00MB, gzip 4.00MB",
                "최근 6개월: 100000건, 지금 모양 14.00MB, 줄바꿈 없이 7.50MB, gzip 2.00MB");
    }
}
