package com.vulntrend.batch.domain.gate;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.gate.dto.DataCommit;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("Gate G — 배치 3일 연속 성공")
class DailyRunCheckTest {

    /** 한국 시각 2026-11-03 10:00 에 점검 */
    private static final Instant NOW = Instant.parse("2026-11-03T01:00:00Z");
    private static final Instant RAN = Instant.parse("2026-11-03T00:00:00Z");
    private static final List<SyncState> ALL_SUCCEEDED = List.of(
            SyncState.restore(SourceName.NVD, RAN, RAN, SyncStatus.SUCCESS),
            SyncState.restore(SourceName.KEV, RAN, RAN, SyncStatus.SUCCESS),
            SyncState.restore(SourceName.EPSS, RAN, RAN, SyncStatus.SUCCESS));

    @Nested
    @DisplayName("데이터 커밋이")
    class Commits {

        @Test
        @DisplayName("오늘까지 한국 달력일 사흘 연속 있으면 통과다")
        void threeDaysEndingToday() {
            GateItem item = DailyRunCheck.judge(commits("2026-11-03T09:00", "2026-11-02T09:00", "2026-11-01T09:00"),
                    ALL_SUCCEEDED, NOW);

            assertThat(item.getStatus()).isEqualTo(GateStatus.PASS);
            assertThat(item.getDetail()).contains("2026-11-01", "2026-11-03");
        }

        @Test
        @DisplayName("오늘 실행 전이면 어제까지 사흘 연속도 통과다")
        void threeDaysEndingYesterday() {
            GateItem item = DailyRunCheck.judge(commits("2026-11-02T09:00", "2026-11-01T09:00", "2026-10-31T21:00"),
                    ALL_SUCCEEDED, NOW);

            assertThat(item.getStatus()).isEqualTo(GateStatus.PASS);
        }

        @Test
        @DisplayName("같은 날 두 번 돈 것은 하루로 센다")
        void sameDayCountsOnce() {
            GateItem item = DailyRunCheck.judge(
                    commits("2026-11-03T21:00", "2026-11-03T09:00", "2026-11-02T09:00", "2026-11-01T09:00"),
                    ALL_SUCCEEDED, NOW);

            assertThat(item.getStatus()).isEqualTo(GateStatus.PASS);
        }

        @Test
        @DisplayName("중간에 하루가 비면 실패다")
        void gap() {
            GateItem item = DailyRunCheck.judge(commits("2026-11-03T09:00", "2026-11-01T09:00", "2026-10-31T09:00"),
                    ALL_SUCCEEDED, NOW);

            assertThat(item.getStatus()).isEqualTo(GateStatus.FAIL);
            assertThat(item.getDetail()).contains("2026-11-02");
        }

        @Test
        @DisplayName("사흘이 안 되면 실패다")
        void fewerThanThree() {
            GateItem item = DailyRunCheck.judge(commits("2026-11-03T09:00", "2026-11-02T09:00"), ALL_SUCCEEDED, NOW);

            assertThat(item.getStatus()).isEqualTo(GateStatus.FAIL);
            assertThat(item.getDetail()).contains("2일");
        }

        @Test
        @DisplayName("마지막 성공이 그저께면 연속이 끊긴 것이라 실패다")
        void stale() {
            GateItem item = DailyRunCheck.judge(commits("2026-11-01T09:00", "2026-10-31T09:00", "2026-10-30T09:00"),
                    ALL_SUCCEEDED, NOW);

            assertThat(item.getStatus()).isEqualTo(GateStatus.FAIL);
        }
    }

    @Nested
    @DisplayName("소스 진행 기록이")
    class Sources {

        private final List<DataCommit> threeDays =
                commits("2026-11-03T09:00", "2026-11-02T09:00", "2026-11-01T09:00");

        @Test
        @DisplayName("한 소스라도 마지막 실행이 실패면 실패다")
        void lastRunFailed() {
            List<SyncState> states = List.of(ALL_SUCCEEDED.get(0), ALL_SUCCEEDED.get(1),
                    SyncState.restore(SourceName.EPSS, RAN, RAN, SyncStatus.FAIL));

            GateItem item = DailyRunCheck.judge(threeDays, states, NOW);

            assertThat(item.getStatus()).isEqualTo(GateStatus.FAIL);
            assertThat(item.getDetail()).contains("epss");
        }

        @Test
        @DisplayName("한 번도 돌지 않은 소스가 있으면 실패다")
        void neverRun() {
            List<SyncState> states = List.of(ALL_SUCCEEDED.get(0), ALL_SUCCEEDED.get(1),
                    SyncState.firstRun(SourceName.EPSS));

            assertThat(DailyRunCheck.judge(threeDays, states, NOW).getStatus()).isEqualTo(GateStatus.FAIL);
        }

        @Test
        @DisplayName("소스마다 마지막 실행 결과를 근거로 남긴다")
        void evidencePerSource() {
            GateItem item = DailyRunCheck.judge(threeDays, ALL_SUCCEEDED, NOW);

            assertThat(item.getEvidence()).contains("nvd: SUCCESS, 마지막 실행 2026-11-03 09:00 KST",
                    "데이터 커밋: 2026-11-03 09:00, 2026-11-02 09:00, 2026-11-01 09:00");
        }
    }

    private static List<DataCommit> commits(String... runAt) {
        return Arrays.stream(runAt).map(time -> new DataCommit(LocalDateTime.parse(time), 3)).toList();
    }
}
