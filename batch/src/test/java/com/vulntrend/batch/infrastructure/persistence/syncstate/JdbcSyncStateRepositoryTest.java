package com.vulntrend.batch.infrastructure.persistence.syncstate;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.domain.vulnerability.SyncWindow;
import com.vulntrend.batch.infrastructure.persistence.PostgresTestConfig;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, JdbcSyncStateRepository.class})
@DisplayName("수집 진행 기록 — 실제 저장 구조에서")
class JdbcSyncStateRepositoryTest {

    private static final Instant YESTERDAY = Instant.parse("2026-10-29T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");

    @Autowired
    private JdbcSyncStateRepository repository;

    @Test
    @DisplayName("한 번도 기록이 없는 소스는 처음 실행하는 소스로 나온다")
    void absentIsFirstRun() {
        SyncState state = repository.findBySource(SourceName.OSV);

        assertThat(state.getSource()).isEqualTo(SourceName.OSV);
        assertThat(state.getLastSyncedAt()).isEmpty();
        assertThat(state.getStatus()).isEqualTo(SyncStatus.NEVER_RUN);
    }

    @Test
    @DisplayName("다 가져온 실행의 진행 기록을 다시 찾으면 같은 시각과 상태가 나온다")
    void savedSuccessIsFoundAgain() {
        SyncState first = SyncState.firstRun(SourceName.NVD);
        repository.save(first.succeeded(SyncWindow.of(YESTERDAY, NOW), NOW));

        SyncState found = repository.findBySource(SourceName.NVD);

        assertThat(found.getLastSyncedAt()).contains(NOW);
        assertThat(found.getLastRunAt()).contains(NOW);
        assertThat(found.getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    @DisplayName("같은 소스를 다시 기록하면 두 번 올라가지 않고 새 기록으로 바뀐다")
    void saveOverwrites() {
        SyncState succeeded = SyncState.firstRun(SourceName.KEV).succeeded(SyncWindow.of(YESTERDAY, NOW), YESTERDAY);
        repository.save(succeeded);

        repository.save(succeeded.failed(NOW));

        SyncState found = repository.findBySource(SourceName.KEV);
        assertThat(found.getStatus()).isEqualTo(SyncStatus.FAIL);
        assertThat(found.getLastSyncedAt()).contains(NOW);
        assertThat(found.getLastRunAt()).contains(NOW);
    }

    @Test
    @DisplayName("처음 실행에서 실패한 소스는 마지막으로 가져온 시각 없이 실패만 기록된다")
    void firstRunFailureHasNoCursor() {
        repository.save(SyncState.firstRun(SourceName.EPSS).failed(NOW));

        SyncState found = repository.findBySource(SourceName.EPSS);

        assertThat(found.getLastSyncedAt()).isEmpty();
        assertThat(found.getStatus()).isEqualTo(SyncStatus.FAIL);
    }
}
