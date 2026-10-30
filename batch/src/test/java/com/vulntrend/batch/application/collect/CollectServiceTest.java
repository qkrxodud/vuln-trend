package com.vulntrend.batch.application.collect;

import static org.assertj.core.api.Assertions.assertThat;

import com.vulntrend.batch.domain.collect.SourceName;
import com.vulntrend.batch.domain.vulnerability.SyncState;
import com.vulntrend.batch.domain.vulnerability.SyncStatus;
import com.vulntrend.batch.fake.FakeCollector;
import com.vulntrend.batch.fake.FakeSyncStateRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("수집 순회")
class CollectServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-30T00:00:00Z");
    private static final Instant LAST_RUN = Instant.parse("2026-10-29T00:00:00Z");

    private final FakeSyncStateRepository syncStates = new FakeSyncStateRepository();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    @DisplayName("각 수집기에 마지막으로 가져온 시각부터 지금까지의 구간을 넘긴다")
    void passesWindowFromCursor() {
        // given: 어제 끝까지 가져온 NVD
        syncStates.save(SyncState.restore(SourceName.NVD, LAST_RUN, LAST_RUN, SyncStatus.SUCCESS));
        FakeCollector nvd = FakeCollector.loading(SourceName.NVD, 5);

        // when
        new CollectService(List.of(nvd), syncStates, clock).collectAll();

        // then
        assertThat(nvd.receivedWindows()).hasSize(1);
        assertThat(nvd.receivedWindows().get(0).getFrom()).isEqualTo(LAST_RUN);
        assertThat(nvd.receivedWindows().get(0).getUntil()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("다 가져온 소스는 다음 실행이 지금부터 이어 가도록 진행 기록이 옮겨진다")
    void successAdvancesCursor() {
        CollectOutcome outcome = new CollectService(
                List.of(FakeCollector.loading(SourceName.NVD, 5)), syncStates, clock).collectAll();

        assertThat(outcome.hasFailure()).isFalse();
        assertThat(outcome.getResults().get(0).getLoadedCount()).isEqualTo(5);
        assertThat(syncStates.findBySource(SourceName.NVD).getLastSyncedAt()).contains(NOW);
        assertThat(syncStates.findBySource(SourceName.NVD).getStatus()).isEqualTo(SyncStatus.SUCCESS);
    }

    @Test
    @DisplayName("수집이 실패한 소스는 다음 실행에서 같은 구간을 다시 가져온다")
    void failureKeepsCursor() {
        syncStates.save(SyncState.restore(SourceName.KEV, LAST_RUN, LAST_RUN, SyncStatus.SUCCESS));

        CollectOutcome outcome = new CollectService(
                List.of(FakeCollector.failing(SourceName.KEV)), syncStates, clock).collectAll();

        assertThat(outcome.failedSources()).containsExactly(SourceName.KEV);
        assertThat(syncStates.findBySource(SourceName.KEV).getLastSyncedAt()).contains(LAST_RUN);
        assertThat(syncStates.findBySource(SourceName.KEV).getStatus()).isEqualTo(SyncStatus.FAIL);
        assertThat(syncStates.findBySource(SourceName.KEV).getLastRunAt()).contains(NOW);
    }

    @Test
    @DisplayName("한 소스가 실패해도 다른 소스는 끝까지 가져오고 진행 기록도 따로 옮겨진다")
    void sourcesAreIndependent() {
        // given: KEV 가 먼저 실패하고 그다음 EPSS
        FakeCollector kev = FakeCollector.failing(SourceName.KEV);
        FakeCollector epss = FakeCollector.loading(SourceName.EPSS, 300);

        // when
        CollectOutcome outcome = new CollectService(List.of(kev, epss), syncStates, clock).collectAll();

        // then
        assertThat(epss.receivedWindows()).hasSize(1);
        assertThat(outcome.failedSources()).containsExactly(SourceName.KEV);
        assertThat(syncStates.findBySource(SourceName.EPSS).getLastSyncedAt()).contains(NOW);
        assertThat(syncStates.findBySource(SourceName.KEV).getLastSyncedAt()).isEmpty();
    }

    @Test
    @DisplayName("수집기가 하나도 없으면 실패 없이 빈 결과가 나온다")
    void noCollectors() {
        CollectOutcome outcome = new CollectService(List.of(), syncStates, clock).collectAll();

        assertThat(outcome.getResults()).isEmpty();
        assertThat(outcome.hasFailure()).isFalse();
    }
}
