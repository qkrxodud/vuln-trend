package com.vulntrend.batch.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.gate.dto.RepoSize;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Gate G — 레포 크기와 커밋당 바뀐 파일 수")
class RepoGrowthCheckTest {

    private static final long MB = 1024L * 1024L;

    @Test
    @DisplayName("크기와 최근 데이터 커밋의 바뀐 파일 수를 기준점으로 남기고 사람이 확인한다")
    void baseline() {
        GateItem item = RepoGrowthCheck.judge(new RepoSize(120 * MB, 3 * MB), List.of(
                new DataCommit(LocalDateTime.parse("2026-11-03T09:00"), 40),
                new DataCommit(LocalDateTime.parse("2026-11-02T09:00"), 20)));

        assertThat(item.getStatus()).isEqualTo(GateStatus.REVIEW);
        assertThat(item.getDetail()).contains("123.0MB", "평균 30.0", "최대 40");
        assertThat(item.getEvidence()).containsExactly("2026-11-03 09:00 — 40개", "2026-11-02 09:00 — 20개");
    }

    @Test
    @DisplayName("500MB 를 넘으면 히스토리 재생성 기준(P6)에 닿아 실패다")
    void overLimit() {
        GateItem item = RepoGrowthCheck.judge(new RepoSize(500 * MB, 1), List.of());

        assertThat(item.getStatus()).isEqualTo(GateStatus.FAIL);
    }

    @Test
    @DisplayName("데이터 커밋이 아직 없으면 그렇다고 적고 확인 필요다")
    void noDataCommits() {
        GateItem item = RepoGrowthCheck.judge(new RepoSize(10 * MB, 0), List.of());

        assertThat(item.getStatus()).isEqualTo(GateStatus.REVIEW);
        assertThat(item.getDetail()).contains("데이터 커밋 없음");
    }
}
